package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

@Data
public class VarietyRequest {
    @NotBlank(message = "品种编号不能为空")
    private String varietyCode;
    @NotBlank(message = "品种名称不能为空")
    private String varietyName;
    private String category;
    @Positive(message = "生长周期天数必须为正数")
    private Integer growDays = 30;
}
