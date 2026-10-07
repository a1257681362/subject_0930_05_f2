package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class ZoneRequest {
    @NotBlank(message = "分区编号不能为空")
    private String zoneCode;
    @NotBlank(message = "分区名称不能为空")
    private String zoneName;
    private String functionType;
    private String equipmentDesc;
}
