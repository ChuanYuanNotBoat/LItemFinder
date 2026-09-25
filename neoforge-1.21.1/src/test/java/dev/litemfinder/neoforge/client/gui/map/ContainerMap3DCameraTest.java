package dev.litemfinder.neoforge.client.gui.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerMap3DCameraTest {

    @Test
    void resetCentersSelectedBlockAndUsesPonderLikeAngle() {
        ContainerMap3DCamera camera = new ContainerMap3DCamera();
        camera.pan(50, -30);
        camera.rotate(30, 30);
        camera.zoom(3);

        camera.reset();

        assertEquals(0.5, camera.focusX(), 1e-9);
        assertEquals(0.5, camera.focusY(), 1e-9);
        assertEquals(0.5, camera.focusZ(), 1e-9);
        assertEquals(45.0, camera.yawDegrees(), 1e-4);
        assertEquals(30.0, camera.pitchDegrees(), 1e-4);
        assertEquals(8.0, camera.pixelsPerBlock(), 1e-4);
    }

    @Test
    void panMovesPointOfInterestAndRotationKeepsPitchBounded() {
        ContainerMap3DCamera camera = new ContainerMap3DCamera();
        camera.pan(24, 16);
        assertTrue(Math.abs(camera.focusX() - 0.5) > 0.1);
        assertTrue(Math.abs(camera.focusY() - 0.5) > 0.1);

        camera.rotate(0, 10000);
        assertTrue(camera.pitchDegrees() <= 80.001);
        camera.rotate(0, -20000);
        assertTrue(camera.pitchDegrees() >= 9.999);
    }

    @Test
    void zoomIsClamped() {
        ContainerMap3DCamera camera = new ContainerMap3DCamera();
        camera.zoom(100);
        assertEquals(40.0, camera.pixelsPerBlock(), 1e-4);
        camera.zoom(-200);
        assertEquals(2.0, camera.pixelsPerBlock(), 1e-4);
    }

    @Test
    void focusProjectsToViewportCenter() {
        ContainerMap3DCamera camera = new ContainerMap3DCamera();
        var point = camera.project(0.5, 0.5, 0.5, 300, 200);
        assertEquals(300, point.x(), 1e-9);
        assertEquals(200, point.y(), 1e-9);
    }
}
