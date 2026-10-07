package com.evops.plant.domain;

import java.util.Arrays;
import java.util.List;

/**
 * 植物生长阶段口径（全系统唯一口径，配方配置、批次归属、阶段切换都引用本枚举）。
 * 阶段按固定顺序推进，{@link #order()} 即阶段先后，禁止各处自行定义阶段字符串/先后。
 */
public enum GrowthStage {

    /** 幼苗期 */
    SEEDLING("幼苗期"),
    /** 营养生长期 */
    VEGETATIVE("营养生长期"),
    /** 开花期 */
    FLOWERING("开花期"),
    /** 结果期 */
    FRUITING("结果期");

    private final String label;

    GrowthStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 阶段先后顺序，从 0 开始。 */
    public int order() {
        return ordinal();
    }

    public static GrowthStage ofCode(String code) {
        for (GrowthStage stage : values()) {
            if (stage.name().equals(code)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("未知生长阶段: " + code);
    }

    /** 按 order 排序的全部阶段（阶段切换/未开始判定的统一遍历顺序）。 */
    public static List<GrowthStage> ordered() {
        return Arrays.asList(values());
    }
}
