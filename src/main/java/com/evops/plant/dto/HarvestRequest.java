package com.evops.plant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class HarvestRequest {
    /** 等级：A / B / C */
    @NotBlank(message = "采收等级不能为空")
    private String grade;
    @NotNull(message = "采收重量不能为空")
    @Positive(message = "采收重量必须大于 0")
    private BigDecimal weightKg;
    @NotBlank(message = "采收去向不能为空")
    private String destination;
    @NotNull(message = "采收日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate harvestDate;
    private String remark;
}
