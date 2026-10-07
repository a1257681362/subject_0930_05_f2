package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 光控下发流水：定时任务按棚区（温室+分区）整点下发。
 * payload_json 为下发时刻冻结的配方/调整快照——历史执行保留快照，配方后续改动不溯及既往。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_light_dispatch")
public class LightDispatch extends BaseEntity {
    private Long greenhouseId;
    private Long zoneId;
    private Long batchId;
    private String stageCode;
    /** 命中的时段起点（分钟）；人工调整无时段概念时为空 */
    private Integer slotStartMinute;
    private Long recipeId;
    private Integer recipeVersionNo;
    /** RECIPE / OVERRIDE / RESTORE / RETRY */
    private String source;
    private String payloadJson;
    /** 计划下发整点（幂等键维度） */
    private LocalDateTime plannedAt;
    private LocalDateTime dispatchedAt;
    /** SUCCESS / FAILED；null 表示尚未送达（待重试） */
    private String result;
    private String failReason;
    private Long operatorId;
    private String operatorName;
}
