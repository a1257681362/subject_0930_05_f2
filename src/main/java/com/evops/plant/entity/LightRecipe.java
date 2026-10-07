package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 光配方头表：按品种版本化配置。
 * status: DRAFT 草稿（可改）/ PUBLISHED 已发布（只追加，不再修改；改配方发新版本）。
 * 同一品种至多一个 PUBLISHED 版本在当前时刻生效；effective_at 之前仍沿用旧版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_light_recipe")
public class LightRecipe extends BaseEntity {
    private String recipeCode;
    private Long varietyId;
    private Integer versionNo;
    private String status;
    /** 新版本基于哪个历史版本复制而来（可空，表示全新配方） */
    private Long baseFromVersionId;
    private LocalDateTime publishedAt;
    /** 生效时间（取发布后的下一整点）；到点前不下发新版本 */
    private LocalDateTime effectiveAt;
    private String remark;
}
