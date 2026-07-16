package com.example.ec.service;

import com.example.ec.dto.CouponForm;
import com.example.ec.entity.DiscountType;
import com.example.ec.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    private CouponService couponService;

    @BeforeEach
    void setUp() {
        couponService = new CouponService(couponRepository);
    }

    private CouponForm formOf(DiscountType type, int discountValue) {
        CouponForm form = new CouponForm();
        form.setCode("SAVE10");
        form.setDiscountType(type);
        form.setDiscountValue(discountValue);
        form.setMinOrderAmount(0);
        return form;
    }

    @Test
    void save_割引率が100を超える率クーポンは拒否される() {
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.empty());
        CouponForm form = formOf(DiscountType.PERCENTAGE, 150);

        assertThatThrownBy(() -> couponService.save(form))
                .isInstanceOf(IllegalArgumentException.class);
        verify(couponRepository, never()).save(any());
    }

    @Test
    void save_割引率が100ちょうどの率クーポンは許可される() {
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.empty());
        when(couponRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CouponForm form = formOf(DiscountType.PERCENTAGE, 100);

        couponService.save(form);

        verify(couponRepository).save(any());
    }

    @Test
    void save_固定額クーポンは100を超えても許可される() {
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.empty());
        when(couponRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CouponForm form = formOf(DiscountType.FIXED_AMOUNT, 5000);

        couponService.save(form);

        verify(couponRepository).save(any());
    }

    @Test
    void save_有効期間の開始日が終了日より後の場合は拒否される() {
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.empty());
        CouponForm form = formOf(DiscountType.FIXED_AMOUNT, 500);
        form.setValidFrom(LocalDate.of(2026, 4, 1));
        form.setValidUntil(LocalDate.of(2026, 3, 1));

        assertThatThrownBy(() -> couponService.save(form))
                .isInstanceOf(IllegalArgumentException.class);
        verify(couponRepository, never()).save(any());
    }
}
