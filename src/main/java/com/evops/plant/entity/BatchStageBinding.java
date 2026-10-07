package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 批次阶段版本绑定：批次每次进入新阶段时冻结一条（进入时的生效配方版本）。
 * 配方新版本只影响"未开始"的阶段；ACTIVE 阶段沿用冻结版本，FINISHED 后为历史快照永不变形。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_batch_stage_binding")
public class BatchStageBinding extends BaseEntity {
    private Long batchId;
    private String stageCode;
    private Long recipeId;
    private Integer recipeVersionNo;
    private Integer progressAtEntry;
    private LocalDateTime enteredAt;
    private LocalDateTime leftAt;
    /** ACTIVE 当前阶段（每批次至多一条）/ FINISHED 历史阶段 */
    private String status;
}
