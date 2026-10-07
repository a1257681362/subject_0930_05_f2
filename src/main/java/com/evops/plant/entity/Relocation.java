package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 转棚留痕（只追加，不修改、不删除）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_batch_relocation")
public class Relocation extends BaseEntity {
    private Long batchId;
    private Integer seqNo;
    private Long fromGreenhouseId;
    private Long fromZoneId;
    private Long toGreenhouseId;
    private Long toZoneId;
    private String reason;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime relocatedAt;
}
