package dev.litemfinder.storage.sqlite;

import dev.litemfinder.core.index.SnapshotFlattener;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.ContainerLocation;
import dev.litemfinder.core.model.ContainerRecord;
import dev.litemfinder.core.model.ContainerType;
import dev.litemfinder.core.model.InventorySnapshot;
import dev.litemfinder.core.model.ItemKey;
import dev.litemfinder.core.model.ItemStackInfo;
import dev.litemfinder.core.model.LogicalLocation;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.model.SlotSnapshot;
import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.persistence.SnapshotRepository;
import dev.litemfinder.core.persistence.SnapshotRepositoryException;
import dev.litemfinder.core.persistence.SnapshotWriteResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** SQLite-backed repository for complete recursive container snapshots. */
public final class SqliteSnapshotRepository implements SnapshotRepository {

    private static final int SCHEMA_VERSION = 1;

    private final Connection connection;
    private final SnapshotFlattener snapshotValidator = new SnapshotFlattener();

    public SqliteSnapshotRepository(Path databaseFile) {
        connection = openConnection(Objects.requireNonNull(databaseFile, "databaseFile must not be null"));
    }

    private static Connection openConnection(Path databaseFile) {
        Path absolutePath = databaseFile.toAbsolutePath().normalize();
        Connection openedConnection = null;
        try {
            Path parent = absolutePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            openedConnection = DriverManager.getConnection("jdbc:sqlite:" + absolutePath);
            configureConnection(openedConnection);
            initializeSchema(openedConnection);
            return openedConnection;
        } catch (SQLException | IOException exception) {
            if (openedConnection != null) {
                try {
                    openedConnection.close();
                } catch (SQLException closeFailure) {
                    exception.addSuppressed(closeFailure);
                }
            }
            throw new SnapshotRepositoryException("failed to open SQLite snapshot repository", exception);
        }
    }

    @Override
    public synchronized SnapshotWriteResult save(InventorySnapshot rootSnapshot) {
        Objects.requireNonNull(rootSnapshot, "rootSnapshot must not be null");
        snapshotValidator.flatten(rootSnapshot);

        try {
            connection.setAutoCommit(false);
            Optional<Instant> existingCaptureTime = findCaptureTime(rootSnapshot.container().id());
            if (existingCaptureTime.isPresent()
                    && rootSnapshot.capturedAt().isBefore(existingCaptureTime.orElseThrow())) {
                connection.rollback();
                return SnapshotWriteResult.IGNORED_STALE;
            }

            deleteRoot(rootSnapshot.container().id());
            insertRoot(rootSnapshot);
            insertSnapshot(rootSnapshot, rootSnapshot.container().id(), null, null);
            connection.commit();
            return existingCaptureTime.isPresent()
                    ? SnapshotWriteResult.REPLACED
                    : SnapshotWriteResult.ADDED;
        } catch (SQLException exception) {
            rollbackAfterFailure(exception);
            throw new SnapshotRepositoryException("failed to save snapshot", exception);
        } finally {
            restoreAutoCommit();
        }
    }

    @Override
    public synchronized Optional<InventorySnapshot> find(ContainerId rootContainerId) {
        Objects.requireNonNull(rootContainerId, "rootContainerId must not be null");
        try {
            if (findCaptureTime(rootContainerId).isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(readSnapshot(rootContainerId, rootContainerId, new HashSet<>()));
        } catch (SQLException exception) {
            throw new SnapshotRepositoryException("failed to read snapshot " + rootContainerId, exception);
        }
    }

    @Override
    public synchronized List<InventorySnapshot> findAll() {
        List<ContainerId> rootIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT root_container_id FROM snapshot_roots ORDER BY root_container_id"
        ); ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                rootIds.add(new ContainerId(result.getString(1)));
            }

            List<InventorySnapshot> snapshots = new ArrayList<>(rootIds.size());
            for (ContainerId rootId : rootIds) {
                snapshots.add(readSnapshot(rootId, rootId, new HashSet<>()));
            }
            return List.copyOf(snapshots);
        } catch (SQLException exception) {
            throw new SnapshotRepositoryException("failed to read snapshots", exception);
        }
    }

    @Override
    public synchronized boolean delete(ContainerId rootContainerId) {
        Objects.requireNonNull(rootContainerId, "rootContainerId must not be null");
        try {
            return deleteRoot(rootContainerId) > 0;
        } catch (SQLException exception) {
            throw new SnapshotRepositoryException("failed to delete snapshot " + rootContainerId, exception);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException exception) {
            throw new SnapshotRepositoryException("failed to close SQLite snapshot repository", exception);
        }
    }

    private static void configureConnection(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA journal_mode = WAL");
        }
    }

    private static void initializeSchema(Connection connection) throws SQLException {
        int currentVersion;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            currentVersion = result.next() ? result.getInt(1) : 0;
        }
        if (currentVersion > SCHEMA_VERSION) {
            throw new SQLException(
                    "database schema version " + currentVersion + " is newer than supported " + SCHEMA_VERSION
            );
        }
        if (currentVersion == SCHEMA_VERSION) {
            return;
        }

        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE snapshot_roots (
                        root_container_id TEXT PRIMARY KEY,
                        captured_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE containers (
                        root_container_id TEXT NOT NULL,
                        container_id TEXT NOT NULL,
                        parent_container_id TEXT,
                        parent_slot INTEGER,
                        type_namespace TEXT NOT NULL,
                        type_path TEXT NOT NULL,
                        location_kind TEXT NOT NULL CHECK (location_kind IN ('WORLD', 'LOGICAL')),
                        scope TEXT NOT NULL,
                        dimension_namespace TEXT,
                        dimension_path TEXT,
                        x INTEGER,
                        y INTEGER,
                        z INTEGER,
                        logical_key TEXT,
                        captured_at TEXT NOT NULL,
                        slot_count INTEGER NOT NULL CHECK (slot_count >= 0),
                        PRIMARY KEY (root_container_id, container_id),
                        FOREIGN KEY (root_container_id)
                            REFERENCES snapshot_roots(root_container_id) ON DELETE CASCADE,
                        FOREIGN KEY (root_container_id, parent_container_id)
                            REFERENCES containers(root_container_id, container_id),
                        CHECK (
                            (parent_container_id IS NULL AND parent_slot IS NULL)
                            OR (parent_container_id IS NOT NULL AND parent_slot >= 0)
                        )
                    )
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX containers_parent_slot
                    ON containers(root_container_id, parent_container_id, parent_slot)
                    WHERE parent_container_id IS NOT NULL
                    """);
            statement.execute("""
                    CREATE TABLE container_metadata (
                        root_container_id TEXT NOT NULL,
                        container_id TEXT NOT NULL,
                        metadata_key TEXT NOT NULL,
                        metadata_value TEXT NOT NULL,
                        PRIMARY KEY (root_container_id, container_id, metadata_key),
                        FOREIGN KEY (root_container_id, container_id)
                            REFERENCES containers(root_container_id, container_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE slots (
                        root_container_id TEXT NOT NULL,
                        container_id TEXT NOT NULL,
                        slot_index INTEGER NOT NULL CHECK (slot_index >= 0),
                        item_namespace TEXT NOT NULL,
                        item_path TEXT NOT NULL,
                        item_variant TEXT NOT NULL,
                        item_count INTEGER NOT NULL CHECK (item_count > 0),
                        PRIMARY KEY (root_container_id, container_id, slot_index),
                        FOREIGN KEY (root_container_id, container_id)
                            REFERENCES containers(root_container_id, container_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE INDEX slots_item_identity
                    ON slots(item_namespace, item_path, item_variant)
                    """);
            statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private Optional<Instant> findCaptureTime(ContainerId rootContainerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT captured_at FROM snapshot_roots WHERE root_container_id = ?"
        )) {
            statement.setString(1, rootContainerId.value());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(Instant.parse(result.getString(1)))
                        : Optional.empty();
            }
        }
    }

    private void insertRoot(InventorySnapshot rootSnapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO snapshot_roots(root_container_id, captured_at) VALUES (?, ?)"
        )) {
            statement.setString(1, rootSnapshot.container().id().value());
            statement.setString(2, rootSnapshot.capturedAt().toString());
            statement.executeUpdate();
        }
    }

    private void insertSnapshot(
            InventorySnapshot snapshot,
            ContainerId rootId,
            ContainerId parentId,
            Integer parentSlot
    ) throws SQLException {
        insertContainer(snapshot, rootId, parentId, parentSlot);
        insertMetadata(snapshot.container(), rootId);
        for (SlotSnapshot slot : snapshot.slots()) {
            insertSlot(slot, snapshot.container().id(), rootId);
            if (slot.nestedContainer().isPresent()) {
                insertSnapshot(slot.nestedContainer().orElseThrow(), rootId, snapshot.container().id(), slot.slot());
            }
        }
    }

    private void insertContainer(
            InventorySnapshot snapshot,
            ContainerId rootId,
            ContainerId parentId,
            Integer parentSlot
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO containers(
                    root_container_id, container_id, parent_container_id, parent_slot,
                    type_namespace, type_path, location_kind, scope,
                    dimension_namespace, dimension_path, x, y, z, logical_key,
                    captured_at, slot_count
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ContainerRecord container = snapshot.container();
            statement.setString(1, rootId.value());
            statement.setString(2, container.id().value());
            setNullableString(statement, 3, parentId == null ? null : parentId.value());
            setNullableInteger(statement, 4, parentSlot);
            statement.setString(5, container.type().id().namespace());
            statement.setString(6, container.type().id().path());

            ContainerLocation location = container.location();
            statement.setString(8, location.scope());
            if (location instanceof WorldLocation world) {
                statement.setString(7, "WORLD");
                statement.setString(9, world.dimension().namespace());
                statement.setString(10, world.dimension().path());
                statement.setInt(11, world.x());
                statement.setInt(12, world.y());
                statement.setInt(13, world.z());
                statement.setNull(14, Types.VARCHAR);
            } else if (location instanceof LogicalLocation logical) {
                statement.setString(7, "LOGICAL");
                statement.setNull(9, Types.VARCHAR);
                statement.setNull(10, Types.VARCHAR);
                statement.setNull(11, Types.INTEGER);
                statement.setNull(12, Types.INTEGER);
                statement.setNull(13, Types.INTEGER);
                statement.setString(14, logical.key());
            } else {
                throw new IllegalArgumentException("unsupported container location: " + location.getClass());
            }
            statement.setString(15, snapshot.capturedAt().toString());
            statement.setInt(16, snapshot.slotCount());
            statement.executeUpdate();
        }
    }

    private void insertMetadata(ContainerRecord container, ContainerId rootId) throws SQLException {
        if (container.metadata().isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO container_metadata(
                    root_container_id, container_id, metadata_key, metadata_value
                ) VALUES (?, ?, ?, ?)
                """)) {
            for (Map.Entry<String, String> metadata : container.metadata().entrySet()) {
                statement.setString(1, rootId.value());
                statement.setString(2, container.id().value());
                statement.setString(3, metadata.getKey());
                statement.setString(4, metadata.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertSlot(SlotSnapshot slot, ContainerId containerId, ContainerId rootId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO slots(
                    root_container_id, container_id, slot_index,
                    item_namespace, item_path, item_variant, item_count
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, rootId.value());
            statement.setString(2, containerId.value());
            statement.setInt(3, slot.slot());
            statement.setString(4, slot.stack().item().namespace());
            statement.setString(5, slot.stack().item().id());
            statement.setString(6, slot.stack().item().variant());
            statement.setLong(7, slot.stack().count());
            statement.executeUpdate();
        }
    }

    private InventorySnapshot readSnapshot(
            ContainerId rootId,
            ContainerId containerId,
            Set<ContainerId> activePath
    ) throws SQLException {
        if (!activePath.add(containerId)) {
            throw new SQLException("cycle detected in stored container tree at " + containerId);
        }
        try {
            StoredContainer stored = readContainer(rootId, containerId);
            List<SlotSnapshot> slots = readSlots(rootId, containerId, activePath);
            return new InventorySnapshot(stored.container(), stored.slotCount(), stored.capturedAt(), slots);
        } finally {
            activePath.remove(containerId);
        }
    }

    private StoredContainer readContainer(ContainerId rootId, ContainerId containerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT type_namespace, type_path, location_kind, scope,
                       dimension_namespace, dimension_path, x, y, z, logical_key,
                       captured_at, slot_count
                FROM containers
                WHERE root_container_id = ? AND container_id = ?
                """)) {
            statement.setString(1, rootId.value());
            statement.setString(2, containerId.value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("missing stored container " + containerId + " for root " + rootId);
                }
                ContainerType type = new ContainerType(new NamespacedId(
                        result.getString("type_namespace"),
                        result.getString("type_path")
                ));
                String scope = result.getString("scope");
                ContainerLocation location = switch (result.getString("location_kind")) {
                    case "WORLD" -> new WorldLocation(
                            scope,
                            new NamespacedId(
                                    result.getString("dimension_namespace"),
                                    result.getString("dimension_path")
                            ),
                            result.getInt("x"),
                            result.getInt("y"),
                            result.getInt("z")
                    );
                    case "LOGICAL" -> new LogicalLocation(scope, result.getString("logical_key"));
                    default -> throw new SQLException("unknown location kind");
                };
                ContainerRecord container = new ContainerRecord(
                        containerId,
                        type,
                        location,
                        readMetadata(rootId, containerId)
                );
                return new StoredContainer(
                        container,
                        result.getInt("slot_count"),
                        Instant.parse(result.getString("captured_at"))
                );
            }
        }
    }

    private Map<String, String> readMetadata(ContainerId rootId, ContainerId containerId) throws SQLException {
        Map<String, String> metadata = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT metadata_key, metadata_value
                FROM container_metadata
                WHERE root_container_id = ? AND container_id = ?
                ORDER BY metadata_key
                """)) {
            statement.setString(1, rootId.value());
            statement.setString(2, containerId.value());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    metadata.put(result.getString(1), result.getString(2));
                }
            }
        }
        return metadata;
    }

    private List<SlotSnapshot> readSlots(
            ContainerId rootId,
            ContainerId containerId,
            Set<ContainerId> activePath
    ) throws SQLException {
        List<StoredSlot> storedSlots = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT slot_index, item_namespace, item_path, item_variant, item_count
                FROM slots
                WHERE root_container_id = ? AND container_id = ?
                ORDER BY slot_index
                """)) {
            statement.setString(1, rootId.value());
            statement.setString(2, containerId.value());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    storedSlots.add(new StoredSlot(
                            result.getInt("slot_index"),
                            new ItemStackInfo(
                                    new ItemKey(
                                            new NamespacedId(
                                                    result.getString("item_namespace"),
                                                    result.getString("item_path")
                                            ),
                                            result.getString("item_variant")
                                    ),
                                    result.getLong("item_count")
                            )
                    ));
                }
            }
        }

        List<SlotSnapshot> slots = new ArrayList<>(storedSlots.size());
        for (StoredSlot storedSlot : storedSlots) {
            Optional<ContainerId> childId = findChild(rootId, containerId, storedSlot.slot());
            if (childId.isPresent()) {
                slots.add(SlotSnapshot.nested(
                        storedSlot.slot(),
                        storedSlot.stack(),
                        readSnapshot(rootId, childId.orElseThrow(), activePath)
                ));
            } else {
                slots.add(new SlotSnapshot(storedSlot.slot(), storedSlot.stack()));
            }
        }
        return slots;
    }

    private Optional<ContainerId> findChild(
            ContainerId rootId,
            ContainerId parentId,
            int parentSlot
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT container_id
                FROM containers
                WHERE root_container_id = ? AND parent_container_id = ? AND parent_slot = ?
                """)) {
            statement.setString(1, rootId.value());
            statement.setString(2, parentId.value());
            statement.setInt(3, parentSlot);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(new ContainerId(result.getString(1)))
                        : Optional.empty();
            }
        }
    }

    private int deleteRoot(ContainerId rootContainerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM snapshot_roots WHERE root_container_id = ?"
        )) {
            statement.setString(1, rootContainerId.value());
            return statement.executeUpdate();
        }
    }

    private static void setNullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setNullableInteger(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private void rollbackAfterFailure(SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private void restoreAutoCommit() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException exception) {
            throw new SnapshotRepositoryException("failed to restore SQLite auto-commit", exception);
        }
    }

    private record StoredContainer(ContainerRecord container, int slotCount, Instant capturedAt) {
    }

    private record StoredSlot(int slot, ItemStackInfo stack) {
    }
}
