package com.evops.plant.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 阶段归属临界口径测试：区间 [from, to) 左闭右开，
 * 临界进度（30/70/90）必须归后一阶段，100 钳制为最后阶段。
 */
class GrowthStageRuleTest {

    private List<GrowthStageRule.StageRange> standardRanges() {
        return new ArrayList<>(Arrays.asList(
                new GrowthStageRule.StageRange(GrowthStage.SEEDLING, 0, 30),
                new GrowthStageRule.StageRange(GrowthStage.VEGETATIVE, 30, 70),
                new GrowthStageRule.StageRange(GrowthStage.FLOWERING, 70, 90),
                new GrowthStageRule.StageRange(GrowthStage.FRUITING, 90, 100)));
    }

    @Test
    void criticalProgress_followsLeftClosedRightOpen() {
        List<GrowthStageRule.StageRange> ranges = standardRanges();
        // 区间下界归属本阶段，上界归属下一阶段
        assertEquals(GrowthStage.SEEDLING, GrowthStageRule.resolve(0, ranges));
        assertEquals(GrowthStage.SEEDLING, GrowthStageRule.resolve(29, ranges));
        assertEquals(GrowthStage.VEGETATIVE, GrowthStageRule.resolve(30, ranges));
        assertEquals(GrowthStage.VEGETATIVE, GrowthStageRule.resolve(69, ranges));
        assertEquals(GrowthStage.FLOWERING, GrowthStageRule.resolve(70, ranges));
        assertEquals(GrowthStage.FLOWERING, GrowthStageRule.resolve(89, ranges));
        assertEquals(GrowthStage.FRUITING, GrowthStageRule.resolve(90, ranges));
        assertEquals(GrowthStage.FRUITING, GrowthStageRule.resolve(99, ranges));
        // 100（计划采收当日）钳制为最后阶段
        assertEquals(GrowthStage.FRUITING, GrowthStageRule.resolve(100, ranges));
    }

    @Test
    void rejectGapOverlapMissingAndOutOfRange() {
        // 有缺口：幼苗 [0,25) 营养 [30,70)
        assertThrows(IllegalArgumentException.class, () -> GrowthStageRule.validate(Arrays.asList(
                new GrowthStageRule.StageRange(GrowthStage.SEEDLING, 0, 25),
                new GrowthStageRule.StageRange(GrowthStage.VEGETATIVE, 30, 70),
                new GrowthStageRule.StageRange(GrowthStage.FLOWERING, 70, 90),
                new GrowthStageRule.StageRange(GrowthStage.FRUITING, 90, 100))));
        // 重叠：[0,30) 与 [29,70)
        assertThrows(IllegalArgumentException.class, () -> GrowthStageRule.validate(Arrays.asList(
                new GrowthStageRule.StageRange(GrowthStage.SEEDLING, 0, 30),
                new GrowthStageRule.StageRange(GrowthStage.VEGETATIVE, 29, 70),
                new GrowthStageRule.StageRange(GrowthStage.FLOWERING, 70, 90),
                new GrowthStageRule.StageRange(GrowthStage.FRUITING, 90, 100))));
        // 缺阶段
        assertThrows(IllegalArgumentException.class, () -> GrowthStageRule.validate(Arrays.asList(
                new GrowthStageRule.StageRange(GrowthStage.SEEDLING, 0, 50),
                new GrowthStageRule.StageRange(GrowthStage.VEGETATIVE, 50, 100))));
        // 未覆盖到 100
        assertThrows(IllegalArgumentException.class, () -> GrowthStageRule.validate(Arrays.asList(
                new GrowthStageRule.StageRange(GrowthStage.SEEDLING, 0, 30),
                new GrowthStageRule.StageRange(GrowthStage.VEGETATIVE, 30, 70),
                new GrowthStageRule.StageRange(GrowthStage.FLOWERING, 70, 90),
                new GrowthStageRule.StageRange(GrowthStage.FRUITING, 90, 99))));
    }

    @Test
    void rejectProgressOutOfBound() {
        assertThrows(IllegalArgumentException.class,
                () -> GrowthStageRule.resolve(-1, standardRanges()));
        assertThrows(IllegalArgumentException.class,
                () -> GrowthStageRule.resolve(101, standardRanges()));
    }
}
