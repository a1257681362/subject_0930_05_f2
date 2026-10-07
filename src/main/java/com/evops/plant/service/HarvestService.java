package com.evops.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.CurrentOperator;
import com.evops.plant.domain.PfStatus;
import com.evops.plant.dto.HarvestRequest;
import com.evops.plant.entity.Batch;
import com.evops.plant.entity.HarvestOrder;
import com.evops.plant.entity.Tray;
import com.evops.plant.mapper.BatchMapper;
import com.evops.plant.mapper.HarvestOrderMapper;
import com.evops.plant.mapper.TrayMapper;
import com.evops.plant.support.NumberGenerator;
import com.evops.plant.support.OperatorResolver;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 采收单：记录等级、重量与去向；采收单号序列生成 + 唯一约束双重保证唯一。 */
@Service
public class HarvestService {

    private static final Set<String> GRADES = new HashSet<>(Arrays.asList("A", "B", "C"));

    private final HarvestOrderMapper harvestOrderMapper;
    private final BatchMapper batchMapper;
    private final TrayMapper trayMapper;
    private final NumberGenerator numberGenerator;
    private final OperatorResolver operatorResolver;

    public HarvestService(HarvestOrderMapper harvestOrderMapper, BatchMapper batchMapper,
                          TrayMapper trayMapper, NumberGenerator numberGenerator,
                          OperatorResolver operatorResolver) {
        this.harvestOrderMapper = harvestOrderMapper;
        this.batchMapper = batchMapper;
        this.trayMapper = trayMapper;
        this.numberGenerator = numberGenerator;
        this.operatorResolver = operatorResolver;
    }

    @Transactional
    public HarvestOrder createHarvest(Long batchId, HarvestRequest request) {
        Batch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("种植批次不存在: " + batchId);
        }
        if (PfStatus.BATCH_HARVESTED.equals(batch.getStatus())) {
            throw new IllegalStateException("批次已采收，不能重复采收: " + batch.getBatchNo());
        }
        String grade = request.getGrade() == null ? null : request.getGrade().toUpperCase();
        if (!GRADES.contains(grade)) {
            throw new IllegalArgumentException("采收等级必须为 A / B / C");
        }

        Long operatorId = CurrentOperator.id();
        HarvestOrder order = new HarvestOrder();
        order.setHarvestNo(numberGenerator.nextHarvestNo());
        order.setBatchId(batch.getId());
        order.setGrade(grade);
        order.setWeightKg(request.getWeightKg());
        order.setDestination(request.getDestination());
        order.setHarvestDate(request.getHarvestDate());
        order.setQcStatus(PfStatus.QC_PENDING);
        order.setOperatorId(operatorId);
        order.setOperatorName(operatorResolver.nameOf(operatorId));
        order.setRemark(request.getRemark());
        try {
            harvestOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("采收单号冲突，请重试: " + order.getHarvestNo());
        }

        batch.setStatus(PfStatus.BATCH_HARVESTED);
        batch.setActualHarvestDate(request.getHarvestDate());
        batchMapper.updateById(batch);

        Tray tray = trayMapper.selectById(batch.getTrayId());
        if (tray != null) {
            tray.setStatus(PfStatus.TRAY_IDLE);
            trayMapper.updateById(tray);
        }
        return order;
    }

    public HarvestOrder getHarvest(Long harvestId) {
        HarvestOrder order = harvestOrderMapper.selectById(harvestId);
        if (order == null) {
            throw new IllegalArgumentException("采收单不存在: " + harvestId);
        }
        return order;
    }

    public List<HarvestOrder> listByBatch(Long batchId) {
        return harvestOrderMapper.selectList(new QueryWrapper<HarvestOrder>()
                .eq(batchId != null, "batch_id", batchId)
                .orderByDesc("id"));
    }
}
