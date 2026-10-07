package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class RelocateRequest {
    @NotNull(message = "目标温室不能为空")
    private Long toGreenhouseId;
    private Long toZoneId;
    private String reason;
}
