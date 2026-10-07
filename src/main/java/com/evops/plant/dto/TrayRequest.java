package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

@Data
public class TrayRequest {
    @NotBlank(message = "播种盘编号不能为空")
    private String trayCode;
    private String spec;
    @Positive(message = "孔数必须为正数")
    private Integer holeCount;
}
