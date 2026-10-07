package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 光配方阶段：阶段口径见 {@link com.evops.plant.domain.GrowthStage}，
 * 区间 [fromProgress, toProgress) 左闭右开，见 {@link com.evops.plant.domain.GrowthStageRule}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_light_recipe_stage")
public class LightRecipeStage extends BaseEntity {
    private Long recipeId;
    private String stageCode;
    private Integer fromProgress;
    private Integer toProgress;
    /** 该阶段默认亮度百分比 0~100，可被时段亮度覆盖语义保留（时段亮度为最终下发值） */
    private Integer defaultBrightnessPct;
    private String remark;
}
