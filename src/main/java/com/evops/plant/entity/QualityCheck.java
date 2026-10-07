package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 质检结果：一张采收单至多一条（uk_pf_qc_harvest）。result: PASSED / REJECTED */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_quality_check")
public class QualityCheck extends BaseEntity {
    private Long harvestId;
    private String result;
    private Long inspectorId;
    private String inspectorName;
    private LocalDateTime checkTime;
    private String note;
}
