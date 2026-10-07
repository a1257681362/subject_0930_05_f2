package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采收单：记录等级、重量与去向；采收单号唯一。
 * grade: A / B / C；qc_status: PENDING / PASSED / REJECTED
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_harvest_order")
public class HarvestOrder extends BaseEntity {
    private String harvestNo;
    private Long batchId;
    private String grade;
    private BigDecimal weightKg;
    private String destination;
    private LocalDate harvestDate;
    private String qcStatus;
    private Long operatorId;
    private String operatorName;
    private String remark;
}
