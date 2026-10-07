package com.evops.plant.service;

import com.evops.common.CurrentOperator;
import com.evops.plant.domain.PfStatus;
import com.evops.plant.dto.QualityCheckRequest;
import com.evops.plant.entity.HarvestOrder;
import com.evops.plant.entity.QualityCheck;
import com.evops.plant.mapper.HarvestOrderMapper;
import com.evops.plant.mapper.QualityCheckMapper;
import com.evops.plant.support.OperatorResolver;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 质检：一张采收单至多一条结论，结论回写采收单质检状态。 */
@Service
public class QualityCheckService {

    private final QualityCheckMapper qualityCheckMapper;
    private final HarvestOrderMapper harvestOrderMapper;
    private final OperatorResolver operatorResolver;

    public QualityCheckService(QualityCheckMapper qualityCheckMapper,
                               HarvestOrderMapper harvestOrderMapper,
                               OperatorResolver operatorResolver) {
        this.qualityCheckMapper = qualityCheckMapper;
        this.harvestOrderMapper = harvestOrderMapper;
        this.operatorResolver = operatorResolver;
    }

    @Transactional
    public QualityCheck check(Long harvestId, QualityCheckRequest request) {
        HarvestOrder order = harvestOrderMapper.selectById(harvestId);
        if (order == null) {
            throw new IllegalArgumentException("采收单不存在: " + harvestId);
        }
        String result = request.getResult() == null ? null : request.getResult().toUpperCase();
        if (!PfStatus.QC_PASSED.equals(result) && !PfStatus.QC_REJECTED.equals(result)) {
            throw new IllegalArgumentException("质检结论必须为 PASSED / REJECTED");
        }

        Long inspectorId = CurrentOperator.id();
        QualityCheck qc = new QualityCheck();
        qc.setHarvestId(order.getId());
        qc.setResult(result);
        qc.setInspectorId(inspectorId);
        qc.setInspectorName(operatorResolver.nameOf(inspectorId));
        qc.setCheckTime(LocalDateTime.now());
        qc.setNote(request.getNote());
        try {
            qualityCheckMapper.insert(qc);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("该采收单已完成质检，不能重复质检: " + order.getHarvestNo());
        }

        order.setQcStatus(result);
        harvestOrderMapper.updateById(order);
        return qc;
    }

    public QualityCheck getByHarvest(Long harvestId) {
        return qualityCheckMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<QualityCheck>()
                .eq("harvest_id", harvestId).last("LIMIT 1"));
    }
}
