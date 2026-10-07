package com.evops.plant.domain;

import com.evops.plant.entity.LightRecipeStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 临界时间归属测试：把光配方相关的时钟调到各类临界时刻，
 * 验证阶段归属、整点生效、光时段、临时调整窗口四条规则按同一口径——左闭右开 [start, end)。
 */
class LightTimeRulesTest {

    private static LightRecipeStage stage(String code, Integer dayFrom, Integer dayTo) {
        LightRecipeStage s = new LightRecipeStage();
        s.setStageCode(code);
        s.setDayFrom(dayFrom);
        s.setDayTo(dayTo);
        return s;
    }

    /** 与种子配方一致的两阶段链：育苗 [0,14)，生长 [14,∞)。 */
    private static List<LightRecipeStage> twoStages() {
        return Arrays.asList(stage("SEEDLING", 0, 14), stage("GROWING", 14, null));
    }

    @Nested
    @DisplayName("生长阶段归属：day_from <= 天数 < day_to")
    class StageAttribution {

        @Test
        @DisplayName("播种当天为第 0 天，归第一个阶段")
        void sowDayIsDayZero() {
            LocalDate sow = LocalDate.of(2026, 10, 1);
            assertEquals(0, LightTimeRules.daysSinceSow(sow, sow));
            assertEquals(13, LightTimeRules.daysSinceSow(sow, LocalDate.of(2026, 10, 14)));
            assertEquals(14, LightTimeRules.daysSinceSow(sow, LocalDate.of(2026, 10, 15)));
        }

        @Test
        @DisplayName("临界日（第 14 天）归下一阶段，前一天仍归上一阶段")
        void boundaryDayBelongsToNextStage() {
            List<LightRecipeStage> stages = twoStages();
            assertEquals("SEEDLING", LightTimeRules.resolveStage(stages, 0).getStageCode());
            assertEquals("SEEDLING", LightTimeRules.resolveStage(stages, 13).getStageCode());
            // 临界：day_to=14 不含，第 14 天起归生长期
            assertEquals("GROWING", LightTimeRules.resolveStage(stages, 14).getStageCode());
            assertEquals("GROWING", LightTimeRules.resolveStage(stages, 15).getStageCode());
        }

        @Test
        @DisplayName("开放段（day_to 空）覆盖到生长结束，负天数无归属")
        void openEndedLastStage() {
            List<LightRecipeStage> stages = twoStages();
            assertEquals("GROWING", LightTimeRules.resolveStage(stages, 100).getStageCode());
            assertNull(LightTimeRules.resolveStage(stages, -1));
        }
    }

    @Nested
    @DisplayName("整点生效：不小于当前时间的最近整点")
    class EffectiveHour {

        @Test
        @DisplayName("恰在整点（10:00:00.000）当拍生效")
        void exactHourTakesEffectImmediately() {
            LocalDateTime onTheHour = LocalDateTime.of(2026, 10, 7, 10, 0, 0);
            assertEquals(LocalDateTime.of(2026, 10, 7, 10, 0), LightTimeRules.effectiveHour(onTheHour));
        }

        @Test
        @DisplayName("过整点一毫秒（10:00:00.001）归下一整点")
        void oneMilliPastHourGoesToNextHour() {
            LocalDateTime justPast = LocalDateTime.of(2026, 10, 7, 10, 0, 0, 1_000_000);
            assertEquals(LocalDateTime.of(2026, 10, 7, 11, 0), LightTimeRules.effectiveHour(justPast));
        }

        @Test
        @DisplayName("10:59:59 归 11:00；23:59 跨日归次日 00:00")
        void hourRollsOverMidnight() {
            assertEquals(LocalDateTime.of(2026, 10, 7, 11, 0),
                    LightTimeRules.effectiveHour(LocalDateTime.of(2026, 10, 7, 10, 59, 59)));
            assertEquals(LocalDateTime.of(2026, 10, 8, 0, 0),
                    LightTimeRules.effectiveHour(LocalDateTime.of(2026, 10, 7, 23, 59, 59)));
        }
    }

    @Nested
    @DisplayName("光时段归属：[on, off) 开灯，支持跨夜")
    class LightPeriod {

        private final LocalTime on = LocalTime.of(6, 0);
        private final LocalTime off = LocalTime.of(22, 0);

        @Test
        @DisplayName("到开灯点即开（06:00 开），到关灯点即关（22:00 关）")
        void dayPeriodBoundaries() {
            assertFalse(LightTimeRules.isLightOn(on, off, LocalTime.of(5, 59, 59)));
            assertTrue(LightTimeRules.isLightOn(on, off, LocalTime.of(6, 0, 0)));
            assertTrue(LightTimeRules.isLightOn(on, off, LocalTime.of(21, 59, 59)));
            assertFalse(LightTimeRules.isLightOn(on, off, LocalTime.of(22, 0, 0)));
        }

        @Test
        @DisplayName("跨夜时段（22:00–06:00）同一左闭右开口径")
        void overnightPeriodBoundaries() {
            LocalTime nightOn = LocalTime.of(22, 0);
            LocalTime nightOff = LocalTime.of(6, 0);
            assertFalse(LightTimeRules.isLightOn(nightOn, nightOff, LocalTime.of(21, 59, 59)));
            assertTrue(LightTimeRules.isLightOn(nightOn, nightOff, LocalTime.of(22, 0, 0)));
            assertTrue(LightTimeRules.isLightOn(nightOn, nightOff, LocalTime.of(0, 0, 0)));
            assertTrue(LightTimeRules.isLightOn(nightOn, nightOff, LocalTime.of(5, 59, 59)));
            assertFalse(LightTimeRules.isLightOn(nightOn, nightOff, LocalTime.of(6, 0, 0)));
            assertFalse(LightTimeRules.isLightOn(nightOn, nightOff, LocalTime.of(12, 0, 0)));
        }
    }

    @Nested
    @DisplayName("临时调整窗口：[start, end)，到结束时间即失效")
    class OverrideWindow {

        private final LocalDateTime start = LocalDateTime.of(2026, 10, 7, 10, 0);
        private final LocalDateTime end = LocalDateTime.of(2026, 10, 7, 12, 0);

        @Test
        @DisplayName("窗口临界：开始前不生效，起点生效，终点失效")
        void windowBoundaries() {
            assertFalse(LightTimeRules.inWindow(start, end, LocalDateTime.of(2026, 10, 7, 9, 59, 59)));
            assertTrue(LightTimeRules.inWindow(start, end, LocalDateTime.of(2026, 10, 7, 10, 0, 0)));
            assertTrue(LightTimeRules.inWindow(start, end, LocalDateTime.of(2026, 10, 7, 11, 59, 59)));
            assertFalse(LightTimeRules.inWindow(start, end, LocalDateTime.of(2026, 10, 7, 12, 0, 0)));
        }
    }

    @Nested
    @DisplayName("同一口径验证：配方调到临界时间，四条规则全部左闭右开")
    class SameCaliberAtBoundary {

        @Test
        @DisplayName("阶段切换日 00:00 整：阶段、整点、时段、窗口同时按 [start, end) 归属")
        void allRulesLeftClosedRightOpenAtCriticalInstant() {
            // 场景：批次 2026-09-23 播种，2026-10-07 恰为第 14 天（育苗→生长切换日）；
            // 把时钟调到该日 06:00:00.000 —— 阶段切换日、整点、开灯点三重临界
            LocalDate sow = LocalDate.of(2026, 9, 23);
            LocalDateTime critical = LocalDateTime.of(2026, 10, 7, 6, 0, 0);

            // 1) 阶段：第 14 天归生长期（临界日归右）
            long days = LightTimeRules.daysSinceSow(sow, critical.toLocalDate());
            assertEquals(14, days);
            assertEquals("GROWING", LightTimeRules.resolveStage(twoStages(), days).getStageCode());

            // 2) 整点：06:00:00.000 整点当拍生效（临界归左）
            assertEquals(critical, LightTimeRules.effectiveHour(critical));

            // 3) 时段：生长期 06:00–22:00，06:00:00 到点即开灯（临界归左）
            assertTrue(LightTimeRules.isLightOn(LocalTime.of(6, 0), LocalTime.of(22, 0),
                    critical.toLocalTime()));

            // 4) 窗口：一条 06:00 开始的临时调整在 06:00:00 生效（临界归左）
            assertTrue(LightTimeRules.inWindow(critical, critical.plusHours(2), critical));

            // 反向临界：前一天（第 13 天）仍归育苗期；05:59:59 未开灯、窗口未开始（全部归左）
            LocalDate dayBefore = LocalDate.of(2026, 10, 6);
            assertEquals(13, LightTimeRules.daysSinceSow(sow, dayBefore));
            assertEquals("SEEDLING", LightTimeRules.resolveStage(twoStages(),
                    LightTimeRules.daysSinceSow(sow, dayBefore)).getStageCode());
            LocalDateTime justBefore = critical.minusSeconds(1);
            assertEquals(LocalDateTime.of(2026, 10, 7, 6, 0), LightTimeRules.effectiveHour(justBefore));
            assertFalse(LightTimeRules.isLightOn(LocalTime.of(6, 0), LocalTime.of(22, 0),
                    justBefore.toLocalTime()));
            assertFalse(LightTimeRules.inWindow(critical, critical.plusHours(2), justBefore));
        }
    }
}
