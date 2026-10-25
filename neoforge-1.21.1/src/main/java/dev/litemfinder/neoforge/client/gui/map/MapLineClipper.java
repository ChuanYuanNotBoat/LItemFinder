package dev.litemfinder.neoforge.client.gui.map;

import java.util.Optional;

/** Clips route segments before creating draw calls; scissoring alone still draws off-screen dots. */
public final class MapLineClipper {

    private MapLineClipper() {
    }

    public static Optional<Line> clip(double x1, double y1, double x2, double y2,
                                      double left, double top, double right, double bottom) {
        if (!Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(x2) || !Double.isFinite(y2)
                || left > right || top > bottom) {
            return Optional.empty();
        }
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) {
            return Optional.empty();
        }
        double start = 0;
        double end = 1;
        for (int edge = 0; edge < 4; edge++) {
            double p = switch (edge) {
                case 0 -> -dx;
                case 1 -> dx;
                case 2 -> -dy;
                default -> dy;
            };
            double q = switch (edge) {
                case 0 -> x1 - left;
                case 1 -> right - x1;
                case 2 -> y1 - top;
                default -> bottom - y1;
            };
            if (p == 0) {
                if (q < 0) {
                    return Optional.empty();
                }
            } else if (p < 0) {
                start = Math.max(start, q / p);
            } else {
                end = Math.min(end, q / p);
            }
            if (start > end) {
                return Optional.empty();
            }
        }
        return Optional.of(new Line(x1 + start * dx, y1 + start * dy, x1 + end * dx, y1 + end * dy));
    }

    public record Line(double x1, double y1, double x2, double y2) {
    }
}
