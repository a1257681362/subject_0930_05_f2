package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 养护记录：LIGHT-光照巡检 / NUTRIENT-营养液循环巡检 / EQUIPMENT-设备巡检 / OTHER
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_care_log")
public class CareLog extends BaseEntity {
    private Long batchId;
    private String careType;
    private String content;
    private BigDecimal reading;
    private String unit;
    private LocalDateTime logTime;
    private Long operatorId;
    private String operatorName;
}
