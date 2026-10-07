package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 光配方版本主表：按品种配置，同一 recipe_code 逐版递增。
 * status: DRAFT 草稿 / PUBLISHED 已发布 / ARCHIVED 已归档（被新版本取代）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_light_recipe")
public class LightRecipe extends BaseEntity {
    private String recipeCode;
    private Long varietyId;
    private Integer versionNo;
    private String status;
    private String remark;
    private LocalDateTime publishedAt;
    private Long publishedBy;
}
