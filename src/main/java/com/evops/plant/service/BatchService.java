package com.evops.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.CurrentOperator;
import com.evops.plant.domain.PfStatus;
import com.evops.plant.dto.CareLogRequest;
import com.evops.plant.dto.RelocateRequest;
import com.evops.plant.dto.SowRequest;
import com.evops.plant.entity.Batch;
import com.evops.plant.entity.CareLog;
import com.evops.plant.entity.Greenhouse;
import com.evops.plant.entity.Relocation;
import com.evops.plant.entity.Tray;
import com.evops.plant.entity.Variety;
import com.evops.plant.entity.Zone;
import com.evops.plant.mapper.BatchMapper;
import com.evops.plant.mapper.CareLogMapper;
import com.evops.plant.mapper.GreenhouseMapper;
import com.evops.plant.mapper.RelocationMapper;
import com.evops.plant.mapper.TrayMapper;
import com.evops.plant.mapper.VarietyMapper;
import com.evops.plant.mapper.ZoneMapper;
import com.evops.plant.support.NumberGenerator;
import com.evops.plant.support.OperatorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 种植批次：播种、养护、转棚留痕。批次号由序列生成 + 唯一约束双重保证唯一。 */
@Service
public class BatchService {

    private static final Logger log = LoggerFactory.getLogger(BatchService.class);

    /** 定时任务创建的"台账起步"批次使用固定编号，便于幂等核对。 */
    public static final String STARTUP_BATCH_NO = "PFB-STARTUP-0001";
    public static final String STARTUP_FLAG_YES = "Y";

    private static final Set<String> CARE_TYPES = new HashSet<>(
            Arrays.asList("LIGHT", "NUTRIENT", "EQUIPMENT", "OTHER"));

    private final BatchMapper batchMapper;
    private final VarietyMapper varietyMapper;
    private final TrayMapper trayMapper;
    private final GreenhouseMapper greenhouseMapper;
    private final ZoneMapper zoneMapper;
    private final RelocationMapper relocationMapper;
    private final CareLogMapper careLogMapper;
    private final NumberGenerator numberGenerator;
    private final OperatorResolver operatorResolver;

    public BatchService(BatchMapper batchMapper, VarietyMapper varietyMapper, TrayMapper trayMapper,
                        GreenhouseMapper greenhouseMapper, ZoneMapper zoneMapper,
                        RelocationMapper relocationMapper, CareLogMapper careLogMapper,
                        NumberGenerator numberGenerator, OperatorResolver operatorResolver) {
        this.batchMapper = batchMapper;
        this.varietyMapper = varietyMapper;
        this.trayMapper = trayMapper;
        this.greenhouseMapper = greenhouseMapper;
        this.zoneMapper = zoneMapper;
        this.relocationMapper = relocationMapper;
        this.careLogMapper = careLogMapper;
        this.numberGenerator = numberGenerator;
        this.operatorResolver = operatorResolver;
    }

    /** 播种建档：生成唯一批次号，关联品种、播种盘、计划采收日。 */
    @Transactional
    public Batch sow(SowRequest request) {
        Variety variety = varietyMapper.selectById(request.getVarietyId());
        if (variety == null) {
            throw new IllegalArgumentException("品种不存在: " + request.getVarietyId());
        }
        Tray tray = trayMapper.selectById(request.getTrayId());
        if (tray == null) {
            throw new IllegalArgumentException("播种盘不存在: " + request.getTrayId());
        }
        if (!PfStatus.TRAY_IDLE.equals(tray.getStatus())) {
            throw new IllegalStateException("播种盘已被占用，无法重复播种: " + tray.getTrayCode());
        }
        Greenhouse gh = greenhouseMapper.selectById(request.getGreenhouseId());
        if (gh == null) {
            throw new IllegalArgumentException("温室不存在: " + request.getGreenhouseId());
        }
        if (request.getZoneId() != null) {
            Zone zone = zoneMapper.selectById(request.getZoneId());
            if (zone == null || !zone.getGreenhouseId().equals(request.getGreenhouseId())) {
                throw new IllegalArgumentException("分区不存在或不属于该温室: " + request.getZoneId());
            }
        }
        if (request.getPlannedHarvestDate().isBefore(request.getSowDate())) {
            throw new IllegalArgumentException("计划采收日不能早于播种日期");
        }

        Batch batch = new Batch();
        batch.setBatchNo(numberGenerator.nextBatchNo());
        batch.setVarietyId(variety.getId());
        batch.setTrayId(tray.getId());
        batch.setGreenhouseId(gh.getId());
        batch.setZoneId(request.getZoneId());
        batch.setSowDate(request.getSowDate());
        batch.setPlannedHarvestDate(request.getPlannedHarvestDate());
        batch.setStatus(PfStatus.BATCH_SOWN);
        batch.setStartupFlag("N");
        batch.setRemark(request.getRemark());
        try {
            batchMapper.insert(batch);
        } catch (DuplicateKeyException e) {
            // 唯一约束兜底：批次号冲突时不允许产生第二条
            throw new IllegalStateException("批次号冲突，请重试: " + batch.getBatchNo());
        }

        tray.setStatus(PfStatus.TRAY_IN_USE);
        trayMapper.updateById(tray);
        return batch;
    }

    /** 养护记录登记（光照/营养液循环/设备巡检）；首次养护把批次推进到养护中。 */
    @Transactional
    public CareLog addCareLog(Long batchId, CareLogRequest request) {
        Batch batch = requireActiveBatch(batchId);
        String careType = request.getCareType() == null ? null : request.getCareType().toUpperCase();
        if (!CARE_TYPES.contains(careType)) {
            throw new IllegalArgumentException("养护类型必须为 LIGHT / NUTRIENT / EQUIPMENT / OTHER");
        }
        Long operatorId = CurrentOperator.id();
        CareLog careLog = new CareLog();
        careLog.setBatchId(batch.getId());
        careLog.setCareType(careType);
        careLog.setContent(request.getContent());
        careLog.setReading(request.getReading());
        careLog.setUnit(request.getUnit());
        careLog.setLogTime(request.getLogTime() == null ? LocalDateTime.now() : request.getLogTime());
        careLog.setOperatorId(operatorId);
        careLog.setOperatorName(operatorResolver.nameOf(operatorId));
        careLogMapper.insert(careLog);

        if (PfStatus.BATCH_SOWN.equals(batch.getStatus()) || PfStatus.BATCH_MOVED.equals(batch.getStatus())) {
            batch.setStatus(PfStatus.BATCH_GROWING);
            batchMapper.updateById(batch);
        }
        return careLog;
    }

    /** 转棚：只追加留痕，记录从哪到哪、原因与操作者，并更新批次当前所在棚/分区。 */
    @Transactional
    public Relocation relocate(Long batchId, RelocateRequest request) {
        Batch batch = requireActiveBatch(batchId);
        Greenhouse toGh = greenhouseMapper.selectById(request.getToGreenhouseId());
        if (toGh == null) {
            throw new IllegalArgumentException("目标温室不存在: " + request.getToGreenhouseId());
        }
        if (request.getToZoneId() != null) {
            Zone toZone = zoneMapper.selectById(request.getToZoneId());
            if (toZone == null || !toZone.getGreenhouseId().equals(request.getToGreenhouseId())) {
                throw new IllegalArgumentException("目标分区不存在或不属于目标温室: " + request.getToZoneId());
            }
        }
        if (request.getToGreenhouseId().equals(batch.getGreenhouseId())
                && java.util.Objects.equals(request.getToZoneId(), batch.getZoneId())) {
            throw new IllegalStateException("目标棚位与当前棚位相同，无需转棚");
        }

        Long count = relocationMapper.selectCount(new QueryWrapper<Relocation>()
                .eq("batch_id", batchId));
        Relocation relocation = new Relocation();
        relocation.setBatchId(batch.getId());
        relocation.setSeqNo(count == null ? 1 : count.intValue() + 1);
        relocation.setFromGreenhouseId(batch.getGreenhouseId());
        relocation.setFromZoneId(batch.getZoneId());
        relocation.setToGreenhouseId(toGh.getId());
        relocation.setToZoneId(request.getToZoneId());
        relocation.setReason(request.getReason());
        Long operatorId = CurrentOperator.id();
        relocation.setOperatorId(operatorId);
        relocation.setOperatorName(operatorResolver.nameOf(operatorId));
        relocation.setRelocatedAt(LocalDateTime.now());
        try {
            relocationMapper.insert(relocation);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("转棚流水冲突，请重试");
        }

        batch.setGreenhouseId(toGh.getId());
        batch.setZoneId(request.getToZoneId());
        batch.setStatus(PfStatus.BATCH_MOVED);
        batchMapper.updateById(batch);
        return relocation;
    }

    public Batch getBatch(Long batchId) {
        Batch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("种植批次不存在: " + batchId);
        }
        return batch;
    }

    public List<Batch> listBatches(String status) {
        return batchMapper.selectList(new QueryWrapper<Batch>()
                .eq(status != null, "status", status)
                .orderByDesc("id"));
    }

    public List<Relocation> listRelocations(Long batchId) {
        return relocationMapper.selectList(new QueryWrapper<Relocation>()
                .eq("batch_id", batchId)
                .orderByAsc("seq_no"));
    }

    public List<CareLog> listCareLogs(Long batchId) {
        return careLogMapper.selectList(new QueryWrapper<CareLog>()
                .eq("batch_id", batchId)
                .orderByDesc("log_time"));
    }

    /**
     * 定时任务入口：幂等确保"植物工厂种植批次台账起步"批次存在且仅一条。
     * 重启反复执行不会多写记录；操作者为系统(0)，不丢操作者。
     *
     * @return 已存在返回既有批次，首次执行返回新建批次
     */
    @Transactional
    public Batch ensureStartupBatch() {
        Batch existing = batchMapper.selectOne(new QueryWrapper<Batch>()
                .eq("startup_flag", STARTUP_FLAG_YES)
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("起步批次已存在，跳过创建: batchNo={}, id={}", existing.getBatchNo(), existing.getId());
            return existing;
        }

        Variety variety = varietyMapper.selectOne(new QueryWrapper<Variety>()
                .eq("variety_code", "V001").last("LIMIT 1"));
        Tray tray = trayMapper.selectOne(new QueryWrapper<Tray>()
                .eq("tray_code", "T0001").last("LIMIT 1"));
        Greenhouse gh = greenhouseMapper.selectOne(new QueryWrapper<Greenhouse>()
                .eq("gh_code", "GH01").last("LIMIT 1"));
        Zone zone = zoneMapper.selectOne(new QueryWrapper<Zone>()
                .eq("greenhouse_id", gh.getId()).eq("zone_code", "A").last("LIMIT 1"));

        Batch batch = new Batch();
        batch.setBatchNo(STARTUP_BATCH_NO);
        batch.setVarietyId(variety.getId());
        batch.setTrayId(tray.getId());
        batch.setGreenhouseId(gh.getId());
        batch.setZoneId(zone.getId());
        batch.setSowDate(java.time.LocalDate.now());
        batch.setPlannedHarvestDate(java.time.LocalDate.now().plusDays(variety.getGrowDays()));
        batch.setStatus(PfStatus.BATCH_SOWN);
        batch.setStartupFlag(STARTUP_FLAG_YES);
        batch.setRemark("植物工厂种植批次台账起步");
        try {
            batchMapper.insert(batch);
        } catch (DuplicateKeyException e) {
            // 并发/重启竞争下另一线程已创建：回查既有记录，绝不多写
            Batch concurrent = batchMapper.selectOne(new QueryWrapper<Batch>()
                    .eq("startup_flag", STARTUP_FLAG_YES).last("LIMIT 1"));
            log.info("起步批次已被并发创建，复用既有记录: batchNo={}", concurrent.getBatchNo());
            return concurrent;
        }

        tray.setStatus(PfStatus.TRAY_IN_USE);
        trayMapper.updateById(tray);
        log.info("起步批次已创建: batchNo={}, id={}, operator={}",
                batch.getBatchNo(), batch.getId(), CurrentOperator.id());
        return batch;
    }

    private Batch requireActiveBatch(Long batchId) {
        Batch batch = getBatch(batchId);
        if (PfStatus.BATCH_HARVESTED.equals(batch.getStatus())) {
            throw new IllegalStateException("批次已采收，不能继续该操作: " + batch.getBatchNo());
        }
        return batch;
    }
}
