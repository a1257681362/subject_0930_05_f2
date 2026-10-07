package com.evops.plant.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 生长阶段归属的<b>唯一口径</b>：按批次生长进度（0~100，百分比）落在阶段区间判定。
 *
 * <p>区间统一为<b>左闭右开 [from, to)</b>：进度 30 归属营养生长期而非幼苗期，
 * 临界进度不存在"两个阶段都算"的歧义；100（计划采收当日）钳制为最后一个阶段，
 * 批次采收后不再参与下发。
 *
 * <p>阶段定义必须与 {@link GrowthStage} 严格一一对应、按阶段顺序首尾相接覆盖 0~100，
 * 不允许缺口、重叠或乱序——配置发布前与归属判定都走这一处校验。
 */
public final class GrowthStageRule {

    private GrowthStageRule() {
    }

    /** 阶段区间定义（不可变值对象）。 */
    public static final class StageRange {
        private final GrowthStage stage;
        private final int fromProgress;
        private final int toProgress;

        public StageRange(GrowthStage stage, int fromProgress, int toProgress) {
            this.stage = stage;
            this.fromProgress = fromProgress;
            this.toProgress = toProgress;
        }

        public GrowthStage getStage() {
            return stage;
        }

        public int getFromProgress() {
            return fromProgress;
        }

        public int getToProgress() {
            return toProgress;
        }

        public boolean contains(int progress) {
            // 左闭右开：from <= progress < to
            return progress >= fromProgress && progress < toProgress;
        }
    }

    /**
     * 校验阶段定义与统一口径一致：与 GrowthStage 一一对应、按序、从 0 起、到 100 止、首尾相接。
     *
     * @throws IllegalArgumentException 阶段缺失/乱序/有缺口或重叠/越界
     */
    public static List<StageRange> validate(List<StageRange> ranges) {
        if (ranges == null || ranges.size() != GrowthStage.values().length) {
            throw new IllegalArgumentException(
                    "光配方必须覆盖全部 " + GrowthStage.values().length + " 个生长阶段");
        }
        List<StageRange> ordered = new ArrayList<>(ranges);
        ordered.sort((a, b) -> a.stage.order() - b.stage.order());
        int expectedFrom = 0;
        for (int i = 0; i < ordered.size(); i++) {
            StageRange range = ordered.get(i);
            GrowthStage expected = GrowthStage.ordered().get(i);
            if (range.stage != expected) {
                throw new IllegalArgumentException("生长阶段配置缺失或重复: " + expected);
            }
            if (range.fromProgress != expectedFrom || range.toProgress <= range.fromProgress) {
                throw new IllegalArgumentException(
                        "阶段 " + expected + " 进度区间必须为 [" + expectedFrom + ", ?) 且上界大于下界");
            }
            if (range.fromProgress < 0 || range.toProgress > 100) {
                throw new IllegalArgumentException("阶段进度必须在 0~100 之间: " + expected);
            }
            expectedFrom = range.toProgress;
        }
        if (expectedFrom != 100) {
            throw new IllegalArgumentException("阶段区间必须首尾相接覆盖 0~100，实际覆盖到: " + expectedFrom);
        }
        return ordered;
    }

    /**
     * 按统一口径归属阶段。
     *
     * @param progress 批次当前生长进度 [0,100]
     * @param ranges   已通过 {@link #validate} 的阶段区间
     * @return 命中的阶段；progress=100 钳制为最后阶段
     */
    public static GrowthStage resolve(int progress, List<StageRange> ranges) {
        List<StageRange> ordered = validate(ranges);
        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException("生长进度超出 0~100 范围: " + progress);
        }
        for (StageRange range : ordered) {
            if (range.contains(progress)) {
                return range.stage;
            }
        }
        // progress == 100：所有 [from,100) 都不命中，钳制到最后阶段（直至采收）
        return ordered.get(ordered.size() - 1).getStage();
    }
}
