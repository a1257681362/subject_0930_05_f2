package com.evops.plant.service;

import com.evops.plant.domain.PfStatus;
import com.evops.plant.dto.LightOverrideRequest;
import com.evops.plant.dto.LightRecipeRequest;
import com.evops.plant.entity.Batch;
import com.evops.plant.entity.BatchStageBinding;
import com.evops.plant.entity.Greenhouse;
import com.evops.plant.entity.LightDispatch;
import com.evops.plant.entity.LightDispatchRetry;
import com.evops.plant.entity.LightManualOverride;
import com.evops.plant.entity.LightRecipe;
import com.evops.plant.entity.Zone;
import com.evops.plant.gateway.LoggingLightDeviceGateway;
import com.evops.plant.mapper.BatchMapper;
import com.evops.plant.mapper.BatchStageBindingMapper;
import com.evops.plant.mapper.GreenhouseMapper;
import com.evops.plant.mapper.LightDispatchMapper;
import com.evops.plant.mapper.LightDispatchRetryMapper;
import com.evops.plant.mapper.LightManualOverrideMapper;
import com.evops.plant.mapper.ZoneMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 光配方端到端流程测试（H2 内存库 + 真实 Quartz 外的全部组件）：
 * 重点验证临界时间口径、版本只影响未开始阶段、历史快照、整点幂等、失败重试、人工调整约束。
 */
@SpringBootTest
@ActiveProfiles("test")
class LightRecipeFlowTest {

    private static final LocalDate SOW = LocalDate.of(2026, 10, 1);
    private static final LocalDate HARVEST = LocalDate.of(2026, 10, 31);

    @Autowired
    private LightRecipeService recipeService;
    @Autowired
    private LightDispatchService dispatchService;
    @Autowired
    private MasterDataService masterDataService;
    @Autowired
    private GreenhouseMapper greenhouseMapper;
    @Autowired
    private ZoneMapper zoneMapper;
    @Autowired
    private com.evops.plant.mapper.VarietyMapper varietyMapper;
    @Autowired
    private BatchMapper batchMapper;
    @Autowired
    private BatchStageBindingMapper bindingMapper;
    @Autowired
    private LightDispatchMapper dispatchMapper;
    @Autowired
    private LightDispatchRetryMapper retryMapper;
    @Autowired
    private LightManualOverrideMapper overrideMapper;
    @Autowired
    private LoggingLightDeviceGateway gateway;
    @Autowired
    private ObjectMapper objectMapper;

    private static final java.util.concurrent.atomic.AtomicLong GH_SEQ =
            new java.util.concurrent.atomic.AtomicLong(100);

    @BeforeEach
    void cleanGateway() {
        gateway.clearFailures();
        gateway.clearSentCommands();
    }

    // ============================================================ 临界时间：整点时段 + 阶段边界

    @Test
    void criticalHourAndStageBoundary_sameRuleEverywhere() {
        Zone zone = newZone();
        Batch batch = newBatch(zone, SOW, HARVEST);

        // 第 8 天（进度 26）06:00 整点：SEEDLING 阶段 + 白天时段 → 55%
        int created = dispatchService.dispatchTick(LocalDateTime.of(2026, 10, 9, 6, 0));
        assertEquals(1, created);
        LightDispatch d0600 = latestDispatch();
        assertEquals(55, payload(d0600).getBrightnessPct());
        assertEquals("SEEDLING", activeBinding(batch).getStageCode());
        assertEquals(1, activeBinding(batch).getRecipeVersionNo());

        // 同一整点重复触发：幂等，不多发
        assertEquals(0, dispatchService.dispatchTick(LocalDateTime.of(2026, 10, 9, 6, 30)));

        // 22:00 整点：按左闭右开归夜间关灯时段 → 0%
        dispatchService.dispatchTick(LocalDateTime.of(2026, 10, 9, 22, 0));
        assertEquals(0, payload(latestDispatch()).getBrightnessPct());
        // 05:00 整点仍属夜间 → 0%（阶段仍为 SEEDLING，未产生新绑定）
        dispatchService.dispatchTick(LocalDateTime.of(2026, 10, 9, 5, 0));
        assertEquals(0, payload(latestDispatch()).getBrightnessPct());
        assertEquals(1, bindingMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BatchStageBinding>()
                        .eq("batch_id", batch.getId())));

        // 第 9 天进度=30：临界进度归后一阶段 VEGETATIVE，白天 → 90%
        dispatchService.dispatchTick(LocalDateTime.of(2026, 10, 10, 6, 0));
        LightDispatch dNext = latestDispatch();
        assertEquals(90, payload(dNext).getBrightnessPct());
        assertEquals("VEGETATIVE", activeBinding(batch).getStageCode());
    }

    // ============================================================ 新版本只影响未开始阶段 + 历史快照

    @Test
    void newVersionOnlyAffectsUnstartedStage_andSnapshotKept() {
        Zone zone = newZone();
        Long varietyId = newVariety();
        Batch batch = newBatch(zone, varietyId, SOW, HARVEST);

        // 该品种先发布 v1（生效时间早于第一次巡检），白天 SEEDLING 55 / VEGETATIVE 90
        LightRecipe v1 = recipeService.createDraft(recipeRequest(varietyId, 55, 90));
        recipeService.publish(v1.getId(), LocalDateTime.of(2026, 10, 1, 0, 0));

        // 第 8 天 06:00：进入 SEEDLING，冻结 v1（白天 55%）
        dispatchService.dispatchTick(LocalDateTime.of(2026, 10, 9, 6, 0));
        assertEquals(55, payload(latestDispatch()).getBrightnessPct());

        // 当天 10:30 发布 v2：SEEDLING 白天改为 44，VEGETATIVE 白天改为 77；下一整点 11:00 生效
        LightRecipe v2 = recipeService.createDraft(recipeRequest(varietyId, 44, 77));
        recipeService.publish(v2.getId(), LocalDateTime.of(2026, 10, 9, 10, 30));

        // 生效边界：10:59 仍选 v1，11:00 起选 v2
        assertEquals(1, recipeService.effectiveRecipe(varietyId, LocalDateTime.of(2026, 10, 9, 10, 59)).getVersionNo());
        assertEquals(2, recipeService.effectiveRecipe(varietyId, LocalDateTime.of(2026, 10, 9, 11, 0)).getVersionNo());

        // 11:00 整点：SEEDLING 阶段已开始 → 仍按冻结的 v1 快照下发 55%，不受 v2 影响
        dispatchService.dispatchTick(LocalDateTime.of(2026, 10, 9, 11, 0));
        LightDispatch kept = latestDispatch();
        assertEquals(1, kept.getRecipeVersionNo());
        assertEquals(55, payload(kept).getBrightnessPct());

        // 第 9 天进度到 30：进入未开始的 VEGETATIVE → 绑定 v2，按 77% 下发；SEEDLING 绑定结束且保留 v1 快照
        dispatchService.dispatchTick(LocalDateTime.of(2026, 10, 10, 6, 0));
        LightDispatch advanced = latestDispatch();
        assertEquals(2, advanced.getRecipeVersionNo());
        assertEquals(77, payload(advanced).getBrightnessPct());

        BatchStageBinding active = activeBinding(batch);
        assertEquals("VEGETATIVE", active.getStageCode());
        assertEquals(2, active.getRecipeVersionNo());
        BatchStageBinding finished = bindingMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BatchStageBinding>()
                        .eq("batch_id", batch.getId())
                        .eq("status", PfStatus.BINDING_FINISHED)).get(0);
        assertEquals("SEEDLING", finished.getStageCode());
        assertEquals(1, finished.getRecipeVersionNo());
        assertNotNull(finished.getLeftAt());

        // 历史流水快照不变形：最早一条仍是 v1/55
        LightDispatch first = dispatchMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LightDispatch>()
                        .eq("batch_id", batch.getId()).orderByAsc("id")).get(0);
        assertEquals(1, first.getRecipeVersionNo());
        assertEquals(55, payload(first).getBrightnessPct());
    }

    @Test
    void publishedRecipeCannotPublishAgain() {
        Long varietyId = newVariety();
        LightRecipe draft = recipeService.createDraft(recipeRequest(varietyId, 50, 80));
        recipeService.publish(draft.getId(), LocalDateTime.of(2026, 10, 9, 10, 0));
        assertThrows(IllegalStateException.class,
                () -> recipeService.publish(draft.getId(), LocalDateTime.of(2026, 10, 9, 12, 0)));
    }

    // ============================================================ 失败设备进入重试队列

    @Test
    void failedDeviceEntersRetryThenRecovers() {
        Zone zone = newZone();
        Batch batch = newBatch(zone, SOW, HARVEST);
        gateway.markFailing(zone.getGreenhouseId() + ":" + zone.getId());

        dispatchService.dispatchTick(LocalDateTime.of(2026, 10, 9, 6, 0));
        LightDispatch failed = latestDispatch();
        assertEquals(PfStatus.DISPATCH_FAILED, failed.getResult());

        LightDispatchRetry retry = retryMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LightDispatchRetry>()
                        .eq("dispatch_id", failed.getId()));
        assertNotNull(retry);
        assertEquals(PfStatus.RETRY_PENDING, retry.getStatus());
        // 按冻结快照重试：恢复设备后到期重试成功
        gateway.clearFailures();
        int succeeded = dispatchService.retryDue(LocalDateTime.of(2026, 10, 9, 6, 2));
        assertEquals(1, succeeded);
        assertEquals(PfStatus.RETRY_DONE, retryMapper.selectById(retry.getId()).getStatus());
        assertEquals(PfStatus.DISPATCH_SUCCESS, dispatchMapper.selectById(failed.getId()).getResult());
        // 重试指令来源标记 RETRY（按本棚区过滤，其他用例批次也在同一上下文中巡检）
        List<com.evops.plant.gateway.LightCommand> zoneCommands = gateway.getSentCommands().stream()
                .filter(c -> c.getGreenhouseId().equals(zone.getGreenhouseId())
                        && c.getZoneId().equals(zone.getId()))
                .collect(java.util.stream.Collectors.toList());
        assertEquals(1, zoneCommands.size());
        assertEquals(PfStatus.SOURCE_RETRY, zoneCommands.get(0).getSource());
    }

    @Test
    void retryExhaustedMarkedGivenUp() {
        Zone zone = newZone();
        newBatch(zone, SOW, HARVEST);
        gateway.markFailing(zone.getGreenhouseId() + ":" + zone.getId());

        dispatchService.dispatchTick(LocalDateTime.of(2026, 10, 9, 6, 0));
        LightDispatchRetry retry = retryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<LightDispatchRetry>()
                        .eq("status", PfStatus.RETRY_PENDING)).get(0);

        LocalDateTime t = LocalDateTime.of(2026, 10, 9, 6, 1);
        for (int i = 0; i < LightDispatchService.MAX_ATTEMPTS; i++) {
            dispatchService.retryDue(t);
            t = t.plusMinutes(60); // 始终晚于退避间隔
        }
        assertEquals(PfStatus.RETRY_GIVEN_UP, retryMapper.selectById(retry.getId()).getStatus());
        assertEquals(LightDispatchService.MAX_ATTEMPTS,
                retryMapper.selectById(retry.getId()).getAttemptCount().intValue());
    }

    // ============================================================ 人工临时调整：强制结束时间与审批人，到期恢复

    @Test
    void overrideRequiresEndTimeAndApprover() {
        Zone zone = newZone();
        newBatch(zone, SOW, HARVEST);

        LightOverrideRequest noEnd = validOverride(zone);
        noEnd.setEndAt(null);
        assertThrows(IllegalArgumentException.class, () -> dispatchService.createOverride(noEnd));

        LightOverrideRequest noApprover = validOverride(zone);
        noApprover.setApproverId(null);
        assertThrows(IllegalArgumentException.class, () -> dispatchService.createOverride(noApprover));

        LightOverrideRequest endBeforeStart = validOverride(zone);
        endBeforeStart.setEndAt(LocalDateTime.of(2026, 10, 9, 8, 0));
        endBeforeStart.setStartAt(LocalDateTime.of(2026, 10, 9, 9, 0));
        assertThrows(IllegalArgumentException.class, () -> dispatchService.createOverride(endBeforeStart));
    }

    @Test
    void overrideAppliesAndAutoRestoresAtEndHour() {
        Zone zone = newZone();
        Batch batch = newBatch(zone, SOW, HARVEST);

        LightOverrideRequest req = validOverride(zone);
        req.setBatchId(batch.getId());
        req.setStartAt(LocalDateTime.of(2026, 10, 9, 8, 0));
        req.setEndAt(LocalDateTime.of(2026, 10, 9, 10, 0));
        LightManualOverride override = dispatchService.createOverride(req);
        assertEquals(PfStatus.OVERRIDE_ACTIVE, override.getStatus());
        assertEquals("建档管理员", override.getApproverName());

        // 09:00 整点：调整生效中 → 覆盖配方
        dispatchService.dispatchTick(LocalDateTime.of(2026, 10, 9, 9, 0));
        LightDispatch overridden = latestDispatch();
        assertEquals(PfStatus.SOURCE_OVERRIDE, overridden.getSource());
        assertEquals(33, payload(overridden).getBrightnessPct());
        assertEquals(1L, payload(overridden).getApproverId());

        // 10:00 整点：end_at 临界（左闭右开）→ 到期自动恢复配方，来源标记 RESTORE
        dispatchService.dispatchTick(LocalDateTime.of(2026, 10, 9, 10, 0));
        LightDispatch restored = latestDispatch();
        assertEquals(PfStatus.SOURCE_RESTORE, restored.getSource());
        assertEquals(55, payload(restored).getBrightnessPct());
        LightManualOverride expired = overrideMapper.selectById(override.getId());
        assertEquals(PfStatus.OVERRIDE_EXPIRED, expired.getStatus());
        assertEquals(LocalDateTime.of(2026, 10, 9, 10, 0), expired.getRestoredAt());

        // 撤销已结束调整被拒绝
        assertThrows(IllegalStateException.class,
                () -> dispatchService.revokeOverride(override.getId()));
    }

    // ============================================================ 配方配置口径校验

    @Test
    void rejectInvalidRatiosAndCoverageGap() {
        // 光谱比例合计不为 100
        Long varietyId = newVariety();
        LightRecipeRequest badRatio = recipeRequest(varietyId, 55, 90);
        badRatio.getStages().get(0).getSlots().get(0).setRatioRedPct(40);
        assertThrows(IllegalArgumentException.class, () -> recipeService.createDraft(badRatio));

        // 时段未无缝覆盖全天（白天 06:00-22:00，夜间 22:00-05:00 留缺口）
        LightRecipeRequest gap = recipeRequest(newVariety(), 55, 90);
        gap.getStages().get(0).getSlots().get(1).setEnd("05:00");
        assertThrows(IllegalArgumentException.class, () -> recipeService.createDraft(gap));

        // 阶段区间不覆盖 0~100
        LightRecipeRequest badStage = recipeRequest(newVariety(), 55, 90);
        badStage.getStages().get(3).setToProgress(99);
        assertThrows(IllegalArgumentException.class, () -> recipeService.createDraft(badStage));
    }

    // ============================================================ helpers

    private Zone newZone() {
        long seq = GH_SEQ.incrementAndGet();
        Greenhouse gh = new Greenhouse();
        gh.setGhCode("GH-T" + seq);
        gh.setGhName("测试温室" + seq);
        gh.setStatus(PfStatus.ACTIVE);
        greenhouseMapper.insert(gh);
        Zone zone = new Zone();
        zone.setGreenhouseId(gh.getId());
        zone.setZoneCode("Z" + seq);
        zone.setZoneName("测试分区");
        zone.setStatus(PfStatus.ACTIVE);
        zoneMapper.insert(zone);
        return zone;
    }

    private Batch newBatch(Zone zone, LocalDate sow, LocalDate plannedHarvest) {
        return newBatch(zone, 1L, sow, plannedHarvest);
    }

    /** 为"版本切换"用例创建独立品种，避免向品种1的种子配方追加版本而污染其他用例。 */
    private Long newVariety() {
        long seq = GH_SEQ.incrementAndGet();
        com.evops.plant.entity.Variety variety = new com.evops.plant.entity.Variety();
        variety.setVarietyCode("V-T" + seq);
        variety.setVarietyName("测试品种" + seq);
        variety.setCategory("测试");
        variety.setGrowDays(30);
        variety.setStatus(PfStatus.ACTIVE);
        varietyMapper.insert(variety);
        return variety.getId();
    }

    private Batch newBatch(Zone zone, Long varietyId, LocalDate sow, LocalDate plannedHarvest) {
        Batch batch = new Batch();
        batch.setBatchNo("PFB-TEST-" + System.nanoTime());
        batch.setVarietyId(varietyId);
        batch.setTrayId(1L);
        batch.setGreenhouseId(zone.getGreenhouseId());
        batch.setZoneId(zone.getId());
        batch.setSowDate(sow);
        batch.setPlannedHarvestDate(plannedHarvest);
        batch.setStatus(PfStatus.BATCH_SOWN);
        batch.setStartupFlag("N");
        batchMapper.insert(batch);
        return batch;
    }

    private BatchStageBinding activeBinding(Batch batch) {
        List<BatchStageBinding> list = bindingMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BatchStageBinding>()
                        .eq("batch_id", batch.getId())
                        .eq("status", PfStatus.BINDING_ACTIVE));
        assertEquals(1, list.size());
        return list.get(0);
    }

    private LightDispatch latestDispatch() {
        List<LightDispatch> all = dispatchMapper.selectList(null);
        assertTrue(!all.isEmpty());
        return all.stream().max((a, b) -> a.getId().compareTo(b.getId())).orElseThrow(IllegalStateException::new);
    }

    private LightPayload payload(LightDispatch dispatch) {
        LightPayload p = LightPayload.fromJson(objectMapper, dispatch.getPayloadJson());
        assertNotNull(p.getBrightnessPct());
        return p;
    }

    private LightOverrideRequest validOverride(Zone zone) {
        LightOverrideRequest req = new LightOverrideRequest();
        req.setGreenhouseId(zone.getGreenhouseId());
        req.setZoneId(zone.getId());
        req.setBrightnessPct(33);
        req.setRatioRedPct(70);
        req.setRatioGreenPct(10);
        req.setRatioBluePct(15);
        req.setRatioWhitePct(5);
        req.setStartAt(LocalDateTime.of(2026, 10, 9, 8, 0));
        req.setEndAt(LocalDateTime.of(2026, 10, 9, 10, 0));
        req.setApproverId(1L);
        req.setReason("测试临时调整");
        return req;
    }

    /** 四阶段、每阶段白天 06:00-22:00 + 夜间 22:00-06:00 两时段的配方请求。 */
    private LightRecipeRequest recipeRequest(Long varietyId, int seedlingDayBrightness, int vegetativeDayBrightness) {
        LightRecipeRequest req = new LightRecipeRequest();
        req.setVarietyId(varietyId);
        List<LightRecipeRequest.StageDto> stages = new ArrayList<>();
        stages.add(stage("SEEDLING", 0, 30, seedlingDayBrightness));
        stages.add(stage("VEGETATIVE", 30, 70, vegetativeDayBrightness));
        stages.add(stage("FLOWERING", 70, 90, 80));
        stages.add(stage("FRUITING", 90, 100, 70));
        req.setStages(stages);
        return req;
    }

    private LightRecipeRequest.StageDto stage(String code, int from, int to, int dayBrightness) {
        LightRecipeRequest.StageDto stage = new LightRecipeRequest.StageDto();
        stage.setStageCode(code);
        stage.setFromProgress(from);
        stage.setToProgress(to);
        stage.setDefaultBrightnessPct(dayBrightness);
        List<LightRecipeRequest.SlotDto> slots = new ArrayList<>();
        slots.add(slot("06:00", "22:00", dayBrightness, 50, 10, 25, 15));
        slots.add(slot("22:00", "06:00", 0, 0, 0, 0, 0));
        stage.setSlots(slots);
        return stage;
    }

    private LightRecipeRequest.SlotDto slot(String start, String end, int brightness,
                                            int r, int g, int b, int w) {
        LightRecipeRequest.SlotDto slot = new LightRecipeRequest.SlotDto();
        slot.setStart(start);
        slot.setEnd(end);
        slot.setBrightnessPct(brightness);
        slot.setRatioRedPct(r);
        slot.setRatioGreenPct(g);
        slot.setRatioBluePct(b);
        slot.setRatioWhitePct(w);
        return slot;
    }
}
