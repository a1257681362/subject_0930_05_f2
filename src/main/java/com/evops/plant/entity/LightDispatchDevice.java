package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备执行项（重试队列）：下发失败的设备在此排队，到 next_retry_at 由任务重发。
 * status: PENDING 待发 / SUCCESS 成功 / FAILED 失败待重试 / RETRYING 重试中 / DEAD 超限放弃
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_light_dispatch_device")
public class LightDispatchDevice extends BaseEntity {
    private Long dispatchId;
    private Long deviceId;
    private String deviceCode;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private LocalDateTime sentAt;
}
