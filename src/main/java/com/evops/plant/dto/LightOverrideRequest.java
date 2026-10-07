package com.evops.plant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.time.LocalDateTime;

/** 人工临时调整：必须带结束时间与审批人；窗口 [startTime, endTime) 内优先于配方生效。 */
@Data
public class LightOverrideRequest {
    @NotNull(message = "棚区不能为空")
    private Long zoneId;
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
    /** 空 = 立即（从当前时间起） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @NotNull(message = "人工临时调整必须带结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    @NotNull(message = "人工临时调整必须带审批人")
    private Long approverId;
    @NotBlank(message = "调整原因不能为空")
    private String reason;
}
