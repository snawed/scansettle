package com.scansettle.api.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Decimal-safe money: minor units (pence) + currency, never a floating point type.
 * MVP is GBP-only (Section 22 of the product brief) but currency is carried
 * explicitly rather than assumed, so a second currency is a data change, not a
 * type change.
 */
public final class Money {

    public static final Currency GBP = Currency.getInstance("GBP");

    private final long minorUnits;
    private final Currency currency;

    private Money(long minorUnits, Currency currency) {
        this.minorUnits = minorUnits;
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    public static Money ofMinorUnits(long minorUnits, Currency currency) {
        return new Money(minorUnits, currency);
    }

    public static Money ofGbp(BigDecimal pounds) {
        if (pounds == null) {
            throw new IllegalArgumentException("pounds must not be null");
        }
        if (pounds.scale() > 2) {
            throw new IllegalArgumentException("GBP amounts cannot have more than 2 decimal places: " + pounds);
        }
        long pence = pounds.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        return new Money(pence, GBP);
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    public long minorUnits() {
        return minorUnits;
    }

    public Currency currency() {
        return currency;
    }

    public BigDecimal toDecimal() {
        return BigDecimal.valueOf(minorUnits).movePointLeft(2);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(this.minorUnits, other.minorUnits), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(this.minorUnits, other.minorUnits), currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return this.minorUnits > other.minorUnits;
    }

    public boolean isNegative() {
        return minorUnits < 0;
    }

    public boolean isZero() {
        return minorUnits == 0;
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot combine amounts in different currencies: " + this.currency + " vs " + other.currency);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return minorUnits == money.minorUnits && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minorUnits, currency);
    }

    @Override
    public String toString() {
        return currency.getSymbol() + toDecimal().toPlainString();
    }
}
