package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 失败设备重试队列：下发失败的棚区设备进入本队列，按退避间隔重试，成功置 DONE。
 * 同一 dispatch 唯一一条（uk_pf_light_retry_dispatch）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_light_dispatch_retry")
public class LightDispatchRetry extends BaseEntity {
    private Long dispatchId;
    private Long greenhouseId;
    private Long zoneId;
    private LocalDateTime nextRetryAt;
    private Integer attemptCount;
    /** PENDING / DONE / GIVEN_UP */
    private String status;
    private String lastFailReason;
}
