package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 种植批次：关联品种、播种盘与计划采收日。
 * status: SOWN 已播种 / GROWING 养护中 / MOVED 已转棚 / HARVESTED 已采收
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_batch")
public class Batch extends BaseEntity {
    private String batchNo;
    private Long varietyId;
    private Long trayId;
    private Long greenhouseId;
    private Long zoneId;
    private LocalDate sowDate;
    private LocalDate plannedHarvestDate;
    private LocalDate actualHarvestDate;
    private String status;
    /** Y=定时任务创建的"台账起步"批次 */
    private String startupFlag;
    private String remark;
}
