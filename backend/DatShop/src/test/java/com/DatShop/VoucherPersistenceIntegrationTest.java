package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.DatShop.model.Voucher;
import com.DatShop.model.VoucherType;
import com.DatShop.repository.VoucherRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class VoucherPersistenceIntegrationTest {

    @Autowired
    private VoucherRepository voucherRepository;

    @Test
    void persistsUniqueUppercaseCodeMoneyAndDefaultUsedCount() {
        Voucher voucher = voucher("WELCOME10");
        voucher.setDiscountValue(new BigDecimal("10.50"));
        voucher.setMinOrderAmount(new BigDecimal("500000.00"));
        Voucher saved = voucherRepository.saveAndFlush(voucher);

        Voucher loaded = voucherRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getCode()).isEqualTo("WELCOME10");
        assertThat(loaded.getUsedCount()).isEqualTo(0);
        assertThat(loaded.getDiscountValue()).isEqualByComparingTo("10.50");
        assertThat(loaded.getMinOrderAmount()).isEqualByComparingTo("500000.00");
        assertThat(voucherRepository.findByCodeIgnoreCase("welcome10")).isPresent();

        Voucher duplicate = voucher("WELCOME10");
        assertThatThrownBy(() -> voucherRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Voucher voucher(String code) {
        Voucher voucher = new Voucher();
        voucher.setCode(code);
        voucher.setType(VoucherType.PERCENT);
        voucher.setDiscountValue(new BigDecimal("10.00"));
        voucher.setUsedCount(0);
        voucher.setStartAt(Instant.parse("2026-01-01T00:00:00Z"));
        voucher.setEndAt(Instant.parse("2026-12-31T23:59:59Z"));
        voucher.setActive(true);
        return voucher;
    }
}
