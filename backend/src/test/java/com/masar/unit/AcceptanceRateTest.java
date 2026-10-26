package com.masar.unit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.masar.dashboard.DashboardService;

/**
 * Whole-percent rounding of the acceptance rate.
 * تقريب نسبة القبول لرقم صحيح.
 */
class AcceptanceRateTest {

    @Test
    void roundsHalfUpAndIsNullWithoutAnswers() {
        assertThat(DashboardService.acceptanceRate(0, 0)).isNull();
        assertThat(DashboardService.acceptanceRate(3, 1)).isEqualTo(75);
        // 1 of 8 = 12.5% → 13. / 1 من 8 = 12.5% ← 13.
        assertThat(DashboardService.acceptanceRate(1, 7)).isEqualTo(13);
        // 2 of 3 = 66.67% → 67. / 2 من 3 ← 67.
        assertThat(DashboardService.acceptanceRate(2, 1)).isEqualTo(67);
        assertThat(DashboardService.acceptanceRate(0, 5)).isZero();
        assertThat(DashboardService.acceptanceRate(5, 0)).isEqualTo(100);
    }
}
