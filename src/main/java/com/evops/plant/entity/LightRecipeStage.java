package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 配方阶段明细：按播种后天数归属，day_from <= 天数 < day_to（左闭右开，day_to 空=开放段）。
 * 光谱三项（红/蓝/远红）合计必须为 100；light_on/light_off 格式 HH:mm，支持跨夜。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_light_recipe_stage")
public class LightRecipeStage extends BaseEntity {
    private Long recipeId;
    private String stageCode;
    private String stageName;
    private Integer dayFrom;
    /** 空 = 开放段（直到生长结束） */
    private Integer dayTo;
    private Integer spectrumRed;
    private Integer spectrumBlue;
    private Integer spectrumFarRed;
    private Integer brightnessPpfd;
    private String lightOn;
    private String lightOff;
}
