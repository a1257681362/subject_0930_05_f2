package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Data
public class GreenhouseRequest {
    @NotBlank(message = "温室编号不能为空")
    private String ghCode;
    @NotBlank(message = "温室名称不能为空")
    private String ghName;
    private String lightSource;
    private Integer photoperiodHours;
    private Integer ppfd;
    private String nutrientCycleMode;
    private BigDecimal nutrientEc;
    private BigDecimal nutrientPh;
    private Integer cycleIntervalMin;
    private String remark;
}
