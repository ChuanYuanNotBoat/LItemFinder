package dev.litemfinder.neoforge.client.view;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** B is a bucket (1000 mB); kB/MB/GB denote thousands/millions/billions of buckets. */
public final class FluidQuantityFormat {
    private FluidQuantityFormat() { }
    public static String display(long millibuckets) {
        if (millibuckets < 0) throw new IllegalArgumentException("negative fluid amount");
        String[] units = {"mB", "B", "kB", "MB", "GB", "TB", "PB"};
        long divisor = 1;
        int unit = 0;
        while (unit + 1 < units.length && millibuckets / divisor >= 1000) {
            divisor = Math.multiplyExact(divisor, 1000);
            unit++;
        }
        return BigDecimal.valueOf(millibuckets).divide(BigDecimal.valueOf(divisor), 2, RoundingMode.DOWN)
                .stripTrailingZeros().toPlainString() + " " + units[unit];
    }
}
