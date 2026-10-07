package com.evops.plant.domain;

import com.evops.plant.entity.LightRecipeStage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * 光配方时间口径（全系统唯一定义处）：所有区间一律左闭右开 [start, end)。
 *  - 生长阶段归属：播种当天为第 0 天，day_from <= 天数 < day_to（day_to 空=开放段）；
 *  - 整点生效：生效时刻 = 不小于当前时间的最近整点（整点本身算当前拍）；
 *  - 光时段：light_on <= t < light_off 为开灯，off <= on 时跨夜；
 *  - 临时调整窗口：start_time <= t < end_time，到点自动失效。
 * 纯函数、无状态，临界时间归属由 LightTimeRulesTest 锁定。
 */
public final class LightTimeRules {

    private LightTimeRules() {
    }

    /** 播种后天数：播种当天为第 0 天。 */
    public static long daysSinceSow(LocalDate sowDate, LocalDate today) {
        return ChronoUnit.DAYS.between(sowDate, today);
    }

    /**
     * 阶段归属：day_from <= 天数 < day_to（day_to 空=开放段），临界日归下一阶段。
     * 阶段按 day_from 升序判定；无匹配（如天数为负）返回 null。
     */
    public static LightRecipeStage resolveStage(List<LightRecipeStage> stages, long daysSinceSow) {
        if (stages == null || daysSinceSow < 0) {
            return null;
        }
        return stages.stream()
                .sorted(Comparator.comparing(LightRecipeStage::getDayFrom))
                .filter(s -> daysSinceSow >= s.getDayFrom()
                        && (s.getDayTo() == null || daysSinceSow < s.getDayTo()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 整点生效：生效时刻 = 不小于 now 的最近整点。
     * 10:00:00.000 → 10:00（当拍生效）；10:00:00.001 → 11:00（下一拍）。
     */
    public static LocalDateTime effectiveHour(LocalDateTime now) {
        LocalDateTime hour = now.truncatedTo(ChronoUnit.HOURS);
        return hour.equals(now) ? hour : hour.plusHours(1);
    }

    /**
     * 光时段归属：[on, off) 为开灯；off <= on 视为跨夜（如 22:00–06:00）。
     * 到 on 点即开，到 off 点即关。
     */
    public static boolean isLightOn(LocalTime on, LocalTime off, LocalTime t) {
        if (on.equals(off)) {
            return false;
        }
        if (off.isAfter(on)) {
            return !t.isBefore(on) && t.isBefore(off);
        }
        // 跨夜：t >= on 或 t < off
        return !t.isBefore(on) || t.isBefore(off);
    }

    /** 临时调整窗口：[start, end)，到 end_time 即失效。 */
    public static boolean inWindow(LocalDateTime start, LocalDateTime end, LocalDateTime t) {
        return !t.isBefore(start) && t.isBefore(end);
    }
}
