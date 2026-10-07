package com.evops.plant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 人工临时调整请求：结束时间与审批人为强制项（业务规则，缺一不可）。
 * 到期整点由定时任务自动恢复配方。
 */
@Data
public class LightOverrideRequest {

    @NotNull(message = "温室不能为空")
    private Long greenhouseId;
    /** 可空：棚区级调整；非空表示只作用于指定分区 */
    private Long zoneId;
    /** 可空：绑定具体批次，便于留痕与随批次迁移 */
    private Long batchId;

    @NotNull(message = "亮度不能为空")
    @Min(value = 0, message = "亮度不能小于0")
    @Max(value = 100, message = "亮度不能大于100")
    private Integer brightnessPct;

    @NotNull @Min(0) @Max(100)
    private Integer ratioRedPct;
    @NotNull @Min(0) @Max(100)
    private Integer ratioGreenPct;
    @NotNull @Min(0) @Max(100)
    private Integer ratioBluePct;
    @NotNull @Min(0) @Max(100)
    private Integer ratioWhitePct;

    @NotNull(message = "开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    /** 强制：临时调整必须带结束时间 */
    @NotNull(message = "人工临时调整必须填写结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;

    /** 强制：人工临时调整必须带审批人 */
    @NotNull(message = "人工临时调整必须填写审批人")
    private Long approverId;

    @NotBlank(message = "调整原因不能为空")
    private String reason;
}
