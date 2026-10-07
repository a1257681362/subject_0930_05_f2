package com.evops.plant.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 批次生长进度口径：进度 = 已生长天数 / 计划周期天数 * 100，钳制在 [0,100]。
 *
 * <p>与 {@link GrowthStageRule} 同一口径配合：进度 0 为播种当天（幼苗期），
 * 计划采收当天为 100（钳制到最后阶段，直至批次采收、不再下发）。
 */
public final class BatchGrowth {

    private BatchGrowth() {
    }

    /**
     * @param sowDate             播种日
     * @param plannedHarvestDate  计划采收日（必须晚于播种日）
     * @param today               归属判定日（临界时间测试可显式指定）
     * @return 0~100 的整数进度
     */
    public static int progress(LocalDate sowDate, LocalDate plannedHarvestDate, LocalDate today) {
        if (sowDate == null || plannedHarvestDate == null || today == null) {
            throw new IllegalArgumentException("播种日/计划采收日/判定日均不能为空");
        }
        long totalDays = ChronoUnit.DAYS.between(sowDate, plannedHarvestDate);
        if (totalDays <= 0) {
            throw new IllegalArgumentException("计划采收日必须晚于播种日");
        }
        long elapsedDays = ChronoUnit.DAYS.between(sowDate, today);
        if (elapsedDays <= 0) {
            return 0;
        }
        if (elapsedDays >= totalDays) {
            return 100;
        }
        return (int) (elapsedDays * 100 / totalDays);
    }
}
