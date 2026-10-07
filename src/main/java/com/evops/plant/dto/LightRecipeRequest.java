package com.evops.plant.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 光配方配置请求（创建草稿 / 基于历史版本发新版共用）。
 * 阶段与时段口径由服务端按 GrowthStageRule / TimeSlotMatcher 统一校验。
 */
@Data
public class LightRecipeRequest {

    /** 创建全新配方时必填；基于历史版本发新版时由路径参数给出，可空 */
    private Long varietyId;

    private String remark;

    @NotEmpty(message = "至少配置一个生长阶段")
    @Valid
    private List<StageDto> stages;

    @Data
    public static class StageDto {
        @NotNull(message = "生长阶段不能为空")
        private String stageCode;
        @NotNull(message = "阶段起始进度不能为空")
        private Integer fromProgress;
        @NotNull(message = "阶段结束进度不能为空")
        private Integer toProgress;
        @NotNull(message = "阶段默认亮度不能为空")
        private Integer defaultBrightnessPct;
        private String remark;

        @NotEmpty(message = "每个阶段至少配置一个时段")
        @Valid
        private List<SlotDto> slots;
    }

    @Data
    public static class SlotDto {
        /** 当地时间 HH:mm，左闭右开；end 早于/等于 start 表示跨午夜 */
        @NotNull(message = "时段开始时间不能为空")
        private String start;
        @NotNull(message = "时段结束时间不能为空")
        private String end;
        @NotNull(message = "亮度不能为空")
        private Integer brightnessPct;
        @NotNull(message = "红光比例不能为空")
        private Integer ratioRedPct;
        @NotNull(message = "绿光比例不能为空")
        private Integer ratioGreenPct;
        @NotNull(message = "蓝光比例不能为空")
        private Integer ratioBluePct;
        @NotNull(message = "白光比例不能为空")
        private Integer ratioWhitePct;
    }
}
