package dev.litemfinder.neoforge.client.gui.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MapLineClipperTest {

    @Test
    void longRouteCrossingViewportIsClippedBeforeDotGeneration() {
        var line = MapLineClipper.clip(-30_000_000, 100, 30_000_000, 100,
                8, 48, 800, 600).orElseThrow();

        assertEquals(8, line.x1(), 0.000001);
        assertEquals(800, line.x2(), 0.000001);
        assertEquals(100, line.y1());
        assertEquals(100, line.y2());
        assertTrue(Math.abs(line.x2() - line.x1()) <= 800);
    }

    @Test
    void entirelyOffscreenSegmentsProduceNoDrawWork() {
        assertTrue(MapLineClipper.clip(-1000, 100, -10, 500, 8, 48, 800, 600).isEmpty());
        assertTrue(MapLineClipper.clip(10, 0, 900, 47, 8, 48, 800, 600).isEmpty());
        assertTrue(MapLineClipper.clip(20, 700, 20, 900, 8, 48, 800, 600).isEmpty());
    }

    @Test
    void diagonalClipPreservesDirectionAndViewportIntersections() {
        var line = MapLineClipper.clip(1000, 1000, -1000, -1000,
                8, 48, 800, 600).orElseThrow();

        assertEquals(new MapLineClipper.Line(600, 600, 48, 48), line);
    }

    @Test
    void visibleSegmentsAndBoundaryPointsRemainValid() {
        assertEquals(new MapLineClipper.Line(10, 50, 500, 500),
                MapLineClipper.clip(10, 50, 500, 500, 8, 48, 800, 600).orElseThrow());
        assertEquals(new MapLineClipper.Line(8, 48, 8, 48),
                MapLineClipper.clip(8, 48, 8, 48, 8, 48, 800, 600).orElseThrow());
        assertTrue(MapLineClipper.clip(Double.NaN, 0, 1, 1, 8, 48, 800, 600).isEmpty());
    }
}
