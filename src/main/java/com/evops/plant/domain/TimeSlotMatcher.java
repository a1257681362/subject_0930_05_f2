package com.evops.plant.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 时段匹配的<b>唯一口径</b>：时间一律折算为"当地分钟数"（00:00=0，23:59=1439）。
 *
 * <p>区间统一为<b>左闭右开</b>：[startMinute, endMinute)。06:00 整点属于白天时段，
 * 22:00 整点属于夜间关灯时段——整点临界归属无歧义。
 *
 * <p>{@code endMinute <= startMinute} 表示<b>跨午夜</b>时段，如 22:00(1320)→06:00(360)；
 * {@code endMinute == startMinute} 表示全天 24 小时。一个阶段的全部时段必须无缝、不重叠
 * 地覆盖 24 小时（允许跨午夜），校验与匹配都走这一处。
 */
public final class TimeSlotMatcher {

    public static final int MINUTES_PER_DAY = 24 * 60;

    private TimeSlotMatcher() {
    }

    /** 时段定义（不可变值对象）。 */
    public static final class Slot {
        private final int startMinute;
        private final int endMinute;

        public Slot(int startMinute, int endMinute) {
            if (startMinute < 0 || startMinute >= MINUTES_PER_DAY) {
                throw new IllegalArgumentException("时段起始必须在 [0,1440) 分钟内: " + startMinute);
            }
            if (endMinute < 0 || endMinute > MINUTES_PER_DAY) {
                throw new IllegalArgumentException("时段结束必须在 [0,1440] 分钟内: " + endMinute);
            }
            this.startMinute = startMinute;
            this.endMinute = endMinute;
        }

        public int getStartMinute() {
            return startMinute;
        }

        public int getEndMinute() {
            return endMinute;
        }

        public boolean isWrapMidnight() {
            // end == start 视为全天；其余 end < start 为跨午夜
            return endMinute <= startMinute;
        }

        /** 左闭右开匹配；跨午夜时段覆盖 [start,1440) ∪ [0,end)。 */
        public boolean contains(int minuteOfDay) {
            if (minuteOfDay < 0 || minuteOfDay >= MINUTES_PER_DAY) {
                throw new IllegalArgumentException("分钟数必须在 [0,1440) 内: " + minuteOfDay);
            }
            if (endMinute > startMinute) {
                return minuteOfDay >= startMinute && minuteOfDay < endMinute;
            }
            // 跨午夜或全天
            return minuteOfDay >= startMinute || minuteOfDay < endMinute;
        }
    }

    /**
     * 校验一个阶段的时段无缝、不重叠地覆盖全天 24 小时。
     * 跨午夜时段先展开为两段再做合并检查。
     */
    public static void validateFullDayCoverage(List<Slot> slots) {
        if (slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("每个生长阶段至少配置一个时段");
        }
        // 展开成不跨午夜的 [start,end) 段
        List<int[]> segments = new ArrayList<>();
        for (Slot slot : slots) {
            if (slot.endMinute > slot.startMinute) {
                segments.add(new int[]{slot.startMinute, slot.endMinute});
            } else if (slot.endMinute == slot.startMinute) {
                // 全天时段：有它就不应再有别的时段
                if (slots.size() != 1) {
                    throw new IllegalArgumentException("全天时段不能与其他时段并存");
                }
                return;
            } else {
                segments.add(new int[]{slot.startMinute, MINUTES_PER_DAY});
                segments.add(new int[]{0, slot.endMinute});
            }
        }
        segments.sort(Comparator.comparingInt(a -> a[0]));
        if (segments.get(0)[0] != 0) {
            throw new IllegalArgumentException("时段必须从 00:00 开始无缝覆盖全天");
        }
        int cursor = 0;
        for (int[] seg : segments) {
            if (seg[0] > cursor) {
                throw new IllegalArgumentException(
                        "时段存在缺口: " + cursor + "~" + seg[0] + " 分钟未配置");
            }
            if (seg[0] < cursor) {
                throw new IllegalArgumentException(
                        "时段存在重叠: " + seg[0] + " 分钟落在已覆盖区间内");
            }
            cursor = seg[1];
        }
        if (cursor != MINUTES_PER_DAY) {
            throw new IllegalArgumentException("时段必须覆盖到 24:00，实际覆盖到: " + cursor);
        }
    }

    /** 匹配 minuteOfDay 命中的时段；调用前时段应已通过全天覆盖校验。 */
    public static Slot match(int minuteOfDay, List<Slot> slots) {
        for (Slot slot : slots) {
            if (slot.contains(minuteOfDay)) {
                return slot;
            }
        }
        throw new IllegalStateException("时段未覆盖全天，分钟 " + minuteOfDay + " 无匹配时段");
    }
}
