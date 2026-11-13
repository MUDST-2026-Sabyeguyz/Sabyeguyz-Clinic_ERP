package com.sabyeguyz.clinicv2.sale;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DiscountTest {
    private static final BigDecimal PRICE = new BigDecimal("2000.00");

    @Test
    void confirmedBahtDiscountReducesPriceByFiveHundred() {
        Discount discount = Discount.of("BAHT", new BigDecimal("500.00"));
        assertEquals(new BigDecimal("1500.00"), discount.apply(PRICE));
        assertEquals(new BigDecimal("500.00"), discount.amountOff(PRICE));
    }

    @Test
    void fullPriceDiscountDoesNotCreateNegativeBalance() {
        assertEquals(new BigDecimal("0.00"), Discount.of("FIXED", PRICE).apply(PRICE));
        assertThrows(IllegalArgumentException.class, () -> Discount.of("BAHT", new BigDecimal("2000.01")).apply(PRICE));
    }

    @Test
    void rejectsNegativeAndIncompleteDiscounts() {
        assertThrows(IllegalArgumentException.class, () -> Discount.of("BAHT", new BigDecimal("-0.01")).apply(PRICE));
        assertThrows(IllegalArgumentException.class, () -> Discount.of(null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> Discount.of("BAHT", null));
        assertThrows(IllegalArgumentException.class, () -> Discount.of("UNKNOWN", BigDecimal.ONE));
    }

    @Test
    void absentDiscountPreservesTheWholePrice() {
        Discount none = Discount.of(" ", BigDecimal.ZERO);
        assertTrue(none.isNone());
        assertEquals(PRICE, none.apply(PRICE));
        assertEquals(new BigDecimal("0.00"), none.amountOff(PRICE));
    }
}
