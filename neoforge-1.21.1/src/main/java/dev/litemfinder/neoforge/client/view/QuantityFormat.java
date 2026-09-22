package dev.litemfinder.neoforge.client.view;

import java.math.BigInteger;
import java.util.Objects;

/** Integer-only quantity formatting and bounded editing rules. */
public final class QuantityFormat {

    private QuantityFormat() {
    }

    public enum Mode {
        NUMBER,
        BOX_STACK_ITEM,
        STACK_AND_REMAINDER
    }

    public static String display(long count, int stackSize, Mode mode) {
        return display(count, stackSize, mode, "盒", "组", "个");
    }

    public static String display(long count, int stackSize, Mode mode,
                                 String boxLabel, String stackLabel, String itemLabel) {
        if (count < 0 || stackSize <= 0) {
            throw new IllegalArgumentException("count and stackSize must be non-negative/positive");
        }
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(boxLabel, "boxLabel must not be null");
        Objects.requireNonNull(stackLabel, "stackLabel must not be null");
        Objects.requireNonNull(itemLabel, "itemLabel must not be null");
        return switch (mode) {
            case NUMBER -> Long.toString(count);
            case BOX_STACK_ITEM -> {
                long boxSize = Math.multiplyExact(27L, stackSize);
                long boxes = count / boxSize;
                long remainder = count % boxSize;
                yield boxes + " " + boxLabel + " + " + (remainder / stackSize) + " " + stackLabel
                        + " + " + (remainder % stackSize) + " " + itemLabel;
            }
            case STACK_AND_REMAINDER -> count / stackSize + " " + stackLabel + " + "
                    + count % stackSize + " " + itemLabel;
        };
    }

    public static long adjust(long current, long delta, long upperBound) {
        if (upperBound < 0 || current < 0 || current > upperBound) {
            throw new IllegalArgumentException("current must lie within 0..upperBound");
        }
        if (delta > 0) {
            return delta >= upperBound - current ? upperBound : current + delta;
        }
        if (delta < 0) {
            if (delta == Long.MIN_VALUE || -delta >= current) {
                return 0;
            }
            return current + delta;
        }
        return current;
    }

    public static long parseClamped(String input, long upperBound) {
        Objects.requireNonNull(input, "input must not be null");
        if (upperBound < 0 || input.isEmpty() || !input.chars().allMatch(
                character -> character >= '0' && character <= '9')) {
            throw new IllegalArgumentException("quantity must be a non-negative decimal integer");
        }
        BigInteger parsed = new BigInteger(input);
        return parsed.compareTo(BigInteger.valueOf(upperBound)) >= 0
                ? upperBound : parsed.longValueExact();
    }

    public static long unitStep(int stackSize, Unit unit) {
        if (stackSize <= 0) {
            throw new IllegalArgumentException("stackSize must be positive");
        }
        Objects.requireNonNull(unit, "unit must not be null");
        return switch (unit) {
            case BOX -> Math.multiplyExact(27L, stackSize);
            case STACK -> stackSize;
            case ITEM -> 1;
        };
    }

    public enum Unit {
        BOX,
        STACK,
        ITEM
    }
}
