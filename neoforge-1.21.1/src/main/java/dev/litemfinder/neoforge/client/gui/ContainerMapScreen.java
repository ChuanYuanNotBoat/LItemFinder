package dev.litemfinder.neoforge.client.gui;

import com.mojang.logging.LogUtils;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;
import dev.litemfinder.core.search.SearchQuery;
import dev.litemfinder.neoforge.client.ItemFinderClientApi;
import dev.litemfinder.neoforge.client.navigation.NavigationGateway;
import dev.litemfinder.neoforge.client.view.AcquisitionDraft;
import dev.litemfinder.neoforge.client.view.AcquisitionPlan;
import dev.litemfinder.neoforge.client.view.ContainerOverview;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Full-screen container coordinate map. It renders only recorded locations, never guessed terrain. */
public final class ContainerMapScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int HEADER = 48;
    private static final int FOOTER = 28;
    private static final int PANEL_WIDTH = 174;
    private static final double MIN_ZOOM = 0.05;
    private static final double MAX_ZOOM = 8.0;

    private final Screen parent;
    private final ItemFinderClientApi api;
    private final AcquisitionDraft draft;
    private final java.util.Optional<NavigationGateway> navigation = NavigationGateway.discover();
    private ContainerOverview overview = new ContainerOverview(List.of());
    private AcquisitionPlan routePlan = new AcquisitionPlan(List.of(), Map.of());
    private NavigationGateway.Overlay navigationOverlay;
    private List<NamespacedId> dimensions = List.of();
    private NamespacedId dimension;
    private ContainerId selected;
    private NamespacedId highlightedItem;
    private Set<ContainerId> highlightedRoots = Set.of();
    private List<Hit> hits = List.of();
    private List<Hit> logicalHits = List.of();
    private EditBox searchBox;
    private Button dimensionButton;
    private Button modeButton;
    private Button lowerButton;
    private Button upperButton;
    private boolean localView;
    private boolean panning;
    private boolean initializedView;
    private double centerX;
    private double centerZ;
    private double zoom = 1;
    private double localScale = 4;
    private double rotation;
    private int layerY;
    private int ticks;
    private long navigationRequestId;
    private boolean providerFailureLogged;
    private boolean navigationPending;
    private int lastNavigationTick = -200;
    private WorldPosition lastNavigationOrigin;
    private List<WorldLocation> lastNavigationStops = List.of();

    public ContainerMapScreen(Screen parent, ItemFinderClientApi api,
                              AcquisitionDraft draft, NamespacedId highlightedItem) {
        super(Component.translatable("gui.litemfinder.map.title"));
        this.parent = Objects.requireNonNull(parent, "parent must not be null");
        this.api = Objects.requireNonNull(api, "api must not be null");
        this.draft = Objects.requireNonNull(draft, "draft must not be null");
        this.highlightedItem = highlightedItem;
    }

    @Override
    protected void init() {
        searchBox = addRenderableWidget(new EditBox(font, 12, 25, Math.max(70, width - 437), 18,
                Component.translatable("gui.litemfinder.map.search")));
        searchBox.setHint(Component.translatable("gui.litemfinder.map.search"));
        searchBox.setMaxLength(120);
        searchBox.setResponder(value -> refreshHighlights());
        dimensionButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> cycleDimension())
                .bounds(Math.max(90, width - 352), 24, 124, 20).build());
        modeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> toggleMode())
                .bounds(width - 220, 24, 58, 20).build());
        lowerButton = addRenderableWidget(Button.builder(Component.literal("Y−"), ignored -> layerY--)
                .bounds(width - 154, 24, 41, 20).build());
        upperButton = addRenderableWidget(Button.builder(Component.literal("Y+"), ignored -> layerY++)
                .bounds(width - 109, 24, 41, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.litemfinder.back"), ignored -> onClose())
                .bounds(width - 64, 24, 52, 20).build());
        refresh();
        updateButtons();
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 40 == 0) {
            refresh();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0101720);
        graphics.fill(0, 0, width, HEADER, 0xFF1C2834);
        graphics.drawString(font, title, 12, 8, 0xFFF2F4F6);
        int right = mapRight();
        graphics.fill(8, HEADER, right, mapBottom(), 0xFF17232C);
        graphics.enableScissor(8, HEADER, right, mapBottom());
        if (localView) {
            renderLocal(graphics);
        } else {
            renderGrid(graphics);
            renderRoute(graphics);
            renderMapMarkers(graphics);
        }
        graphics.disableScissor();
        renderPanel(graphics);
        graphics.fill(0, mapBottom(), width, height, 0xFF1C2834);
        boolean actualRoute = navigationOverlay != null && !navigationOverlay.segments().isEmpty();
        String footer = Component.translatable(localView ? "gui.litemfinder.map.local_hint"
                : actualRoute && navigationOverlay.status() == NavigationGateway.Status.PARTIAL
                ? "gui.litemfinder.map.partial_hint"
                : actualRoute ? "gui.litemfinder.map.actual_hint" : "gui.litemfinder.map.overview_hint")
                .getString();
        graphics.drawString(font, font.plainSubstrByWidth(footer, width - 24), 12,
                height - 18, 0xFFB8C6D2);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderGrid(GuiGraphics graphics) {
        int left = 8;
        int right = mapRight();
        int top = HEADER;
        int bottom = mapBottom();
        double step = zoom >= 2 ? 16 : zoom >= 0.5 ? 64 : zoom >= 0.15 ? 256 : 1024;
        int firstX = (int) Math.floor((centerX - (mapCenterX() - left) / zoom) / step);
        int lastX = (int) Math.ceil((centerX + (right - mapCenterX()) / zoom) / step);
        for (int index = firstX; index <= lastX && index - firstX < 300; index++) {
            int x = (int) Math.round(screenX(index * step));
            graphics.vLine(x, top, bottom, 0xFF2C4151);
        }
        int firstZ = (int) Math.floor((centerZ - (mapCenterY() - top) / zoom) / step);
        int lastZ = (int) Math.ceil((centerZ + (bottom - mapCenterY()) / zoom) / step);
        for (int index = firstZ; index <= lastZ && index - firstZ < 300; index++) {
            int y = (int) Math.round(screenZ(index * step));
            graphics.hLine(left, right, y, 0xFF2C4151);
        }
    }

    private void renderMapMarkers(GuiGraphics graphics) {
        Map<Long, List<ContainerOverview.RootRow>> clusters = new HashMap<>();
        for (ContainerOverview.RootRow row : worldRoots()) {
            WorldLocation location = (WorldLocation) row.container().location();
            int x = (int) Math.round(screenX(location.x()));
            int y = (int) Math.round(screenZ(location.z()));
            if (x < 4 || x > mapRight() + 4 || y < HEADER - 4 || y > mapBottom() + 4) {
                continue;
            }
            int cellX = zoom < 0.35 ? Math.floorDiv(x, 14) : x;
            int cellY = zoom < 0.35 ? Math.floorDiv(y, 14) : y;
            long key = ((long) cellX << 32) ^ (cellY & 0xffffffffL);
            clusters.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        List<Hit> markers = new ArrayList<>();
        for (List<ContainerOverview.RootRow> cluster : clusters.values()) {
            double averageX = cluster.stream().mapToDouble(row -> ((WorldLocation) row.container().location()).x())
                    .average().orElse(0);
            double averageZ = cluster.stream().mapToDouble(row -> ((WorldLocation) row.container().location()).z())
                    .average().orElse(0);
            int x = (int) Math.round(screenX(averageX));
            int y = (int) Math.round(screenZ(averageZ));
            boolean active = cluster.stream().anyMatch(row -> row.container().id().equals(selected));
            boolean highlighted = cluster.stream().anyMatch(row -> highlightedRoots.contains(row.container().id()));
            int color = active ? 0xFFFFD36B : highlighted ? 0xFF72E7B2 : 0xFF8EB7D9;
            int radius = cluster.size() > 1 ? 7 : 4;
            graphics.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, color);
            if (cluster.size() > 1) {
                graphics.drawCenteredString(font, Integer.toString(cluster.size()), x, y - 4, 0xFF101820);
            }
            markers.add(new Hit(x - radius - 3, y - radius - 3, x + radius + 4, y + radius + 4,
                    List.copyOf(cluster)));
        }
        hits = List.copyOf(markers);
    }

    private void renderRoute(GuiGraphics graphics) {
        if (navigationOverlay != null && !navigationOverlay.segments().isEmpty()) {
            renderNavigationOverlay(graphics);
            return;
        }
        Set<ContainerId> seen = new HashSet<>();
        List<WorldLocation> points = routePlan.picks().stream()
                .filter(pick -> seen.add(pick.source().rootContainer().id()))
                .map(pick -> pick.source().rootContainer().location())
                .filter(WorldLocation.class::isInstance)
                .map(WorldLocation.class::cast)
                .filter(location -> location.dimension().equals(dimension))
                .toList();
        for (int index = 1; index < points.size(); index++) {
            WorldLocation from = points.get(index - 1);
            WorldLocation to = points.get(index);
            drawDottedLine(graphics, (int) screenX(from.x()), (int) screenZ(from.z()),
                    (int) screenX(to.x()), (int) screenZ(to.z()), 0xFFEDC877, 7);
        }
    }

    private void renderNavigationOverlay(GuiGraphics graphics) {
        for (NavigationGateway.Segment segment : navigationOverlay.segments()) {
            int color = routeColor(segment.mode());
            for (int index = 1; index < segment.points().size(); index++) {
                WorldPosition from = segment.points().get(index - 1);
                WorldPosition to = segment.points().get(index);
                if (lastNavigationOrigin == null
                        || !from.scope().equals(lastNavigationOrigin.scope())
                        || !to.scope().equals(lastNavigationOrigin.scope())) {
                    continue;
                }
                if (!from.dimension().equals(dimension) || !to.dimension().equals(dimension)) {
                    if (from.dimension().equals(dimension)) {
                        int x = (int) screenX(from.x());
                        int y = (int) screenZ(from.z());
                        graphics.fill(x - 5, y - 5, x + 6, y + 6, color);
                    }
                    continue;
                }
                drawDottedLine(graphics, (int) screenX(from.x()), (int) screenZ(from.z()),
                        (int) screenX(to.x()), (int) screenZ(to.z()), color, 2);
            }
        }
    }

    private void renderLocal(GuiGraphics graphics) {
        ContainerOverview.RootRow focus = selectedRow();
        if (focus == null || !(focus.container().location() instanceof WorldLocation center)) {
            return;
        }
        List<Hit> markers = new ArrayList<>();
        Set<ContainerId> planRoots = new HashSet<>();
        routePlan.picks().forEach(pick -> planRoots.add(pick.source().rootContainer().id()));
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        renderLocalNavigationOverlay(graphics, center, cos, sin);
        for (ContainerOverview.RootRow row : worldRoots()) {
            WorldLocation location = (WorldLocation) row.container().location();
            int dy = location.y() - layerY;
            if (Math.abs(location.x() - center.x()) > 64 || Math.abs(location.z() - center.z()) > 64
                    || Math.abs(dy) > 4) {
                continue;
            }
            int x = localX(location.x(), location.z(), center, cos, sin);
            int y = localY(location.x(), location.y(), location.z(), center, cos, sin);
            int color = row.container().id().equals(selected) ? 0xFFFFD36B
                    : planRoots.contains(row.container().id()) ? 0xFF72E7B2 : 0xFF8EB7D9;
            graphics.fill(x - 5, y - 5, x + 6, y + 6, color);
            graphics.drawString(font, Integer.toString(location.y()), x + 8, y - 4, 0xFFDAE8F2);
            markers.add(new Hit(x - 8, y - 8, x + 8, y + 8, List.of(row)));
        }
        hits = List.copyOf(markers);
        graphics.drawString(font, Component.translatable("gui.litemfinder.map.y_layer", layerY),
                15, HEADER + 10, 0xFFFFD36B);
    }

    private void renderLocalNavigationOverlay(GuiGraphics graphics, WorldLocation center,
                                              double cos, double sin) {
        if (navigationOverlay == null || lastNavigationOrigin == null) {
            return;
        }
        for (NavigationGateway.Segment segment : navigationOverlay.segments()) {
            for (int index = 1; index < segment.points().size(); index++) {
                WorldPosition from = segment.points().get(index - 1);
                WorldPosition to = segment.points().get(index);
                if (!from.scope().equals(center.scope()) || !to.scope().equals(center.scope())
                        || !from.dimension().equals(center.dimension())
                        || !to.dimension().equals(center.dimension())
                        || !withinLocalView(from, center) || !withinLocalView(to, center)) {
                    continue;
                }
                drawDottedLine(graphics,
                        localX(from.x(), from.z(), center, cos, sin),
                        localY(from.x(), from.y(), from.z(), center, cos, sin),
                        localX(to.x(), to.z(), center, cos, sin),
                        localY(to.x(), to.y(), to.z(), center, cos, sin),
                        routeColor(segment.mode()), 2);
            }
        }
    }

    private boolean withinLocalView(WorldPosition point, WorldLocation center) {
        return Math.abs(point.x() - center.x()) <= 64
                && Math.abs(point.z() - center.z()) <= 64
                && Math.abs(point.y() - layerY) <= 4;
    }

    private int localX(double x, double z, WorldLocation center, double cos, double sin) {
        double dx = x - center.x();
        double dz = z - center.z();
        return (int) Math.round(mapCenterX() + ((dx * cos - dz * sin) - (dx * sin + dz * cos))
                * localScale);
    }

    private int localY(double x, double y, double z, WorldLocation center, double cos, double sin) {
        double dx = x - center.x();
        double dz = z - center.z();
        return (int) Math.round(mapCenterY() + ((dx * cos - dz * sin) + (dx * sin + dz * cos))
                * localScale * 0.45 - (y - center.y()) * localScale * 1.3);
    }

    private static int routeColor(String mode) {
        return switch (mode) {
            case "RAIL" -> 0xFFEC8AC8;
            case "WATER_BOAT", "ICE_BOAT" -> 0xFF74CDEB;
            case "PORTAL" -> 0xFFB38CF2;
            default -> 0xFF72E7B2;
        };
    }

    private void renderPanel(GuiGraphics graphics) {
        int left = mapRight() + 6;
        graphics.fill(mapRight(), HEADER, width, mapBottom(), 0xFF202E3A);
        ContainerOverview.RootRow row = selectedRow();
        int y = HEADER + 9;
        graphics.drawString(font, Component.translatable("gui.litemfinder.map.details"),
                left, y, 0xFFF2F4F6);
        y += 19;
        if (row != null) {
            y = panelLine(graphics, row.container().type().id().toString(), left, y, 0xFFD8E7F2);
            if (row.container().location() instanceof WorldLocation world) {
                y = panelLine(graphics, world.dimension().toString(), left, y, 0xFFB8C6D2);
                y = panelLine(graphics, world.x() + ", " + world.y() + ", " + world.z(),
                        left, y, 0xFFFFD36B);
            } else {
                y = panelLine(graphics, Component.translatable("gui.litemfinder.plan.logical").getString(),
                        left, y, 0xFFB8C6D2);
            }
            y = panelLine(graphics, Component.translatable("gui.litemfinder.map.slots",
                    row.occupiedSlots(), row.slots()).getString(), left, y, 0xFFB8C6D2);
            panelLine(graphics, row.observedAt().toString(), left, y, 0xFFB8C6D2);
        } else {
            y = panelLine(graphics, Component.translatable("gui.litemfinder.map.select").getString(),
                    left, y, 0xFFB8C6D2);
        }
        int logicalY = Math.max(y + 26, HEADER + 121);
        graphics.drawString(font, Component.translatable("gui.litemfinder.map.logical"),
                left, logicalY, 0xFFF2F4F6);
        List<Hit> sidebar = new ArrayList<>();
        logicalY += 17;
        for (ContainerOverview.RootRow logical : overview.roots()) {
            if (logical.container().location() instanceof WorldLocation || logicalY + 14 >= mapBottom()) {
                continue;
            }
            String label = logical.container().type().id().path() + " " + logical.occupiedSlots();
            graphics.drawString(font, font.plainSubstrByWidth(label, PANEL_WIDTH - 18),
                    left, logicalY, logical.container().id().equals(selected) ? 0xFFFFD36B : 0xFFAED4E8);
            sidebar.add(new Hit(left, logicalY - 2, width - 8, logicalY + 12, List.of(logical)));
            logicalY += 17;
        }
        logicalHits = List.copyOf(sidebar);
    }

    private int panelLine(GuiGraphics graphics, String value, int x, int y, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(value, PANEL_WIDTH - 18), x, y, color);
        return y + 16;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0 || mouseY < HEADER || mouseY >= mapBottom()) {
            return false;
        }
        for (Hit hit : logicalHits) {
            if (hit.contains(mouseX, mouseY)) {
                selected = hit.rows().getFirst().container().id();
                return true;
            }
        }
        if (mouseX < mapRight()) {
            for (Hit hit : hits) {
                if (hit.contains(mouseX, mouseY)) {
                    if (hit.rows().size() > 1 && !localView) {
                        centerX = hit.rows().stream().mapToDouble(row ->
                                ((WorldLocation) row.container().location()).x()).average().orElse(centerX);
                        centerZ = hit.rows().stream().mapToDouble(row ->
                                ((WorldLocation) row.container().location()).z()).average().orElse(centerZ);
                        zoom = Math.min(MAX_ZOOM, zoom * 2);
                    } else {
                        selected = hit.rows().getFirst().container().id();
                        if (hit.rows().getFirst().container().location() instanceof WorldLocation world) {
                            layerY = world.y();
                        }
                    }
                    return true;
                }
            }
            panning = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && panning) {
            if (localView) {
                rotation += dragX * 0.01;
            } else {
                centerX -= dragX / zoom;
                centerZ -= dragY / zoom;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        panning = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseX < mapRight() && mouseY >= HEADER && mouseY < mapBottom() && deltaY != 0) {
            if (localView) {
                localScale = Math.max(0.5, Math.min(16, localScale * Math.pow(1.2, deltaY)));
            } else {
                double worldX = centerX + (mouseX - mapCenterX()) / zoom;
                double worldZ = centerZ + (mouseY - mapCenterY()) / zoom;
                zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * Math.pow(1.2, deltaY)));
                centerX = worldX - (mouseX - mapCenterX()) / zoom;
                centerZ = worldZ - (mouseY - mapCenterY()) / zoom;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        navigationRequestId++;
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void refresh() {
        overview = api.containerOverview();
        routePlan = api.plan(draft.selections());
        refreshNavigation();
        dimensions = overview.roots().stream()
                .map(row -> row.container().location())
                .filter(WorldLocation.class::isInstance)
                .map(location -> ((WorldLocation) location).dimension())
                .distinct().sorted().toList();
        if (dimension == null || !dimensions.contains(dimension)) {
            NamespacedId playerDimension = minecraft != null && minecraft.level != null
                    ? NamespacedId.parse(minecraft.level.dimension().location().toString()) : null;
            dimension = dimensions.contains(playerDimension) ? playerDimension
                    : dimensions.isEmpty() ? null : dimensions.getFirst();
            initializedView = false;
        }
        if (selected != null && selectedRow() == null) {
            selected = null;
            localView = false;
        }
        if (!initializedView) {
            fitView();
            initializedView = true;
        }
        refreshHighlights();
        updateButtons();
    }

    private void refreshNavigation() {
        if (navigation.isPresent() && minecraft != null && minecraft.player != null
                && minecraft.level != null) {
            Set<ContainerId> seen = new HashSet<>();
            List<WorldLocation> stops = routePlan.picks().stream()
                    .filter(pick -> seen.add(pick.source().rootContainer().id()))
                    .map(pick -> pick.source().rootContainer().location())
                    .filter(WorldLocation.class::isInstance)
                    .map(WorldLocation.class::cast)
                    .toList();
            if (!stops.isEmpty()) {
                var player = minecraft.player.position();
                WorldPosition origin = new WorldPosition(stops.getFirst().scope(),
                        NamespacedId.parse(minecraft.level.dimension().location().toString()),
                        Math.floor(player.x), Math.floor(player.y), Math.floor(player.z));
                boolean changed = !stops.equals(lastNavigationStops)
                        || lastNavigationOrigin == null
                        || !origin.scope().equals(lastNavigationOrigin.scope())
                        || !origin.dimension().equals(lastNavigationOrigin.dimension())
                        || Math.abs(origin.x() - lastNavigationOrigin.x()) > 8
                        || Math.abs(origin.y() - lastNavigationOrigin.y()) > 8
                        || Math.abs(origin.z() - lastNavigationOrigin.z()) > 8;
                if ((!changed && navigationPending) || (!changed && ticks - lastNavigationTick < 200)) {
                    return;
                }
                lastNavigationStops = stops;
                lastNavigationOrigin = origin;
                lastNavigationTick = ticks;
                navigationPending = true;
                navigationOverlay = null;
                long requestId = ++navigationRequestId;
                try {
                    navigation.orElseThrow().route(origin, stops).whenComplete((overlay, failure) ->
                            minecraft.execute(() -> {
                                if (requestId != navigationRequestId) {
                                    return;
                                }
                                navigationPending = false;
                                if (failure != null) {
                                    logProviderFailure(failure);
                                    return;
                                }
                                if (overlay != null && (overlay.status() == NavigationGateway.Status.FOUND
                                        || overlay.status() == NavigationGateway.Status.PARTIAL)) {
                                    navigationOverlay = overlay;
                                }
                            }));
                } catch (RuntimeException failedProvider) {
                    navigationPending = false;
                    logProviderFailure(failedProvider);
                }
                return;
            }
        }
        navigationRequestId++;
        navigationPending = false;
        navigationOverlay = null;
        lastNavigationStops = List.of();
        lastNavigationOrigin = null;
    }

    private void refreshHighlights() {
        if (searchBox == null) {
            return;
        }
        String query = searchBox.getValue().trim();
        if (query.isEmpty() && highlightedItem == null) {
            highlightedRoots = Set.of();
            return;
        }
        Set<ContainerId> roots = new HashSet<>();
        if (query.isEmpty()) {
            api.findByItemId(highlightedItem).entries().forEach(entry ->
                    roots.add(entry.rootContainer().id()));
        } else {
            api.search(SearchQuery.all().withText(query)).results().forEach(result ->
                    result.entries().forEach(entry -> roots.add(entry.rootContainer().id())));
        }
        highlightedRoots = Set.copyOf(roots);
    }

    private void fitView() {
        List<ContainerOverview.RootRow> rows = worldRoots();
        if (rows.isEmpty()) {
            centerX = 0;
            centerZ = 0;
            zoom = 1;
            return;
        }
        double minX = rows.stream().mapToDouble(row -> ((WorldLocation) row.container().location()).x())
                .min().orElse(0);
        double maxX = rows.stream().mapToDouble(row -> ((WorldLocation) row.container().location()).x())
                .max().orElse(0);
        double minZ = rows.stream().mapToDouble(row -> ((WorldLocation) row.container().location()).z())
                .min().orElse(0);
        double maxZ = rows.stream().mapToDouble(row -> ((WorldLocation) row.container().location()).z())
                .max().orElse(0);
        centerX = (minX + maxX) / 2;
        centerZ = (minZ + maxZ) / 2;
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM,
                Math.min((mapRight() - 50.0) / Math.max(16, maxX - minX + 16),
                        (mapBottom() - HEADER - 40.0) / Math.max(16, maxZ - minZ + 16))));
    }

    private void cycleDimension() {
        if (dimensions.isEmpty()) {
            return;
        }
        dimension = dimensions.get((dimensions.indexOf(dimension) + 1) % dimensions.size());
        selected = null;
        localView = false;
        fitView();
        updateButtons();
    }

    private void toggleMode() {
        if (!localView) {
            ContainerOverview.RootRow row = selectedRow();
            if (row == null || !(row.container().location() instanceof WorldLocation world)) {
                return;
            }
            layerY = world.y();
        }
        localView = !localView;
        updateButtons();
    }

    private void updateButtons() {
        if (dimensionButton == null || modeButton == null) {
            return;
        }
        dimensionButton.setMessage(dimension == null ? Component.translatable("gui.litemfinder.map.no_dimension")
                : Component.literal(dimension.path()));
        modeButton.setMessage(Component.literal(localView ? "2D" : "3D"));
        lowerButton.visible = localView;
        upperButton.visible = localView;
    }

    private List<ContainerOverview.RootRow> worldRoots() {
        return overview.roots().stream()
                .filter(row -> row.container().location() instanceof WorldLocation world
                        && world.dimension().equals(dimension))
                .sorted(Comparator.comparing(row -> row.container().id()))
                .toList();
    }

    private ContainerOverview.RootRow selectedRow() {
        return overview.roots().stream().filter(row -> row.container().id().equals(selected))
                .findFirst().orElse(null);
    }

    private int mapRight() {
        return Math.max(110, width - PANEL_WIDTH - 8);
    }

    private int mapBottom() {
        return Math.max(HEADER + 1, height - FOOTER);
    }

    private double mapCenterX() {
        return (8 + mapRight()) / 2.0;
    }

    private double mapCenterY() {
        return (HEADER + mapBottom()) / 2.0;
    }

    private double screenX(double worldX) {
        return mapCenterX() + (worldX - centerX) * zoom;
    }

    private double screenZ(double worldZ) {
        return mapCenterY() + (worldZ - centerZ) * zoom;
    }

    private void logProviderFailure(Throwable failure) {
        if (!providerFailureLogged) {
            providerFailureLogged = true;
            LOGGER.warn("Optional navigation provider failed; showing schematic stops", failure);
        }
    }

    private static void drawDottedLine(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                                       int color, int spacing) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) {
            return;
        }
        for (int i = 0; i <= steps && i < 4096; i += spacing) {
            int x = (int) Math.round(x1 + (x2 - x1) * (i / (double) steps));
            int y = (int) Math.round(y1 + (y2 - y1) * (i / (double) steps));
            graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    private record Hit(int left, int top, int right, int bottom,
                       List<ContainerOverview.RootRow> rows) {

        private boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }
}
