package com.evops.plant.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class QualityCheckRequest {
    /** PASSED-合格 / REJECTED-不合格 */
    @NotBlank(message = "质检结论不能为空")
    private String result;
    private String note;
}
