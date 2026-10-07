package com.evops.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.plant.domain.LightParamChecks;
import com.evops.plant.domain.LightStatus;
import com.evops.plant.domain.LightTimeRules;
import com.evops.plant.dto.LightOverrideRequest;
import com.evops.plant.entity.LightOverride;
import com.evops.plant.entity.Operator;
import com.evops.plant.entity.Zone;
import com.evops.plant.mapper.LightOverrideMapper;
import com.evops.plant.mapper.OperatorMapper;
import com.evops.plant.mapper.ZoneMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 人工临时调整：必须带结束时间与审批人（数据库 NOT NULL + 本服务双重校验）。
 * 窗口 [start_time, end_time) 内优先于配方生效，到点自动失效；
 * 切换同样遵守整点生效口径——窗口内不含整点的调整永远不会生效，直接拒绝。
 */
@Service
public class LightOverrideService {

    private final LightOverrideMapper overrideMapper;
    private final ZoneMapper zoneMapper;
    private final OperatorMapper operatorMapper;

    public LightOverrideService(LightOverrideMapper overrideMapper, ZoneMapper zoneMapper,
                                OperatorMapper operatorMapper) {
        this.overrideMapper = overrideMapper;
        this.zoneMapper = zoneMapper;
        this.operatorMapper = operatorMapper;
    }

    @Transactional
    public LightOverride create(LightOverrideRequest request) {
        Zone zone = zoneMapper.selectById(request.getZoneId());
        if (zone == null) {
            throw new IllegalArgumentException("棚区不存在: " + request.getZoneId());
        }
        LightParamChecks.checkSpectrum(request.getSpectrumRed(), request.getSpectrumBlue(),
                request.getSpectrumFarRed());
        LightParamChecks.checkBrightness(request.getBrightnessPpfd());
        LightParamChecks.checkPeriod(request.getLightOn(), request.getLightOff());

        // 结束时间与审批人为硬性要求，服务层再校验一次（不依赖 DTO 注解单点）
        if (request.getEndTime() == null) {
            throw new IllegalArgumentException("人工临时调整必须带结束时间");
        }
        if (request.getApproverId() == null) {
            throw new IllegalArgumentException("人工临时调整必须带审批人");
        }
        LocalDateTime start = request.getStartTime() == null ? LocalDateTime.now() : request.getStartTime();
        LocalDateTime end = request.getEndTime();
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }
        if (!LightTimeRules.effectiveHour(start).isBefore(end)) {
            throw new IllegalArgumentException("调整窗口内不含任何整点，切换不会生效，请延长结束时间");
        }
        Operator approver = operatorMapper.selectById(request.getApproverId());
        if (approver == null || !"ACTIVE".equals(approver.getStatus())) {
            throw new IllegalArgumentException("审批人不存在或已停用: " + request.getApproverId());
        }

        expireOverdue(LocalDateTime.now());
        Long active = overrideMapper.selectCount(new QueryWrapper<LightOverride>()
                .eq("zone_id", zone.getId())
                .eq("status", LightStatus.OVERRIDE_ACTIVE));
        if (active != null && active > 0) {
            throw new IllegalStateException("该棚区已有生效中的临时调整，请先撤销: " + zone.getZoneCode());
        }

        LightOverride override = new LightOverride();
        override.setZoneId(zone.getId());
        override.setSpectrumRed(request.getSpectrumRed());
        override.setSpectrumBlue(request.getSpectrumBlue());
        override.setSpectrumFarRed(request.getSpectrumFarRed());
        override.setBrightnessPpfd(request.getBrightnessPpfd());
        override.setLightOn(request.getLightOn());
        override.setLightOff(request.getLightOff());
        override.setStartTime(start);
        override.setEndTime(end);
        override.setApproverId(approver.getId());
        override.setApproverName(approver.getName());
        override.setReason(request.getReason());
        override.setStatus(LightStatus.OVERRIDE_ACTIVE);
        overrideMapper.insert(override);
        return override;
    }

    /** 撤销：仅生效中的调整可撤销。 */
    @Transactional
    public LightOverride revoke(Long overrideId) {
        LightOverride override = overrideMapper.selectById(overrideId);
        if (override == null) {
            throw new IllegalArgumentException("临时调整不存在: " + overrideId);
        }
        if (!LightStatus.OVERRIDE_ACTIVE.equals(override.getStatus())) {
            throw new IllegalStateException("仅生效中的临时调整可撤销，当前状态: " + override.getStatus());
        }
        override.setStatus(LightStatus.OVERRIDE_REVOKED);
        overrideMapper.updateById(override);
        return override;
    }

    /** 过期归置：窗口已结束的 ACTIVE 调整置为 EXPIRED（解析口径本身按窗口判断，此处仅状态归置）。 */
    @Transactional
    public int expireOverdue(LocalDateTime now) {
        List<LightOverride> overdue = overrideMapper.selectList(new QueryWrapper<LightOverride>()
                .eq("status", LightStatus.OVERRIDE_ACTIVE)
                .le("end_time", now));
        for (LightOverride o : overdue) {
            o.setStatus(LightStatus.OVERRIDE_EXPIRED);
            overrideMapper.updateById(o);
        }
        return overdue.size();
    }

    /** 棚区在指定时刻生效中的临时调整（窗口左闭右开，取最新一条）。 */
    public LightOverride activeAt(Long zoneId, LocalDateTime t) {
        return overrideMapper.selectOne(new QueryWrapper<LightOverride>()
                .eq("zone_id", zoneId)
                .eq("status", LightStatus.OVERRIDE_ACTIVE)
                .le("start_time", t)
                .gt("end_time", t)
                .orderByDesc("id")
                .last("LIMIT 1"));
    }

    public List<LightOverride> list(Long zoneId, String status) {
        return overrideMapper.selectList(new QueryWrapper<LightOverride>()
                .eq(zoneId != null, "zone_id", zoneId)
                .eq(status != null, "status", status)
                .orderByDesc("id"));
    }
}
