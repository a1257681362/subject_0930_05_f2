package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/** 配方阶段：day_from <= 播种后天数 < day_to（左闭右开，day_to 空=开放段）。 */
@Data
public class LightStageRequest {
    @NotBlank(message = "阶段编码不能为空")
    private String stageCode;
    @NotBlank(message = "阶段名称不能为空")
    private String stageName;
    @NotNull(message = "阶段起始天数不能为空")
    @Min(value = 0, message = "阶段起始天数不能为负")
    private Integer dayFrom;
    /** 空 = 开放段（直到生长结束），仅允许最后一个阶段 */
    private Integer dayTo;
    @NotNull(message = "红光比例不能为空")
    @Min(0) @Max(100)
    private Integer spectrumRed;
    @NotNull(message = "蓝光比例不能为空")
    @Min(0) @Max(100)
    private Integer spectrumBlue;
    @NotNull(message = "远红光比例不能为空")
    @Min(0) @Max(100)
    private Integer spectrumFarRed;
    @NotNull(message = "亮度 PPFD 不能为空")
    @Min(1)
    private Integer brightnessPpfd;
    @NotBlank(message = "开灯时间不能为空")
    @Pattern(regexp = "^\\d{2}:\\d{2}$", message = "开灯时间格式必须为 HH:mm")
    private String lightOn;
    @NotBlank(message = "关灯时间不能为空")
    @Pattern(regexp = "^\\d{2}:\\d{2}$", message = "关灯时间格式必须为 HH:mm")
    private String lightOff;
}
