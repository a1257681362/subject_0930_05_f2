package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 灯控设备（隶属分区）。status: ACTIVE 正常 / FAULT 故障 / OFFLINE 离线；
 * 非 ACTIVE 设备下发失败，进入重试队列。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_light_device")
public class LightDevice extends BaseEntity {
    private String deviceCode;
    private Long zoneId;
    private String status;
}
