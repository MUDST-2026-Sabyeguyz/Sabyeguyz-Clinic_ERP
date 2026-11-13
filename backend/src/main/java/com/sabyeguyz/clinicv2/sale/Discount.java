package com.sabyeguyz.clinicv2.sale;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Line discount on a sale item, as a percentage or a baht amount. The result
 * of {@link #apply} is the commissionable price: a line discount reduces the
 * commission base, so a 2,000 THB item with a 500 THB discount earns commission
 * on 1,500 THB (feedback PDF, "สรุปรายการ").
 */
public record Discount(
        DiscountType type,
        BigDecimal value) {
    private static final Discount NONE = new Discount(null, null);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static Discount none() {
        return NONE;
    }

    /** Parses request fields; a blank type with no value means no discount. */
    public static Discount of(
            String type,
            BigDecimal value) {
        if (type == null || type.isBlank()) {
            if (value != null && value.signum() != 0) {
                throw new IllegalArgumentException("ต้องระบุประเภทส่วนลด (เปอร์เซ็นต์ หรือ บาท)");
            }

            return NONE;
        }

        if (value == null) {
            throw new IllegalArgumentException("ต้องระบุมูลค่าส่วนลด");
        }

        DiscountType parsed = switch (type.trim().toUpperCase()) {
            case "PERCENT", "PERCENTAGE" -> DiscountType.Percentage;
            case "FIXED", "AMOUNT", "BAHT" -> DiscountType.Fixed;
            default -> throw new IllegalArgumentException("ประเภทส่วนลดไม่ถูกต้อง: " + type);
        };

        return new Discount(parsed, value);
    }

    public boolean isNone() {
        return type == null;
    }

    /** The money taken off {@code base}; zero when there is no discount. */
    public BigDecimal amountOff(BigDecimal base) {
        return type == null
                ? BigDecimal.ZERO.setScale(2)
                : base.subtract(apply(base)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal apply(BigDecimal price) {
        if (type == null) {
            return price;
        }

        if (value.signum() < 0) {
            throw new IllegalArgumentException("ส่วนลดต้องไม่ติดลบ");
        }

        return switch (type) {
            case Percentage -> {
                if (value.compareTo(HUNDRED) > 0) {
                    throw new IllegalArgumentException("ส่วนลดเปอร์เซ็นต์ต้องไม่เกิน 100");
                }

                yield price.subtract(price.multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP));
            }

            case Fixed -> {
                if (value.stripTrailingZeros().scale() > 2) {
                    throw new IllegalArgumentException("ส่วนลดบาทต้องไม่ละเอียดเกิน 2 ตำแหน่ง");
                }

                if (value.compareTo(price) > 0) {
                    throw new IllegalArgumentException("ส่วนลดต้องไม่เกินราคา");
                }

                yield price.subtract(value);
            }
        };
    }
}
