package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;

import com.DatShop.model.Voucher;
import com.DatShop.model.VoucherType;
import com.DatShop.service.VoucherCalculator;
import com.DatShop.service.VoucherEvaluation;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VoucherCalculatorTest {

    private static final Instant START = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-06-30T23:59:59Z");
    private static final Instant MID = Instant.parse("2026-06-15T12:00:00Z");

    private final VoucherCalculator calculator = new VoucherCalculator();

    @Test
    void percentDiscountAndCap() {
        Voucher tenPercent = percent(new BigDecimal("10.00"), null);
        VoucherEvaluation plain = calculator.evaluate(tenPercent, money("1000000.00"), MID);
        assertThat(plain.valid()).isTrue();
        assertThat(plain.discountAmount()).isEqualByComparingTo("100000.00");

        Voucher capped = percent(new BigDecimal("10.00"), money("150000.00"));
        VoucherEvaluation cappedResult = calculator.evaluate(capped, money("2000000.00"), MID);
        assertThat(cappedResult.valid()).isTrue();
        assertThat(cappedResult.discountAmount()).isEqualByComparingTo("150000.00");
    }

    @Test
    void fixedDiscountNeverExceedsSubtotal() {
        Voucher fixed = fixed(money("100000.00"));
        VoucherEvaluation normal = calculator.evaluate(fixed, money("1000000.00"), MID);
        assertThat(normal.valid()).isTrue();
        assertThat(normal.discountAmount()).isEqualByComparingTo("100000.00");

        VoucherEvaluation capped = calculator.evaluate(fixed, money("50000.00"), MID);
        assertThat(capped.valid()).isTrue();
        assertThat(capped.discountAmount()).isEqualByComparingTo("50000.00");
    }

    @Test
    void minOrderIsInclusive() {
        Voucher voucher = percent(new BigDecimal("10.00"), null);
        voucher.setMinOrderAmount(money("500000.00"));
        assertThat(calculator.evaluate(voucher, money("499999.00"), MID).valid()).isFalse();
        assertThat(calculator.evaluate(voucher, money("500000.00"), MID).valid()).isTrue();
    }

    @Test
    void timeWindowIsInclusive() {
        Voucher voucher = percent(new BigDecimal("10.00"), null);
        assertThat(calculator.evaluate(voucher, money("1000000.00"), START.minusSeconds(1)).valid()).isFalse();
        assertThat(calculator.evaluate(voucher, money("1000000.00"), START).valid()).isTrue();
        assertThat(calculator.evaluate(voucher, money("1000000.00"), END).valid()).isTrue();
        assertThat(calculator.evaluate(voucher, money("1000000.00"), END.plusSeconds(1)).valid()).isFalse();
    }

    @Test
    void activeAndUsageLimit() {
        Voucher voucher = percent(new BigDecimal("10.00"), null);
        voucher.setActive(false);
        assertThat(calculator.evaluate(voucher, money("1000000.00"), MID).valid()).isFalse();

        voucher.setActive(true);
        voucher.setUsageLimit(2);
        voucher.setUsedCount(2);
        assertThat(calculator.evaluate(voucher, money("1000000.00"), MID).valid()).isFalse();

        voucher.setUsedCount(1);
        assertThat(calculator.evaluate(voucher, money("1000000.00"), MID).valid()).isTrue();

        voucher.setUsageLimit(null);
        voucher.setUsedCount(99);
        assertThat(calculator.evaluate(voucher, money("1000000.00"), MID).valid()).isTrue();
    }

    private static Voucher percent(BigDecimal value, BigDecimal max) {
        Voucher voucher = base();
        voucher.setType(VoucherType.PERCENT);
        voucher.setDiscountValue(value);
        voucher.setMaxDiscountAmount(max);
        return voucher;
    }

    private static Voucher fixed(BigDecimal value) {
        Voucher voucher = base();
        voucher.setType(VoucherType.FIXED);
        voucher.setDiscountValue(value);
        return voucher;
    }

    private static Voucher base() {
        Voucher voucher = new Voucher();
        voucher.setCode("TEST");
        voucher.setActive(true);
        voucher.setUsedCount(0);
        voucher.setStartAt(START);
        voucher.setEndAt(END);
        return voucher;
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
