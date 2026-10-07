package com.evops.plant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class SowRequest {
    @NotNull(message = "品种不能为空")
    private Long varietyId;
    @NotNull(message = "播种盘不能为空")
    private Long trayId;
    @NotNull(message = "温室不能为空")
    private Long greenhouseId;
    private Long zoneId;
    @NotNull(message = "播种日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate sowDate;
    @NotNull(message = "计划采收日不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate plannedHarvestDate;
    private String remark;
}
