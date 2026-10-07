package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 光配方时段：[startMinute, endMinute) 左闭右开，end&lt;=start 表示跨午夜。
 * 光谱四项比例之和必须为 100（关灯时段全 0，亮度 0）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_light_recipe_slot")
public class LightRecipeSlot extends BaseEntity {
    private Long stageId;
    private Integer startMinute;
    private Integer endMinute;
    private Integer brightnessPct;
    private Integer ratioRedPct;
    private Integer ratioGreenPct;
    private Integer ratioBluePct;
    private Integer ratioWhitePct;
}
