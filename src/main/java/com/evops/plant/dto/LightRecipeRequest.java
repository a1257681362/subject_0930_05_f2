package com.evops.plant.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/** 创建光配方（草稿，版本号自动递增）；发布后只影响未开始的阶段。 */
@Data
public class LightRecipeRequest {
    @NotBlank(message = "配方编码不能为空")
    private String recipeCode;
    @NotNull(message = "品种不能为空")
    private Long varietyId;
    private String remark;
    @NotEmpty(message = "配方至少包含一个生长阶段")
    @Valid
    private List<LightStageRequest> stages;
}
