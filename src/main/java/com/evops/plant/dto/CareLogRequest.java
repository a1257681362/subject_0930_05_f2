package com.evops.plant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CareLogRequest {
    /** LIGHT-光照 / NUTRIENT-营养液循环 / EQUIPMENT-设备 / OTHER */
    @NotBlank(message = "养护类型不能为空")
    private String careType;
    @NotBlank(message = "养护内容不能为空")
    private String content;
    private BigDecimal reading;
    private String unit;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime logTime;
}
