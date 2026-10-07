package com.evops.plant.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 生长进度口径测试：与阶段左闭右开区间配合，临界日归属明确。 */
class BatchGrowthTest {

    private final LocalDate sow = LocalDate.of(2026, 10, 1);
    private final LocalDate harvest = LocalDate.of(2026, 10, 31);

    @Test
    void progressAcrossCycle() {
        assertEquals(0, BatchGrowth.progress(sow, harvest, LocalDate.of(2026, 10, 1)));
        assertEquals(30, BatchGrowth.progress(sow, harvest, LocalDate.of(2026, 10, 10)));
        assertEquals(63, BatchGrowth.progress(sow, harvest, LocalDate.of(2026, 10, 20)));
        // 计划采收当日钳制 100
        assertEquals(100, BatchGrowth.progress(sow, harvest, LocalDate.of(2026, 10, 31)));
    }

    @Test
    void rejectInvalidDates() {
        assertThrows(IllegalArgumentException.class,
                () -> BatchGrowth.progress(harvest, sow, sow));
        assertThrows(IllegalArgumentException.class,
                () -> BatchGrowth.progress(null, harvest, sow));
    }
}
