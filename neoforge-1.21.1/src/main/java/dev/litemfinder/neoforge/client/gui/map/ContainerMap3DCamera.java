package dev.litemfinder.neoforge.client.gui.map;

/** Orthographic orbit camera used by the Ponder-style local container scene. */
public final class ContainerMap3DCamera {

    private static final double MIN_PITCH = Math.toRadians(10);
    private static final double MAX_PITCH = Math.toRadians(80);
    private static final double MIN_SCALE = 2.0;
    private static final double MAX_SCALE = 40.0;

    private double focusX = 0.5;
    private double focusY = 0.5;
    private double focusZ = 0.5;
    private double yaw = Math.toRadians(45);
    private double pitch = Math.toRadians(30);
    private double pixelsPerBlock = 8.0;

    public void reset() {
        focusX = 0.5;
        focusY = 0.5;
        focusZ = 0.5;
        yaw = Math.toRadians(45);
        pitch = Math.toRadians(30);
        pixelsPerBlock = 8.0;
    }

    /** Pans the point of interest in the current camera plane. */
    public void pan(double dragX, double dragY) {
        double horizontal = -dragX / pixelsPerBlock;
        double vertical = dragY / pixelsPerBlock;
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double sinPitch = Math.sin(pitch);
        double cosPitch = Math.cos(pitch);

        // Inverse-rotated camera right and up vectors.
        focusX += horizontal * cosYaw + vertical * sinYaw * sinPitch;
        focusY += vertical * cosPitch;
        focusZ += horizontal * sinYaw - vertical * cosYaw * sinPitch;
    }

    /** Right-drag orbit control. */
    public void rotate(double dragX, double dragY) {
        yaw += Math.toRadians(dragX * 0.45);
        pitch = clamp(pitch + Math.toRadians(dragY * 0.35), MIN_PITCH, MAX_PITCH);
    }

    public void zoom(double wheelDelta) {
        pixelsPerBlock = clamp(pixelsPerBlock * Math.pow(1.2, wheelDelta), MIN_SCALE, MAX_SCALE);
    }

    /** Projects a local-world point into GUI coordinates using the same orbit transform as the renderer. */
    public ProjectedPoint project(double x, double y, double z, double centerX, double centerY) {
        double dx = x - focusX;
        double dy = y - focusY;
        double dz = z - focusZ;

        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double yawX = cosYaw * dx + sinYaw * dz;
        double yawZ = -sinYaw * dx + cosYaw * dz;

        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        double pitchY = cosPitch * dy - sinPitch * yawZ;
        double depth = sinPitch * dy + cosPitch * yawZ;

        return new ProjectedPoint(
                centerX + yawX * pixelsPerBlock,
                centerY - pitchY * pixelsPerBlock,
                depth
        );
    }

    public double focusX() {
        return focusX;
    }

    public double focusY() {
        return focusY;
    }

    public double focusZ() {
        return focusZ;
    }

    public float yawDegrees() {
        return (float) Math.toDegrees(yaw);
    }

    public float pitchDegrees() {
        return (float) Math.toDegrees(pitch);
    }

    public float pixelsPerBlock() {
        return (float) pixelsPerBlock;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record ProjectedPoint(double x, double y, double depth) {
    }
}
