package com.mastercard.system.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {}

    public static BigDecimal round(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** base * rate / 100, redondeado a 2 decimales. */
    public static BigDecimal percent(BigDecimal base, BigDecimal rate) {
        return round(base.multiply(rate).divide(HUNDRED, 6, RoundingMode.HALF_UP));
    }
}
