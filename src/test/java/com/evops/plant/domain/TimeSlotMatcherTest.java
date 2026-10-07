package com.evops.plant.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 时段临界口径测试：[start,end) 左闭右开，整点临界（06:00/22:00）归属无歧义；
 * 跨午夜时段与全天时段匹配正确。
 */
class TimeSlotMatcherTest {

    private final TimeSlotMatcher.Slot day = new TimeSlotMatcher.Slot(360, 1320);     // 06:00-22:00
    private final TimeSlotMatcher.Slot night = new TimeSlotMatcher.Slot(1320, 360);   // 22:00-次日06:00 跨午夜
    private final List<TimeSlotMatcher.Slot> dayNight = Arrays.asList(day, night);

    @Test
    void criticalHourBelongsToLaterSlot() {
        TimeSlotMatcher.validateFullDayCoverage(dayNight);
        // 05:59 夜间关灯；06:00 整点属于白天给光
        assertSame(night, TimeSlotMatcher.match(359, dayNight));
        assertSame(day, TimeSlotMatcher.match(360, dayNight));
        // 21:59 白天；22:00 整点属于夜间关灯
        assertSame(day, TimeSlotMatcher.match(1319, dayNight));
        assertSame(night, TimeSlotMatcher.match(1320, dayNight));
        assertSame(night, TimeSlotMatcher.match(1439, dayNight));
        assertSame(night, TimeSlotMatcher.match(0, dayNight));
    }

    @Test
    void wrapMidnightSlotCoversEveningAndEarlyMorning() {
        // 22:00(1320) → 次日 06:00(360) 跨午夜
        TimeSlotMatcher.Slot wrap = new TimeSlotMatcher.Slot(1320, 360);
        assertFalse(wrap.contains(1319));
        assertTrue(wrap.contains(1320));
        assertTrue(wrap.contains(0));
        assertTrue(wrap.contains(359));
        assertFalse(wrap.contains(360));
    }

    @Test
    void allDaySlotWhenEndEqualsStart() {
        TimeSlotMatcher.Slot allDay = new TimeSlotMatcher.Slot(0, 0);
        assertTrue(allDay.contains(0));
        assertTrue(allDay.contains(720));
        assertTrue(allDay.contains(1439));
        // 全天时段不能与其他时段并存
        assertThrows(IllegalArgumentException.class, () ->
                TimeSlotMatcher.validateFullDayCoverage(
                        Arrays.asList(allDay, new TimeSlotMatcher.Slot(0, 1440))));
        TimeSlotMatcher.validateFullDayCoverage(Collections.singletonList(allDay));
    }

    @Test
    void coverageValidationRejectsGapAndOverlap() {
        // 缺口：00:00-06:00 缺失（只有白天非跨午夜段）
        assertThrows(IllegalArgumentException.class, () ->
                TimeSlotMatcher.validateFullDayCoverage(Collections.singletonList(
                        new TimeSlotMatcher.Slot(360, 1440))));
        // 重叠：06:00-22:00 与 21:00-24:00
        assertThrows(IllegalArgumentException.class, () ->
                TimeSlotMatcher.validateFullDayCoverage(Arrays.asList(
                        new TimeSlotMatcher.Slot(0, 360),
                        new TimeSlotMatcher.Slot(360, 1320),
                        new TimeSlotMatcher.Slot(1260, 1440))));
        // 合法：白天 + 跨午夜夜间，展开后无缝覆盖全天
        TimeSlotMatcher.validateFullDayCoverage(dayNight);
    }

    @Test
    void wrapMidnightPlusDayCoversFullDay() {
        TimeSlotMatcher.validateFullDayCoverage(Arrays.asList(
                new TimeSlotMatcher.Slot(360, 1320),
                new TimeSlotMatcher.Slot(1320, 360)));
    }
}
