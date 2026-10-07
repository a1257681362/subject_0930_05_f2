package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 人工临时调整：必须带结束时间 end_at 与审批人 approver_id（接口强校验，缺一不可）。
 * 生效期间整点下发覆盖配方；到期整点由定时任务自动恢复配方并置 EXPIRED；可提前 REVOKED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_light_manual_override")
public class LightManualOverride extends BaseEntity {
    private Long greenhouseId;
    private Long zoneId;
    /** 可空：空表示棚区级调整；非空表示只作用于该批次所在棚位 */
    private Long batchId;
    private Integer brightnessPct;
    private Integer ratioRedPct;
    private Integer ratioGreenPct;
    private Integer ratioBluePct;
    private Integer ratioWhitePct;
    private LocalDateTime startAt;
    /** 必填：临时调整的强制结束时间，到期自动恢复配方 */
    private LocalDateTime endAt;
    /** 必填：审批人操作者 id */
    private Long approverId;
    private String approverName;
    private String reason;
    /** ACTIVE / EXPIRED / REVOKED */
    private String status;
    private LocalDateTime restoredAt;
    private Long operatorId;
    private String operatorName;
}
