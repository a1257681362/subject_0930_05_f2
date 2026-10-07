package com.evops.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.CurrentOperator;
import com.evops.plant.domain.BatchGrowth;
import com.evops.plant.domain.GrowthStage;
import com.evops.plant.domain.GrowthStageRule;
import com.evops.plant.domain.PfStatus;
import com.evops.plant.domain.TimeSlotMatcher;
import com.evops.plant.dto.LightOverrideRequest;
import com.evops.plant.dto.LightRecipeDetail;
import com.evops.plant.entity.Batch;
import com.evops.plant.entity.BatchStageBinding;
import com.evops.plant.entity.LightDispatch;
import com.evops.plant.entity.LightDispatchRetry;
import com.evops.plant.entity.LightManualOverride;
import com.evops.plant.entity.LightRecipe;
import com.evops.plant.entity.LightRecipeSlot;
import com.evops.plant.entity.LightRecipeStage;
import com.evops.plant.gateway.LightCommand;
import com.evops.plant.gateway.LightDeviceException;
import com.evops.plant.gateway.LightDeviceGateway;
import com.evops.plant.mapper.BatchMapper;
import com.evops.plant.mapper.BatchStageBindingMapper;
import com.evops.plant.mapper.GreenhouseMapper;
import com.evops.plant.mapper.LightDispatchMapper;
import com.evops.plant.mapper.LightDispatchRetryMapper;
import com.evops.plant.mapper.LightManualOverrideMapper;
import com.evops.plant.mapper.LightRecipeMapper;
import com.evops.plant.support.OperatorResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 光配方整点下发服务。
 *
 * <p>核心口径：
 * <ul>
 *   <li><b>整点生效</b>：每小时 0 分按棚区下发一次；配方发布 effective_at 为下一整点，
 *       planned_at 取整点，幂等键（温室+分区+整点）保证重复触发/重启不多发；</li>
 *   <li><b>版本只影响未开始阶段</b>：批次"进入"阶段时才绑定当时生效配方（t_pf_batch_stage_binding
 *       快照），已开始阶段即使发布了新版本也沿用冻结版本，直至本阶段结束；</li>
 *   <li><b>阶段同一口径归属</b>：进度按 BatchGrowth 计算、阶段按 GrowthStageRule 左闭右开判定、
 *       时段按 TimeSlotMatcher 左闭右开（含跨午夜）匹配，全链路只有这一处口径；</li>
 *   <li><b>历史执行保留快照</b>：下发流水 payload_json 冻结当时参数，重试也按快照重发；</li>
 *   <li><b>失败设备重试</b>：网关抛异常的棚区进 t_pf_light_dispatch_retry，退避重试至成功或放弃；</li>
 *   <li><b>人工临时调整</b>：必须带结束时间与审批人（DTO 强校验），有效期内覆盖配方，
 *       到期整点自动恢复配方。</li>
 * </ul>
 */
@Service
public class LightDispatchService {

    private static final Logger log = LoggerFactory.getLogger(LightDispatchService.class);

    /** 最多重试次数，超过置 GIVEN_UP，需人工介入。 */
    public static final int MAX_ATTEMPTS = 5;
    /** 重试退避基数（分钟）：1,2,4,8,16，封顶 60。 */
    private static final long BACKOFF_BASE_MINUTES = 1L;
    private static final long BACKOFF_CAP_MINUTES = 60L;

    private final BatchMapper batchMapper;
    private final GreenhouseMapper greenhouseMapper;
    private final BatchStageBindingMapper bindingMapper;
    private final LightRecipeMapper recipeMapper;
    private final LightDispatchMapper dispatchMapper;
    private final LightDispatchRetryMapper retryMapper;
    private final LightManualOverrideMapper overrideMapper;
    private final LightRecipeService recipeService;
    private final LightDeviceGateway deviceGateway;
    private final OperatorResolver operatorResolver;
    private final ObjectMapper objectMapper;

    public LightDispatchService(BatchMapper batchMapper, GreenhouseMapper greenhouseMapper,
                                BatchStageBindingMapper bindingMapper, LightRecipeMapper recipeMapper,
                                LightDispatchMapper dispatchMapper, LightDispatchRetryMapper retryMapper,
                                LightManualOverrideMapper overrideMapper, LightRecipeService recipeService,
                                LightDeviceGateway deviceGateway, OperatorResolver operatorResolver,
                                ObjectMapper objectMapper) {
        this.batchMapper = batchMapper;
        this.greenhouseMapper = greenhouseMapper;
        this.bindingMapper = bindingMapper;
        this.recipeMapper = recipeMapper;
        this.dispatchMapper = dispatchMapper;
        this.retryMapper = retryMapper;
        this.overrideMapper = overrideMapper;
        this.recipeService = recipeService;
        this.deviceGateway = deviceGateway;
        this.operatorResolver = operatorResolver;
        this.objectMapper = objectMapper;
    }

    // ============================================================ 整点巡检下发

    /**
     * 整点巡检：由定时任务每小时 0 分触发（启动时也补跑当前整点）。
     *
     * @return 本次实际产生的下发记录条数（成功+失败；幂等跳过不计）
     */
    public int dispatchTick() {
        return dispatchTick(LocalDateTime.now());
    }

    /** 可注入时钟的重载（临界时间测试用）。 */
    @Transactional
    public int dispatchTick(LocalDateTime nowRaw) {
        LocalDateTime hour = nowRaw.truncatedTo(ChronoUnit.HOURS);
        Long operatorId = CurrentOperator.id();
        String operatorName = operatorResolver.nameOf(operatorId);

        // 1) 到期的人工调整：到点整点结束，恢复配方
        expireOverrides(hour);

        // 2) 未采收批次按棚区归组（同一棚区灯具共享，取最早播种批次为代表）
        Map<ZoneKey, List<Batch>> zoneBatches = groupActiveBatchesByZone();
        int created = 0;
        for (Map.Entry<ZoneKey, List<Batch>> entry : zoneBatches.entrySet()) {
            ZoneKey key = entry.getKey();
            List<Batch> batches = entry.getValue();
            Batch representative = batches.stream()
                    .min(Comparator.comparing(Batch::getSowDate).thenComparing(Batch::getId))
                    .orElseThrow(IllegalStateException::new);
            try {
                boolean sent = dispatchOneZone(key, representative, hour, operatorId, operatorName);
                if (sent) {
                    created++;
                }
            } catch (Exception e) {
                // 单棚区失败不影响其他棚区；设备失败本身已入重试队列，这里兜底记录
                log.error("[光配方下发] 棚区处理失败 gh={} zone={}: {}",
                        key.greenhouseId, key.zoneId, e.getMessage(), e);
            }
        }
        log.info("[光配方下发] 整点巡检完成 plannedAt={} 棚区数={} 新下发={}", hour, zoneBatches.size(), created);
        return created;
    }

    private boolean dispatchOneZone(ZoneKey key, Batch batch, LocalDateTime hour,
                                    Long operatorId, String operatorName) {
        // 幂等：同一棚区同一整点只下发一条（覆盖配方/调整/恢复全部情形）
        QueryWrapper<LightDispatch> idempotent = new QueryWrapper<LightDispatch>()
                .eq("greenhouse_id", key.greenhouseId);
        if (key.zoneId == null) {
            idempotent.isNull("zone_id");
        } else {
            idempotent.eq("zone_id", key.zoneId);
        }
        idempotent.eq("planned_at", hour);
        Long exists = dispatchMapper.selectCount(idempotent);
        if (exists != null && exists > 0) {
            log.info("[光配方下发] 棚区 {} 整点 {} 已下发，幂等跳过", key, hour);
            return false;
        }
        if (greenhouseMapper.selectById(key.greenhouseId) == null) {
            log.warn("[光配方下发] 温室不存在，跳过: {}", key.greenhouseId);
            return false;
        }

        // 3) 人工临时调整在有效期内优先于配方（批次级 > 分区级 > 温室级）
        LightManualOverride override = findActiveOverride(key, batch.getId(), hour);

        LightPayload payload;
        if (override != null) {
            payload = payloadFromOverride(override, hour);
        } else {
            // 4) 推进/取入阶段绑定（新版本只影响未开始阶段），用冻结版本匹配时段
            LightRecipe frozenRecipe = ensureStageBinding(batch, hour);
            if (frozenRecipe == null) {
                log.warn("[光配方下发] 品种 {} 在 {} 无生效配方，批次 {} 跳过",
                        batch.getVarietyId(), hour, batch.getBatchNo());
                return false;
            }
            BatchStageBinding binding = bindingMapper.selectOne(new QueryWrapper<BatchStageBinding>()
                    .eq("batch_id", batch.getId())
                    .eq("status", PfStatus.BINDING_ACTIVE)
                    .last("LIMIT 1"));
            payload = payloadFromRecipe(frozenRecipe, binding.getStageCode(), hour);
            // 本整点刚好有调整到期恢复 → 标记来源为 RESTORE
            if (restoredThisHour(key, batch.getId(), hour)) {
                payload.setSource(PfStatus.SOURCE_RESTORE);
            }
        }

        LightDispatch dispatch = newDispatch(key, batch, hour, payload, operatorId, operatorName);
        dispatchMapper.insert(dispatch);
        deliver(dispatch, payload);
        return true;
    }

    // ============================================================ 阶段绑定（版本冻结）

    /**
     * 按统一口径计算批次当前阶段；首次进入/阶段推进时绑定<b>当前生效版本</b>，
     * 阶段未变则沿用既有冻结版本——新版本因此只影响未开始的阶段。
     *
     * @return 当前阶段应执行的冻结配方；品种无任何生效配方时返回 null
     */
    private LightRecipe ensureStageBinding(Batch batch, LocalDateTime hour) {
        int progress = BatchGrowth.progress(batch.getSowDate(), batch.getPlannedHarvestDate(),
                hour.toLocalDate());
        LightRecipe current = recipeService.effectiveRecipe(batch.getVarietyId(), hour);
        if (current == null) {
            return null;
        }
        GrowthStage target = resolveStage(current, progress);

        BatchStageBinding active = bindingMapper.selectOne(new QueryWrapper<BatchStageBinding>()
                .eq("batch_id", batch.getId())
                .eq("status", PfStatus.BINDING_ACTIVE)
                .last("LIMIT 1"));

        if (active == null) {
            enterStage(batch, target, current, progress, hour);
            return current;
        }
        GrowthStage activeStage = GrowthStage.ofCode(active.getStageCode());
        if (activeStage == target) {
            // 阶段已开始：冻结版本不变，新发布版本不溯及本阶段
            return recipeMapper.selectById(active.getRecipeId());
        }
        if (target.order() <= activeStage.order()) {
            // 阶段不可能回退（数据异常时保持冻结版本，不重开已结束阶段）
            log.warn("[光配方下发] 批次 {} 阶段口径回退 {}→{}，沿用既有绑定",
                    batch.getBatchNo(), activeStage, target);
            return recipeMapper.selectById(active.getRecipeId());
        }
        // 推进到新阶段：旧绑定结束（历史快照），新阶段绑定当前生效版本
        active.setStatus(PfStatus.BINDING_FINISHED);
        active.setLeftAt(hour);
        bindingMapper.updateById(active);
        enterStage(batch, target, current, progress, hour);
        return current;
    }

    private void enterStage(Batch batch, GrowthStage stage, LightRecipe recipe,
                            int progress, LocalDateTime hour) {
        BatchStageBinding binding = new BatchStageBinding();
        binding.setBatchId(batch.getId());
        binding.setStageCode(stage.name());
        binding.setRecipeId(recipe.getId());
        binding.setRecipeVersionNo(recipe.getVersionNo());
        binding.setProgressAtEntry(progress);
        binding.setEnteredAt(hour);
        binding.setStatus(PfStatus.BINDING_ACTIVE);
        bindingMapper.insert(binding);
        log.info("[光配方下发] 批次 {} 进入阶段 {}，冻结配方 v{}（progress={}）",
                batch.getBatchNo(), stage, recipe.getVersionNo(), progress);
    }

    private GrowthStage resolveStage(LightRecipe recipe, int progress) {
        LightRecipeDetail detail = recipeService.loadDetail(recipe);
        List<GrowthStageRule.StageRange> ranges = new ArrayList<>();
        for (LightRecipeDetail.StageView view : detail.getStages()) {
            LightRecipeStage s = view.getStage();
            ranges.add(new GrowthStageRule.StageRange(GrowthStage.ofCode(s.getStageCode()),
                    s.getFromProgress(), s.getToProgress()));
        }
        return GrowthStageRule.resolve(progress, ranges);
    }

    // ============================================================ 快照构造

    private LightPayload payloadFromRecipe(LightRecipe recipe, String stageCode, LocalDateTime hour) {
        LightRecipeDetail detail = recipeService.loadDetail(recipe);
        LightRecipeDetail.StageView stageView = detail.getStages().stream()
                .filter(v -> v.getStage().getStageCode().equals(stageCode))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("冻结配方缺少阶段: " + stageCode));

        int minuteOfDay = hour.getHour() * 60;
        List<TimeSlotMatcher.Slot> slots = new ArrayList<>();
        for (LightRecipeSlot s : stageView.getSlots()) {
            slots.add(new TimeSlotMatcher.Slot(s.getStartMinute(), s.getEndMinute()));
        }
        TimeSlotMatcher.validateFullDayCoverage(slots);
        TimeSlotMatcher.Slot hit = TimeSlotMatcher.match(minuteOfDay, slots);
        LightRecipeSlot slot = stageView.getSlots().stream()
                .filter(s -> s.getStartMinute() == hit.getStartMinute()
                        && s.getEndMinute() == hit.getEndMinute())
                .findFirst()
                .orElseThrow(IllegalStateException::new);

        LightPayload payload = new LightPayload();
        payload.setRecipeId(recipe.getId());
        payload.setRecipeVersionNo(recipe.getVersionNo());
        payload.setStageCode(stageCode);
        payload.setSlotStartMinute(slot.getStartMinute());
        payload.setBrightnessPct(slot.getBrightnessPct());
        payload.setRatioRedPct(slot.getRatioRedPct());
        payload.setRatioGreenPct(slot.getRatioGreenPct());
        payload.setRatioBluePct(slot.getRatioBluePct());
        payload.setRatioWhitePct(slot.getRatioWhitePct());
        payload.setSource(PfStatus.SOURCE_RECIPE);
        payload.setSnapshotAt(hour);
        return payload;
    }

    private LightPayload payloadFromOverride(LightManualOverride override, LocalDateTime hour) {
        LightPayload payload = new LightPayload();
        payload.setStageCode(null);
        payload.setBrightnessPct(override.getBrightnessPct());
        payload.setRatioRedPct(override.getRatioRedPct());
        payload.setRatioGreenPct(override.getRatioGreenPct());
        payload.setRatioBluePct(override.getRatioBluePct());
        payload.setRatioWhitePct(override.getRatioWhitePct());
        payload.setSource(PfStatus.SOURCE_OVERRIDE);
        payload.setSnapshotAt(hour);
        payload.setApproverId(override.getApproverId());
        payload.setReason(override.getReason());
        return payload;
    }

    private LightDispatch newDispatch(ZoneKey key, Batch batch, LocalDateTime hour,
                                      LightPayload payload, Long operatorId, String operatorName) {
        LightDispatch dispatch = new LightDispatch();
        dispatch.setGreenhouseId(key.greenhouseId);
        dispatch.setZoneId(key.zoneId);
        dispatch.setBatchId(batch.getId());
        dispatch.setStageCode(payload.getStageCode());
        dispatch.setSlotStartMinute(payload.getSlotStartMinute());
        dispatch.setRecipeId(payload.getRecipeId());
        dispatch.setRecipeVersionNo(payload.getRecipeVersionNo());
        dispatch.setSource(payload.getSource());
        dispatch.setPayloadJson(payload.toJson(objectMapper));
        dispatch.setPlannedAt(hour);
        dispatch.setOperatorId(operatorId);
        dispatch.setOperatorName(operatorName);
        return dispatch;
    }

    // ============================================================ 设备送达与重试

    /** 送达网关；失败置 FAILED 并入重试队列（同一 dispatch 唯一一条）。 */
    private void deliver(LightDispatch dispatch, LightPayload payload) {
        try {
            deviceGateway.send(toCommand(dispatch, payload));
            dispatch.setResult(PfStatus.DISPATCH_SUCCESS);
            dispatch.setDispatchedAt(LocalDateTime.now());
            dispatch.setFailReason(null);
            dispatchMapper.updateById(dispatch);
        } catch (LightDeviceException e) {
            log.warn("[光配方下发] 棚区设备下发失败，进入重试队列 dispatchId={}: {}",
                    dispatch.getId(), e.getMessage());
            dispatch.setResult(PfStatus.DISPATCH_FAILED);
            dispatch.setFailReason(truncate(e.getMessage()));
            dispatch.setDispatchedAt(LocalDateTime.now());
            dispatchMapper.updateById(dispatch);
            enqueueRetry(dispatch, e.getMessage(), LocalDateTime.now());
        }
    }

    private void enqueueRetry(LightDispatch dispatch, String reason, LocalDateTime now) {
        LightDispatchRetry existing = retryMapper.selectOne(new QueryWrapper<LightDispatchRetry>()
                .eq("dispatch_id", dispatch.getId()).last("LIMIT 1"));
        if (existing != null) {
            if (PfStatus.RETRY_PENDING.equals(existing.getStatus())) {
                return;
            }
            existing.setStatus(PfStatus.RETRY_PENDING);
            existing.setNextRetryAt(backoff(existing.getAttemptCount(), now));
            existing.setLastFailReason(truncate(reason));
            retryMapper.updateById(existing);
            return;
        }
        LightDispatchRetry retry = new LightDispatchRetry();
        retry.setDispatchId(dispatch.getId());
        retry.setGreenhouseId(dispatch.getGreenhouseId());
        retry.setZoneId(dispatch.getZoneId());
        retry.setAttemptCount(0);
        retry.setStatus(PfStatus.RETRY_PENDING);
        retry.setNextRetryAt(backoff(0, now));
        retry.setLastFailReason(truncate(reason));
        retryMapper.insert(retry);
    }

    /** 重试到期记录（定时任务高频扫描，如每 5 分钟）。按快照重发，不改配方内容。 */
    public int retryDue() {
        return retryDue(LocalDateTime.now());
    }

    @Transactional
    public int retryDue(LocalDateTime now) {
        List<LightDispatchRetry> due = retryMapper.selectList(new QueryWrapper<LightDispatchRetry>()
                .eq("status", PfStatus.RETRY_PENDING)
                .le("next_retry_at", now)
                .orderByAsc("id"));
        int succeeded = 0;
        for (LightDispatchRetry retry : due) {
            LightDispatch dispatch = dispatchMapper.selectById(retry.getDispatchId());
            if (dispatch == null) {
                retry.setStatus(PfStatus.RETRY_GIVEN_UP);
                retry.setLastFailReason("下发流水不存在");
                retryMapper.updateById(retry);
                continue;
            }
            LightPayload payload = LightPayload.fromJson(objectMapper, dispatch.getPayloadJson());
            payload.setSource(PfStatus.SOURCE_RETRY);
            try {
                deviceGateway.send(toCommand(dispatch, payload));
                retry.setStatus(PfStatus.RETRY_DONE);
                retry.setLastFailReason(null);
                retryMapper.updateById(retry);
                dispatch.setResult(PfStatus.DISPATCH_SUCCESS);
                dispatch.setFailReason(null);
                dispatchMapper.updateById(dispatch);
                succeeded++;
                log.info("[光配方重试] 棚区 {} 重试成功 dispatchId={} 尝试次数={}",
                        zoneKey(dispatch), dispatch.getId(), retry.getAttemptCount() + 1);
            } catch (LightDeviceException e) {
                int attempts = retry.getAttemptCount() + 1;
                retry.setAttemptCount(attempts);
                retry.setLastFailReason(truncate(e.getMessage()));
                if (attempts >= MAX_ATTEMPTS) {
                    retry.setStatus(PfStatus.RETRY_GIVEN_UP);
                    log.error("[光配方重试] 棚区 {} 已达上限 {} 次，放弃待人工介入 dispatchId={}",
                            zoneKey(dispatch), MAX_ATTEMPTS, dispatch.getId());
                } else {
                    retry.setNextRetryAt(backoff(attempts, now));
                    log.warn("[光配方重试] 棚区 {} 第 {} 次失败，{} 后再试",
                            zoneKey(dispatch), attempts, retry.getNextRetryAt());
                }
                retryMapper.updateById(retry);
            }
        }
        return succeeded;
    }

    private LocalDateTime backoff(int attempt, LocalDateTime now) {
        long minutes = Math.min(BACKOFF_CAP_MINUTES, BACKOFF_BASE_MINUTES * (1L << attempt));
        return now.plusMinutes(minutes);
    }

    private LightCommand toCommand(LightDispatch dispatch, LightPayload payload) {
        return new LightCommand(dispatch.getGreenhouseId(), dispatch.getZoneId(), dispatch.getBatchId(),
                payload.getBrightnessPct(), payload.getRatioRedPct(), payload.getRatioGreenPct(),
                payload.getRatioBluePct(), payload.getRatioWhitePct(), payload.getSource());
    }

    // ============================================================ 人工临时调整

    /** 登记人工临时调整——结束时间与审批人在 DTO 层强制非空，此处再做业务口径校验。 */
    @Transactional
    public LightManualOverride createOverride(LightOverrideRequest request) {
        if (greenhouseMapper.selectById(request.getGreenhouseId()) == null) {
            throw new IllegalArgumentException("温室不存在: " + request.getGreenhouseId());
        }
        if (request.getEndAt() == null) {
            throw new IllegalArgumentException("人工临时调整必须带结束时间");
        }
        if (request.getApproverId() == null) {
            throw new IllegalArgumentException("人工临时调整必须带审批人");
        }
        // 结束时间必须晚于开始时间（整点口径：至少留出一个整点）
        if (!request.getEndAt().isAfter(request.getStartAt())) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }
        int ratioSum = request.getRatioRedPct() + request.getRatioGreenPct()
                + request.getRatioBluePct() + request.getRatioWhitePct();
        if (request.getBrightnessPct() == 0) {
            if (ratioSum != 0) {
                throw new IllegalArgumentException("关灯调整（亮度0）光谱比例应全部为0");
            }
        } else if (ratioSum != 100) {
            throw new IllegalArgumentException("红/绿/蓝/白比例之和必须为100，实际: " + ratioSum);
        }

        LightManualOverride override = new LightManualOverride();
        override.setGreenhouseId(request.getGreenhouseId());
        override.setZoneId(request.getZoneId());
        override.setBatchId(request.getBatchId());
        override.setBrightnessPct(request.getBrightnessPct());
        override.setRatioRedPct(request.getRatioRedPct());
        override.setRatioGreenPct(request.getRatioGreenPct());
        override.setRatioBluePct(request.getRatioBluePct());
        override.setRatioWhitePct(request.getRatioWhitePct());
        override.setStartAt(request.getStartAt());
        override.setEndAt(request.getEndAt());
        override.setApproverId(request.getApproverId());
        override.setApproverName(operatorResolver.nameOf(request.getApproverId()));
        override.setReason(request.getReason());
        override.setStatus(PfStatus.OVERRIDE_ACTIVE);
        Long operatorId = CurrentOperator.id();
        override.setOperatorId(operatorId);
        override.setOperatorName(operatorResolver.nameOf(operatorId));
        overrideMapper.insert(override);
        log.info("[人工调整] 登记临时调整 gh={} zone={} 结束={} 审批人={} 登记人={}",
                request.getGreenhouseId(), request.getZoneId(), request.getEndAt(),
                request.getApproverId(), operatorId);
        return override;
    }

    /** 提前撤销人工调整（仍需留痕；下一整点即恢复配方）。 */
    @Transactional
    public LightManualOverride revokeOverride(Long overrideId) {
        LightManualOverride override = overrideMapper.selectById(overrideId);
        if (override == null) {
            throw new IllegalArgumentException("人工调整不存在: " + overrideId);
        }
        if (!PfStatus.OVERRIDE_ACTIVE.equals(override.getStatus())) {
            throw new IllegalStateException("人工调整已结束，不能撤销: " + overrideId);
        }
        override.setStatus(PfStatus.OVERRIDE_REVOKED);
        override.setRestoredAt(LocalDateTime.now());
        overrideMapper.updateById(override);
        return override;
    }

    /** 到期调整置 EXPIRED（左闭右开：end_at <= 整点 即失效，整点恢复配方）。 */
    private void expireOverrides(LocalDateTime hour) {
        List<LightManualOverride> due = overrideMapper.selectList(new QueryWrapper<LightManualOverride>()
                .eq("status", PfStatus.OVERRIDE_ACTIVE)
                .le("end_at", hour));
        for (LightManualOverride override : due) {
            override.setStatus(PfStatus.OVERRIDE_EXPIRED);
            override.setRestoredAt(hour);
            overrideMapper.updateById(override);
            log.info("[人工调整] 到期自动恢复配方 overrideId={} endAt={} restoredAt={}",
                    override.getId(), override.getEndAt(), hour);
        }
    }

    /** 有效期按整点左闭右开：start_at <= hour < end_at；优先级 批次 > 分区 > 温室。 */
    private LightManualOverride findActiveOverride(ZoneKey key, Long batchId, LocalDateTime hour) {
        QueryWrapper<LightManualOverride> zoneCond = new QueryWrapper<LightManualOverride>()
                .eq("status", PfStatus.OVERRIDE_ACTIVE)
                .eq("greenhouse_id", key.greenhouseId);
        if (key.zoneId == null) {
            zoneCond.isNull("zone_id");
        } else {
            zoneCond.eq("zone_id", key.zoneId);
        }
        List<LightManualOverride> actives = overrideMapper.selectList(zoneCond
                .le("start_at", hour)
                .gt("end_at", hour)
                .orderByDesc("id"));
        // 温室级（zone_id 为空）与分区级在同一查询里取不到，补查温室级
        if (key.zoneId != null) {
            actives.addAll(overrideMapper.selectList(new QueryWrapper<LightManualOverride>()
                    .eq("status", PfStatus.OVERRIDE_ACTIVE)
                    .eq("greenhouse_id", key.greenhouseId)
                    .isNull("zone_id")
                    .le("start_at", hour)
                    .gt("end_at", hour)
                    .orderByDesc("id")));
        }
        LightManualOverride batchMatch = null;
        LightManualOverride zoneMatch = null;
        for (LightManualOverride o : actives) {
            if (o.getBatchId() != null && o.getBatchId().equals(batchId)) {
                batchMatch = pickLatest(batchMatch, o);
            } else if (o.getBatchId() == null) {
                zoneMatch = pickLatest(zoneMatch, o);
            }
        }
        return batchMatch != null ? batchMatch : zoneMatch;
    }

    private LightManualOverride pickLatest(LightManualOverride a, LightManualOverride b) {
        if (a == null) {
            return b;
        }
        return b.getId() > a.getId() ? b : a;
    }

    private boolean restoredThisHour(ZoneKey key, Long batchId, LocalDateTime hour) {
        QueryWrapper<LightManualOverride> qw = new QueryWrapper<LightManualOverride>()
                .eq("status", PfStatus.OVERRIDE_EXPIRED)
                .eq("greenhouse_id", key.greenhouseId);
        if (key.zoneId == null) {
            qw.isNull("zone_id");
        } else {
            qw.eq("zone_id", key.zoneId);
        }
        qw.eq("restored_at", hour)
                .and(w -> w.isNull("batch_id").or().eq("batch_id", batchId));
        return overrideMapper.selectCount(qw) > 0;
    }

    // ============================================================ 棚区归组/查询

    private Map<ZoneKey, List<Batch>> groupActiveBatchesByZone() {
        List<Batch> batches = batchMapper.selectList(new QueryWrapper<Batch>()
                .ne("status", PfStatus.BATCH_HARVESTED));
        Map<ZoneKey, List<Batch>> grouped = new LinkedHashMap<>();
        for (Batch batch : batches) {
            ZoneKey key = new ZoneKey(batch.getGreenhouseId(), batch.getZoneId());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(batch);
        }
        return grouped;
    }

    public List<LightDispatch> listDispatches(String result) {
        return dispatchMapper.selectList(new QueryWrapper<LightDispatch>()
                .eq(result != null, "result", result)
                .orderByDesc("planned_at").orderByDesc("id"));
    }

    public List<LightDispatchRetry> listRetries(String status) {
        return retryMapper.selectList(new QueryWrapper<LightDispatchRetry>()
                .eq(status != null, "status", status)
                .orderByAsc("next_retry_at"));
    }

    public List<LightManualOverride> listOverrides(String status) {
        return overrideMapper.selectList(new QueryWrapper<LightManualOverride>()
                .eq(status != null, "status", status)
                .orderByDesc("id"));
    }

    private String zoneKey(LightDispatch d) {
        return d.getGreenhouseId() + ":" + (d.getZoneId() == null ? "*" : d.getZoneId());
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 255 ? s : s.substring(0, 255);
    }

    /** 棚区键：分区可空（温室级灯具）。 */
    private static final class ZoneKey {
        private final Long greenhouseId;
        private final Long zoneId;

        private ZoneKey(Long greenhouseId, Long zoneId) {
            this.greenhouseId = greenhouseId;
            this.zoneId = zoneId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ZoneKey)) {
                return false;
            }
            ZoneKey other = (ZoneKey) o;
            return greenhouseId.equals(other.greenhouseId)
                    && java.util.Objects.equals(zoneId, other.zoneId);
        }

        @Override
        public int hashCode() {
            return 31 * greenhouseId.hashCode() + (zoneId == null ? 0 : zoneId.hashCode());
        }

        @Override
        public String toString() {
            return greenhouseId + ":" + (zoneId == null ? "*" : zoneId);
        }
    }
}
