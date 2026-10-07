package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 人工临时调整：必须带结束时间与审批人（建表 NOT NULL + 服务层双重校验）。
 * 窗口 [start_time, end_time) 内优先于配方生效，到点自动失效。
 * status: ACTIVE 生效中 / EXPIRED 已过期 / REVOKED 已撤销
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_light_override")
public class LightOverride extends BaseEntity {
    private Long zoneId;
    private Integer spectrumRed;
    private Integer spectrumBlue;
    private Integer spectrumFarRed;
    private Integer brightnessPpfd;
    private String lightOn;
    private String lightOff;
    private LocalDateTime startTime;
    /** 必填：临时调整不允许无限期 */
    private LocalDateTime endTime;
    /** 必填：审批人，关联 t_pf_operator */
    private Long approverId;
    private String approverName;
    private String reason;
    private String status;
}
