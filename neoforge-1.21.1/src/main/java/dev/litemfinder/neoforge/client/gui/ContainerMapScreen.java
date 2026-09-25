package dev.litemfinder.neoforge.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import dev.litemfinder.core.model.ContainerId;
import dev.litemfinder.core.model.NamespacedId;
import dev.litemfinder.core.model.WorldLocation;
import dev.litemfinder.core.model.WorldPosition;
import dev.litemfinder.core.search.SearchQuery;
import dev.litemfinder.neoforge.client.ItemFinderClientApi;
import dev.litemfinder.neoforge.client.gui.map.ContainerMap3DCamera;
import dev.litemfinder.neoforge.client.gui.map.ContainerRouteOptimizer;
import dev.litemfinder.neoforge.client.gui.map.ContainerVisualResolver;
import dev.litemfinder.neoforge.client.gui.map.LoadedTerrainSampler;
import dev.litemfinder.neoforge.client.navigation.NavigationGateway;
import dev.litemfinder.neoforge.client.view.AcquisitionDraft;
import dev.litemfinder.neoforge.client.view.AcquisitionPlan;
import dev.litemfinder.neoforge.client.view.ContainerOverview;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Full-screen container map backed by recorded locations and currently loaded client terrain. */
public final class ContainerMapScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int HEADER = 48;
    private static final int FOOTER = 28;
    private static final int PANEL_WIDTH = 174;
    private static final double MIN_ZOOM = 0.05;
    private static final double MAX_ZOOM = 8.0;
    private static final double ICON_LOD_ZOOM = 2.0;
    private static final int LOCAL_RADIUS = 64;
    private static final int LOCAL_Y_RADIUS = 24;
    private static final double CLICK_DRAG_THRESHOLD = 4.0;
    private static final double MIN_GRID_STEP = 16.0;
    private static final double GRID_MIN_SCREEN_SPACING = 48.0;
    private static final double GRID_MAX_SCREEN_SPACING = 104.0;
    private static final double LOCAL_OUTLINE_EXPANSION = 1.0 / 64.0;
    private static final double TERRAIN_MIN_ZOOM = 0.18;
    private static final int TERRAIN_MAX_VISIBLE_SAMPLES = 4_096;
    private static final int TERRAIN_NEW_SAMPLES_PER_FRAME = 512;
    private static final int TERRAIN_CONTOUR_INTERVAL = 4;
    private static final int TERRAIN_MAJOR_CONTOUR_INTERVAL = 16;
    private static final MapDecoration PLAYER_MAP_DECORATION = new MapDecoration(
            MapDecorationTypes.PLAYER, (byte) 0, (byte) 0, (byte) 0, Optional.empty());

    private final Screen parent;
    private final ItemFinderClientApi api;
    private final AcquisitionDraft draft;
    private final ContainerVisualResolver visualResolver = new ContainerVisualResolver();
    private final ContainerMap3DCamera localCamera = new ContainerMap3DCamera();
    private final LoadedTerrainSampler terrainSampler = new LoadedTerrainSampler();
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
    private Button contentsButton;
    private boolean localView;
    private boolean panning;
    private int localDragButton = -1;
    private double localPressX;
    private double localPressY;
    private double localDragDistance;
    private WorldLocation localAnchor;
    private boolean initializedView;
    private double centerX;
    private double centerZ;
    private double zoom = 1;
    private double gridStep = 64.0;
    private String searchText = "";
    private int layerY;
    private int ticks;
    private long navigationRequestId;
    private boolean providerFailureLogged;
    private boolean navigationPending;
    private int lastNavigationTick = -200;
    private WorldPosition routeOrigin;
    private List<WorldLocation> orderedRouteStops = List.of();
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
        searchBox.setValue(searchText);
        searchBox.setResponder(value -> {
            searchText = value;
            refreshHighlights();
        });
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
        contentsButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.litemfinder.map.contents"), ignored -> openSelectedContents())
                .bounds(mapRight() + 6, HEADER + 112, PANEL_WIDTH - 18, 20).build());
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
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xF0101720);
        graphics.fill(0, 0, width, HEADER, 0xFF1C2834);
        graphics.drawString(font, title, 12, 8, 0xFFF2F4F6);
        int right = mapRight();
        graphics.fill(8, HEADER, right, mapBottom(), 0xFF17232C);
        graphics.enableScissor(8, HEADER, right, mapBottom());
        if (localView) {
            renderLocal(graphics);
        } else {
            renderTerrainContext(graphics);
            renderGrid(graphics);
            renderRoute(graphics);
            renderMapMarkers(graphics);
            renderPlayerPointer(graphics);
            renderMapCoordinates(graphics, mouseX, mouseY);
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

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The background was rendered before the map; super.render only needs to draw widgets.
    }

    private void renderGrid(GuiGraphics graphics) {
        int left = 8;
        int right = mapRight();
        int top = HEADER;
        int bottom = mapBottom();
        double step = stableGridStep();
        int firstX = (int) Math.floor((centerX - (mapCenterX() - left) / zoom) / step);
        int lastX = (int) Math.ceil((centerX + (right - mapCenterX()) / zoom) / step);
        for (int index = firstX; index <= lastX && index - firstX < 300; index++) {
            int x = stablePixel(screenX(index * step));
            graphics.vLine(x, top, bottom, 0xFF2C4151);
        }
        int firstZ = (int) Math.floor((centerZ - (mapCenterY() - top) / zoom) / step);
        int lastZ = (int) Math.ceil((centerZ + (bottom - mapCenterY()) / zoom) / step);
        for (int index = firstZ; index <= lastZ && index - firstZ < 300; index++) {
            int y = stablePixel(screenZ(index * step));
            graphics.hLine(left, right, y, 0xFF2C4151);
        }
    }

    private void renderTerrainContext(GuiGraphics graphics) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null
                || dimension == null || zoom < TERRAIN_MIN_ZOOM
                || !NamespacedId.parse(minecraft.level.dimension().location().toString()).equals(dimension)) {
            return;
        }
        int playerChunkX = ((int) Math.floor(minecraft.player.getX())) >> 4;
        int playerChunkZ = ((int) Math.floor(minecraft.player.getZ())) >> 4;
        int chunkRadius = minecraft.options.renderDistance().get() + 1;
        double loadedLeft = (playerChunkX - chunkRadius) * 16.0;
        double loadedRight = (playerChunkX + chunkRadius + 1) * 16.0;
        double loadedTop = (playerChunkZ - chunkRadius) * 16.0;
        double loadedBottom = (playerChunkZ + chunkRadius + 1) * 16.0;
        double worldLeft = Math.max(loadedLeft, centerX + (8 - mapCenterX()) / zoom);
        double worldRight = Math.min(loadedRight, centerX + (mapRight() - mapCenterX()) / zoom);
        double worldTop = Math.max(loadedTop, centerZ + (HEADER - mapCenterY()) / zoom);
        double worldBottom = Math.min(loadedBottom, centerZ + (mapBottom() - mapCenterY()) / zoom);
        if (worldLeft >= worldRight || worldTop >= worldBottom) {
            return;
        }
        int step = terrainSampleStep(worldRight - worldLeft, worldBottom - worldTop);
        long firstX = (long) Math.floor(worldLeft / step) * step;
        long lastX = (long) Math.ceil(worldRight / step) * step;
        long firstZ = (long) Math.floor(worldTop / step) * step;
        long lastZ = (long) Math.ceil(worldBottom / step) * step;
        int columns = (int) Math.min(Integer.MAX_VALUE, (lastX - firstX) / step + 1);
        int rows = (int) Math.min(Integer.MAX_VALUE, (lastZ - firstZ) / step + 1);
        if ((long) columns * rows > TERRAIN_MAX_VISIBLE_SAMPLES + columns + rows) {
            return;
        }

        terrainSampler.beginFrame(TERRAIN_NEW_SAMPLES_PER_FRAME);
        LoadedTerrainSampler.TerrainSample[][] samples =
                new LoadedTerrainSampler.TerrainSample[rows][columns];
        for (int row = 0; row < rows; row++) {
            long worldZ = firstZ + (long) row * step;
            for (int column = 0; column < columns; column++) {
                long worldX = firstX + (long) column * step;
                long sampleX = worldX + step / 2L;
                long sampleZ = worldZ + step / 2L;
                if (sampleX < Integer.MIN_VALUE || sampleX > Integer.MAX_VALUE
                        || sampleZ < Integer.MIN_VALUE || sampleZ > Integer.MAX_VALUE) {
                    continue;
                }
                Optional<LoadedTerrainSampler.TerrainSample> sampled = terrainSampler.sample(
                        minecraft.level, (int) sampleX, (int) sampleZ, ticks);
                if (sampled.isEmpty()) {
                    continue;
                }
                LoadedTerrainSampler.TerrainSample sample = sampled.orElseThrow();
                samples[row][column] = sample;
                int left = stablePixel(screenX(worldX));
                int right = stablePixel(screenX(worldX + step));
                int top = stablePixel(screenZ(worldZ));
                int bottom = stablePixel(screenZ(worldZ + step));
                graphics.fill(left, top, Math.max(left + 1, right), Math.max(top + 1, bottom),
                        terrainColor(sample));
            }
        }
        for (int row = 1; row < rows; row++) {
            double top = screenZ(firstZ + (row - 0.5) * step);
            double bottom = screenZ(firstZ + (row + 0.5) * step);
            for (int column = 1; column < columns; column++) {
                LoadedTerrainSampler.TerrainSample topLeft = samples[row - 1][column - 1];
                LoadedTerrainSampler.TerrainSample topRight = samples[row - 1][column];
                LoadedTerrainSampler.TerrainSample bottomRight = samples[row][column];
                LoadedTerrainSampler.TerrainSample bottomLeft = samples[row][column - 1];
                if (topLeft == null || topRight == null || bottomRight == null || bottomLeft == null) {
                    continue;
                }
                double left = screenX(firstX + (column - 0.5) * step);
                double right = screenX(firstX + (column + 0.5) * step);
                renderContourCell(graphics, left, top, right, bottom,
                        topLeft.height(), topRight.height(), bottomRight.height(), bottomLeft.height());
            }
        }
    }

    private static void renderContourCell(GuiGraphics graphics, double left, double top,
                                          double right, double bottom,
                                          int topLeft, int topRight, int bottomRight, int bottomLeft) {
        int minimum = Math.min(Math.min(topLeft, topRight), Math.min(bottomRight, bottomLeft));
        int maximum = Math.max(Math.max(topLeft, topRight), Math.max(bottomRight, bottomLeft));
        int level = Math.floorDiv(minimum, TERRAIN_CONTOUR_INTERVAL) * TERRAIN_CONTOUR_INTERVAL;
        if (level <= minimum) {
            level += TERRAIN_CONTOUR_INTERVAL;
        }
        for (; level <= maximum; level += TERRAIN_CONTOUR_INTERVAL) {
            List<ContourPoint> crossings = new ArrayList<>(4);
            addContourCrossing(crossings, left, top, topLeft, right, top, topRight, level);
            addContourCrossing(crossings, right, top, topRight, right, bottom, bottomRight, level);
            addContourCrossing(crossings, right, bottom, bottomRight, left, bottom, bottomLeft, level);
            addContourCrossing(crossings, left, bottom, bottomLeft, left, top, topLeft, level);
            int color = Math.floorMod(level, TERRAIN_MAJOR_CONTOUR_INTERVAL) == 0
                    ? 0xE0B6CBD7 : 0xC0809AA9;
            if (crossings.size() == 2) {
                drawThinLine(graphics, crossings.get(0), crossings.get(1), color);
            } else if (crossings.size() == 4) {
                drawThinLine(graphics, crossings.get(0), crossings.get(1), color);
                drawThinLine(graphics, crossings.get(2), crossings.get(3), color);
            }
        }
    }

    private static void addContourCrossing(List<ContourPoint> crossings,
                                           double firstX, double firstY, int firstHeight,
                                           double secondX, double secondY, int secondHeight, int level) {
        if ((firstHeight < level) == (secondHeight < level) || firstHeight == secondHeight) {
            return;
        }
        double progress = (level - firstHeight) / (double) (secondHeight - firstHeight);
        crossings.add(new ContourPoint(
                firstX + (secondX - firstX) * progress,
                firstY + (secondY - firstY) * progress));
    }

    private static void drawThinLine(GuiGraphics graphics, ContourPoint from, ContourPoint to, int color) {
        int steps = Math.max((int) Math.ceil(Math.abs(to.x() - from.x())),
                (int) Math.ceil(Math.abs(to.y() - from.y())));
        for (int index = 0; index <= steps; index++) {
            double progress = steps == 0 ? 0.0 : index / (double) steps;
            int x = stablePixel(from.x() + (to.x() - from.x()) * progress);
            int y = stablePixel(from.y() + (to.y() - from.y()) * progress);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private int terrainSampleStep(double worldWidth, double worldHeight) {
        int step = 1;
        while (step < 16) {
            long columns = (long) Math.ceil(worldWidth / step) + 2;
            long rows = (long) Math.ceil(worldHeight / step) + 2;
            if (columns * rows <= TERRAIN_MAX_VISIBLE_SAMPLES && step * zoom >= 3.0) {
                break;
            }
            step *= 2;
        }
        return step;
    }

    private static int terrainColor(LoadedTerrainSampler.TerrainSample sample) {
        return switch (sample.surface()) {
            case WATER -> 0xFF1E6387;
            case LAVA -> 0xFF9A3F1F;
            case LAND -> {
                int elevation = Math.max(0, Math.min(20, (sample.height() + 64) / 16));
                int red = 21 + elevation;
                int green = 31 + elevation * 2;
                int blue = 36 + elevation * 2;
                yield 0xFF000000 | red << 16 | green << 8 | blue;
            }
        };
    }

    private void renderMapCoordinates(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = 8;
        int right = mapRight();
        int top = HEADER;
        int bottom = mapBottom();
        double step = stableGridStep();
        int firstX = (int) Math.floor((centerX - (mapCenterX() - left) / zoom) / step);
        int lastX = (int) Math.ceil((centerX + (right - mapCenterX()) / zoom) / step);
        int lastXLabelRight = Integer.MIN_VALUE;
        for (int index = firstX; index <= lastX && index - firstX < 300; index++) {
            int x = stablePixel(screenX(index * step));
            String label = "X " + Math.round(index * step);
            int labelWidth = font.width(label);
            int labelX = Math.max(left + 3, Math.min(x - labelWidth / 2, right - labelWidth - 3));
            if (x >= left && x <= right && labelX > lastXLabelRight + 6) {
                drawMapLabel(graphics, label, labelX, top + 3);
                lastXLabelRight = labelX + labelWidth;
            }
        }
        int firstZ = (int) Math.floor((centerZ - (mapCenterY() - top) / zoom) / step);
        int lastZ = (int) Math.ceil((centerZ + (bottom - mapCenterY()) / zoom) / step);
        int lastZLabelBottom = top + font.lineHeight + 7;
        for (int index = firstZ; index <= lastZ && index - firstZ < 300; index++) {
            int y = stablePixel(screenZ(index * step));
            String label = "Z " + Math.round(index * step);
            int labelY = y - font.lineHeight / 2;
            if (y >= top && y <= bottom && labelY > lastZLabelBottom + 4
                    && labelY + font.lineHeight < bottom - font.lineHeight - 6) {
                drawMapLabel(graphics, label, left + 4, labelY);
                lastZLabelBottom = labelY + font.lineHeight;
            }
        }
        if (mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom) {
            long cursorX = (long) Math.floor(centerX + (mouseX - mapCenterX()) / zoom);
            long cursorZ = (long) Math.floor(centerZ + (mouseY - mapCenterY()) / zoom);
            drawMapLabel(graphics, "X " + cursorX + "  ·  Z " + cursorZ,
                    left + 4, bottom - font.lineHeight - 4);
        }
    }

    private void drawMapLabel(GuiGraphics graphics, String label, int x, int y) {
        int labelWidth = font.width(label);
        graphics.fill(x - 2, y - 2, x + labelWidth + 2, y + font.lineHeight + 1, 0xC018242E);
        graphics.drawString(font, label, x, y, 0xFFD7E3EC, false);
    }

    /**
     * Keeps grid LOD stable while zooming. The old hard cutovers (16/64/256/1024) could
     * switch an entire grid level at once near a threshold, which looked like the grid was
     * shaking. Hysteresis keeps the current level until it is clearly too dense or sparse.
     */
    private double stableGridStep() {
        while (gridStep * zoom < GRID_MIN_SCREEN_SPACING) {
            gridStep *= 2.0;
        }
        while (gridStep > MIN_GRID_STEP && gridStep * zoom > GRID_MAX_SCREEN_SPACING) {
            gridStep /= 2.0;
        }
        return gridStep;
    }

    private static int stablePixel(double value) {
        return (int) Math.floor(value + 0.5);
    }

    private void renderMapMarkers(GuiGraphics graphics) {
        List<Hit> markers = new ArrayList<>();
        List<ContainerOverview.RootRow> rows = new ArrayList<>(worldRoots());
        rows.sort(Comparator.comparing(row -> row.container().id()));
        for (ContainerOverview.RootRow row : rows) {
            WorldLocation location = (WorldLocation) row.container().location();
            double x = screenX(location.x());
            double y = screenZ(location.z());
            if (x < 4 || x > mapRight() + 4 || y < HEADER - 4 || y > mapBottom() + 4) {
                continue;
            }
            int markerX = stablePixel(x);
            int markerY = stablePixel(y);
            boolean active = row.container().id().equals(selected);
            boolean highlighted = highlightedRoots.contains(row.container().id());
            int color = active ? 0xFFFFD36B : highlighted ? 0xFF72E7B2 : 0xFF8EB7D9;

            int radius;
            if (zoom >= ICON_LOD_ZOOM) {
                radius = renderOverviewContainerVisual(graphics, row, markerX, markerY, color);
            } else {
                radius = 4;
                graphics.fill(markerX - radius, markerY - radius,
                        markerX + radius + 1, markerY + radius + 1, color);
            }
            markers.add(new Hit(markerX - radius - 3, markerY - radius - 3,
                    markerX + radius + 4, markerY + radius + 4, List.of(row)));
        }
        hits = List.copyOf(markers);
    }

    private int renderOverviewContainerVisual(GuiGraphics graphics, ContainerOverview.RootRow row,
                                              int x, int y, int outlineColor) {
        ContainerVisualResolver.ContainerBlockVisual visual = visualResolver.resolve(row);
        float iconSize = (float) Math.max(8.0, Math.min(14.0, zoom * 1.6));
        int radius = Math.max(4, Math.round(iconSize / 2));
        if (!visual.icon().isEmpty()) {
            float scale = iconSize / 16.0F;
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(x, y, 120);
            pose.scale(scale, scale, 1.0F);
            graphics.renderItem(visual.icon(), -8, -8);
            pose.popPose();
        } else {
            graphics.fill(x - radius + 1, y - radius + 1, x + radius, y + radius, 0xFF617789);
        }
        drawFrame(graphics, x - radius, y - radius, x + radius, y + radius, outlineColor);
        return radius;
    }

    private void renderRoute(GuiGraphics graphics) {
        if (navigationOverlay != null && !navigationOverlay.segments().isEmpty()) {
            renderNavigationOverlay(graphics);
            return;
        }
        if (orderedRouteStops.isEmpty()) {
            return;
        }
        WorldPosition from = liveRouteOrigin();
        int firstStop = 0;
        if (from == null) {
            from = WorldPosition.at(orderedRouteStops.getFirst());
            firstStop = 1;
        }
        for (int index = firstStop; index < orderedRouteStops.size(); index++) {
            WorldLocation to = orderedRouteStops.get(index);
            if (from.scope().equals(to.scope()) && from.dimension().equals(to.dimension())
                    && to.dimension().equals(dimension)) {
                drawDottedLine(graphics, (int) screenX(from.x()), (int) screenZ(from.z()),
                        (int) screenX(to.x()), (int) screenZ(to.z()), 0xFFEDC877, 7);
            }
            from = WorldPosition.at(to);
        }
    }

    private WorldPosition liveRouteOrigin() {
        if (routeOrigin == null || minecraft == null || minecraft.player == null || minecraft.level == null) {
            return routeOrigin;
        }
        var position = minecraft.player.position();
        return new WorldPosition(routeOrigin.scope(),
                NamespacedId.parse(minecraft.level.dimension().location().toString()),
                position.x, position.y, position.z);
    }

    private void renderPlayerPointer(GuiGraphics graphics) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null || dimension == null
                || !NamespacedId.parse(minecraft.level.dimension().location().toString()).equals(dimension)) {
            return;
        }
        TextureAtlasSprite sprite = minecraft.getMapDecorationTextures().get(PLAYER_MAP_DECORATION);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(screenX(minecraft.player.getX()), screenZ(minecraft.player.getZ()), 200);
        pose.mulPose(Axis.ZP.rotationDegrees(minecraft.player.getYRot()));
        graphics.blit(-6, -6, 0, 12, 12, sprite, 0.45F, 0.84F, 1.0F, 1.0F);
        pose.popPose();
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
        WorldLocation anchor = localAnchor;
        if (anchor == null) {
            ContainerOverview.RootRow focus = selectedRow();
            if (focus == null || !(focus.container().location() instanceof WorldLocation world)) {
                hits = List.of();
                return;
            }
            anchor = world;
            localAnchor = world;
            localCamera.reset();
        }

        Set<ContainerId> planRoots = new HashSet<>();
        routePlan.picks().forEach(pick -> planRoots.add(pick.source().rootContainer().id()));
        List<ContainerOverview.RootRow> rows = localRows(anchor);
        renderLocalScene(graphics, anchor, rows, planRoots);

        List<Hit> markers = new ArrayList<>();
        for (ContainerOverview.RootRow row : rows) {
            WorldLocation location = (WorldLocation) row.container().location();
            ProjectedBounds bounds = projectLocalBlock(location, anchor);
            if (bounds.right() < 8 || bounds.left() > mapRight()
                    || bounds.bottom() < HEADER || bounds.top() > mapBottom()) {
                continue;
            }
            markers.add(new Hit((int) Math.floor(bounds.left()) - 3,
                    (int) Math.floor(bounds.top()) - 3,
                    (int) Math.ceil(bounds.right()) + 4,
                    (int) Math.ceil(bounds.bottom()) + 4, List.of(row)));
        }
        hits = List.copyOf(markers);
        renderLocalNavigationOverlay(graphics, anchor);
        graphics.drawString(font, Component.translatable("gui.litemfinder.map.y_layer", layerY),
                15, HEADER + 10, 0xFFFFD36B);
    }

    private void renderLocalScene(GuiGraphics graphics, WorldLocation anchor,
                                  List<ContainerOverview.RootRow> rows,
                                  Set<ContainerId> planRoots) {
        if (minecraft == null) {
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(mapCenterX(), mapCenterY(), 300);
        float scale = localCamera.pixelsPerBlock();
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(localCamera.pitchDegrees()));
        pose.mulPose(Axis.YP.rotationDegrees(localCamera.yawDegrees()));
        pose.translate(-localCamera.focusX(), -localCamera.focusY(), -localCamera.focusZ());

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        RenderSystem.enableDepthTest();
        for (ContainerOverview.RootRow row : rows) {
            WorldLocation location = (WorldLocation) row.container().location();
            ContainerVisualResolver.ContainerBlockVisual visual = visualResolver.resolve(row);
            pose.pushPose();
            pose.translate(location.x() - anchor.x(), location.y() - anchor.y(), location.z() - anchor.z());
            try {
                minecraft.getBlockRenderer().renderSingleBlock(visual.state(), pose, bufferSource,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            } catch (RuntimeException renderFailure) {
                LOGGER.debug("Could not render container block {} on map", visual.block(), renderFailure);
            }
            pose.popPose();
        }

        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        for (ContainerOverview.RootRow row : rows) {
            WorldLocation location = (WorldLocation) row.container().location();
            int color = localOutlineColor(row, planRoots);
            pose.pushPose();
            pose.translate(location.x() - anchor.x(), location.y() - anchor.y(), location.z() - anchor.z());
            LevelRenderer.renderLineBox(pose, lines,
                    -LOCAL_OUTLINE_EXPANSION, -LOCAL_OUTLINE_EXPANSION, -LOCAL_OUTLINE_EXPANSION,
                    1.0 + LOCAL_OUTLINE_EXPANSION, 1.0 + LOCAL_OUTLINE_EXPANSION,
                    1.0 + LOCAL_OUTLINE_EXPANSION,
                    colorChannel(color, 16), colorChannel(color, 8), colorChannel(color, 0),
                    colorChannel(color, 24));
            pose.popPose();
        }
        bufferSource.endBatch();
        RenderSystem.disableDepthTest();
        pose.popPose();
    }

    private int localOutlineColor(ContainerOverview.RootRow row, Set<ContainerId> planRoots) {
        return row.container().id().equals(selected) ? 0xFFFFD36B
                : highlightedRoots.contains(row.container().id()) ? 0xFF72E7B2
                : planRoots.contains(row.container().id()) ? 0xFF74CDEB : 0xB08EB7D9;
    }

    private ProjectedBounds projectLocalBlock(WorldLocation location, WorldLocation anchor) {
        double left = Double.POSITIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            ContainerMap3DCamera.ProjectedPoint point = projectLocal(
                    location.x() + (corner & 1),
                    location.y() + ((corner >> 1) & 1),
                    location.z() + ((corner >> 2) & 1), anchor);
            left = Math.min(left, point.x());
            top = Math.min(top, point.y());
            right = Math.max(right, point.x());
            bottom = Math.max(bottom, point.y());
        }
        return new ProjectedBounds(left, top, right, bottom);
    }

    private static float colorChannel(int color, int shift) {
        return ((color >>> shift) & 0xFF) / 255.0F;
    }

    private List<ContainerOverview.RootRow> localRows(WorldLocation anchor) {
        return worldRoots().stream()
                .filter(row -> {
                    WorldLocation location = (WorldLocation) row.container().location();
                    return Math.abs(location.x() - anchor.x()) <= LOCAL_RADIUS
                            && Math.abs(location.z() - anchor.z()) <= LOCAL_RADIUS
                            && Math.abs(location.y() - layerY) <= LOCAL_Y_RADIUS;
                })
                .toList();
    }

    private ContainerMap3DCamera.ProjectedPoint projectLocal(double worldX, double worldY, double worldZ,
                                                              WorldLocation anchor) {
        return localCamera.project(worldX - anchor.x(), worldY - anchor.y(), worldZ - anchor.z(),
                mapCenterX(), mapCenterY());
    }

    private void renderLocalNavigationOverlay(GuiGraphics graphics, WorldLocation anchor) {
        if (navigationOverlay == null || lastNavigationOrigin == null) {
            return;
        }
        for (NavigationGateway.Segment segment : navigationOverlay.segments()) {
            for (int index = 1; index < segment.points().size(); index++) {
                WorldPosition from = segment.points().get(index - 1);
                WorldPosition to = segment.points().get(index);
                if (!from.scope().equals(anchor.scope()) || !to.scope().equals(anchor.scope())
                        || !from.dimension().equals(anchor.dimension())
                        || !to.dimension().equals(anchor.dimension())
                        || !withinLocalView(from, anchor) || !withinLocalView(to, anchor)) {
                    continue;
                }
                ContainerMap3DCamera.ProjectedPoint fromPoint = projectLocal(from.x(), from.y(), from.z(), anchor);
                ContainerMap3DCamera.ProjectedPoint toPoint = projectLocal(to.x(), to.y(), to.z(), anchor);
                drawDottedLine(graphics,
                        (int) Math.round(fromPoint.x()), (int) Math.round(fromPoint.y()),
                        (int) Math.round(toPoint.x()), (int) Math.round(toPoint.y()),
                        routeColor(segment.mode()), 2);
            }
        }
    }

    private boolean withinLocalView(WorldPosition point, WorldLocation anchor) {
        return Math.abs(point.x() - anchor.x()) <= LOCAL_RADIUS
                && Math.abs(point.z() - anchor.z()) <= LOCAL_RADIUS
                && Math.abs(point.y() - layerY) <= LOCAL_Y_RADIUS;
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
            y = panelLine(graphics, SnapshotTimeFormatter.format(row.observedAt()), left, y, 0xFFB8C6D2);
            contentsButton.visible = true;
            contentsButton.setX(left);
            contentsButton.setY(y + 1);
            y += 25;
        } else {
            y = panelLine(graphics, Component.translatable("gui.litemfinder.map.select").getString(),
                    left, y, 0xFFB8C6D2);
            contentsButton.visible = false;
        }
        int logicalY = Math.max(y + 10, HEADER + 121);
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
        if (mouseY < HEADER || mouseY >= mapBottom()) {
            return false;
        }
        if (button == 0) {
            for (Hit hit : logicalHits) {
                if (hit.contains(mouseX, mouseY)) {
                    selected = hit.rows().getFirst().container().id();
                    return true;
                }
            }
        }
        if (mouseX >= mapRight()) {
            return false;
        }

        if (localView) {
            if (button == 0 || button == 1) {
                localDragButton = button;
                localPressX = mouseX;
                localPressY = mouseY;
                localDragDistance = 0;
                return true;
            }
            return false;
        }

        if (button != 0) {
            return false;
        }
        List<Hit> clicked = hits.stream().filter(hit -> hit.contains(mouseX, mouseY)).toList();
        if (!clicked.isEmpty()) {
            int current = -1;
            for (int index = 0; index < clicked.size(); index++) {
                if (clicked.get(index).rows().getFirst().container().id().equals(selected)) {
                    current = index;
                    break;
                }
            }
            ContainerOverview.RootRow target = clicked.get((current + 1) % clicked.size()).rows().getFirst();
            selected = target.container().id();
            if (target.container().location() instanceof WorldLocation world) {
                layerY = world.y();
            }
            return true;
        }
        panning = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (localView && button == localDragButton) {
            localDragDistance = Math.max(localDragDistance,
                    Math.hypot(mouseX - localPressX, mouseY - localPressY));
            if (button == 0) {
                localCamera.pan(dragX, dragY);
            } else if (button == 1) {
                localCamera.rotate(dragX, dragY);
            }
            return true;
        }
        if (!localView && button == 0 && panning) {
            centerX -= dragX / zoom;
            centerZ -= dragY / zoom;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (localView && button == localDragButton) {
            if (button == 0 && localDragDistance <= CLICK_DRAG_THRESHOLD) {
                selectLocalHit(mouseX, mouseY);
            }
            localDragButton = -1;
            localDragDistance = 0;
            return true;
        }
        panning = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void selectLocalHit(double mouseX, double mouseY) {
        Hit best = hits.stream()
                .filter(hit -> hit.contains(mouseX, mouseY))
                .min(Comparator.comparingDouble(hit -> hit.distanceSquared(mouseX, mouseY)))
                .orElse(null);
        if (best != null && !best.rows().isEmpty()) {
            selected = best.rows().getFirst().container().id();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseX < mapRight() && mouseY >= HEADER && mouseY < mapBottom() && deltaY != 0) {
            if (localView) {
                localCamera.zoom(deltaY);
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
        terrainSampler.clear();
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
            localAnchor = null;
            localDragButton = -1;
        }
        if (!initializedView) {
            fitView();
            initializedView = true;
        }
        refreshHighlights();
        updateButtons();
    }

    private void refreshNavigation() {
        List<WorldLocation> sourceStops = sourceStops();
        routeOrigin = playerOrigin(sourceStops);
        orderedRouteStops = routeOrigin == null
                ? sourceStops : ContainerRouteOptimizer.order(routeOrigin, sourceStops);

        if (navigation.isPresent() && routeOrigin != null && !orderedRouteStops.isEmpty()) {
            WorldPosition origin = routeOrigin;
            List<WorldLocation> stops = orderedRouteStops;
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
        navigationRequestId++;
        navigationPending = false;
        navigationOverlay = null;
        lastNavigationStops = List.of();
        lastNavigationOrigin = null;
    }

    private List<WorldLocation> sourceStops() {
        Set<ContainerId> seen = new HashSet<>();
        return routePlan.picks().stream()
                .filter(pick -> seen.add(pick.source().rootContainer().id()))
                .map(pick -> pick.source().rootContainer().location())
                .filter(WorldLocation.class::isInstance)
                .map(WorldLocation.class::cast)
                .toList();
    }

    private WorldPosition playerOrigin(List<WorldLocation> stops) {
        if (stops.isEmpty() || minecraft == null || minecraft.player == null || minecraft.level == null) {
            return null;
        }
        var player = minecraft.player.position();
        return new WorldPosition(stops.getFirst().scope(),
                NamespacedId.parse(minecraft.level.dimension().location().toString()),
                Math.floor(player.x), Math.floor(player.y), Math.floor(player.z));
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
        localAnchor = null;
        localDragButton = -1;
        fitView();
        updateButtons();
    }

    private void toggleMode() {
        if (!localView) {
            ContainerOverview.RootRow row = localEntryRow();
            if (row == null || !(row.container().location() instanceof WorldLocation world)) {
                return;
            }
            // Entering 3D must not require an earlier marker selection. Anchor the local scene
            // to the selected container when available, otherwise to the container nearest the
            // current 2D viewport centre. Selecting the anchor also makes the details panel
            // immediately useful and gives G3.2 a deterministic click/preview target.
            selected = row.container().id();
            layerY = world.y();
            localAnchor = world;
            localCamera.reset();
            localDragButton = -1;
            localDragDistance = 0;
        } else {
            localDragButton = -1;
            localDragDistance = 0;
        }
        localView = !localView;
        updateButtons();
    }

    private void openSelectedContents() {
        ContainerOverview.RootRow row = selectedRow();
        if (row != null && minecraft != null) {
            minecraft.setScreen(new ContainerContentsScreen(this, api, row.container().id()));
        }
    }

    private ContainerOverview.RootRow localEntryRow() {
        ContainerOverview.RootRow current = selectedRow();
        if (current != null && current.container().location() instanceof WorldLocation world
                && world.dimension().equals(dimension)) {
            return current;
        }
        return worldRoots().stream()
                .min(Comparator.comparingDouble(row -> {
                    WorldLocation world = (WorldLocation) row.container().location();
                    double dx = world.x() - centerX;
                    double dz = world.z() - centerZ;
                    return dx * dx + dz * dz;
                }))
                .orElse(null);
    }

    private void updateButtons() {
        if (dimensionButton == null || modeButton == null) {
            return;
        }
        dimensionButton.setMessage(dimension == null ? Component.translatable("gui.litemfinder.map.no_dimension")
                : Component.literal(dimension.path()));
        modeButton.setMessage(Component.literal(localView ? "2D" : "3D"));
        modeButton.active = localView || !worldRoots().isEmpty();
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

    private static void drawFrame(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        graphics.hLine(left, right, top, color);
        graphics.hLine(left, right, bottom, color);
        graphics.vLine(left, top, bottom, color);
        graphics.vLine(right, top, bottom, color);
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

        private double distanceSquared(double x, double y) {
            double centerX = (left + right) * 0.5;
            double centerY = (top + bottom) * 0.5;
            double dx = x - centerX;
            double dy = y - centerY;
            return dx * dx + dy * dy;
        }
    }

    private record ProjectedBounds(double left, double top, double right, double bottom) {
    }

    private record ContourPoint(double x, double y) {
    }
}
