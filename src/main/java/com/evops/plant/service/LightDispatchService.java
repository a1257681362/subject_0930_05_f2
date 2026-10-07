package com.evops.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.evops.plant.domain.LightStatus;
import com.evops.plant.domain.LightTimeRules;
import com.evops.plant.domain.PfStatus;
import com.evops.plant.entity.Batch;
import com.evops.plant.entity.LightDevice;
import com.evops.plant.entity.LightDispatch;
import com.evops.plant.entity.LightDispatchDevice;
import com.evops.plant.entity.LightOverride;
import com.evops.plant.entity.LightRecipe;
import com.evops.plant.entity.LightRecipeStage;
import com.evops.plant.entity.Zone;
import com.evops.plant.mapper.BatchMapper;
import com.evops.plant.mapper.LightDeviceMapper;
import com.evops.plant.mapper.LightDispatchDeviceMapper;
import com.evops.plant.mapper.LightDispatchMapper;
import com.evops.plant.mapper.LightRecipeStageMapper;
import com.evops.plant.mapper.ZoneMapper;
import com.evops.plant.support.LightDeviceGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 光配方下发：定时任务按棚区整点下发。
 *  - 生效拍 = 不小于当前时间的最近整点；到点（effective_at <= now）才允许下发，切换只在整点生效；
 *  - 人工临时调整优先于配方；已开始阶段沿用首次下发快照（新版本只影响未开始阶段）；
 *  - 下发单全量冗余参数即历史快照，配方后续变更不影响已生成记录；
 *  - 失败设备进入重试队列，按固定间隔重发，超过最大次数置 DEAD。
 * 任务幂等：每棚区每生效整点至多一单（uk_pf_light_dispatch_hour 兜底），重复触发不多写。
 */
@Service
public class LightDispatchService {

    private static final Logger log = LoggerFactory.getLogger(LightDispatchService.class);

    /** 最大重试次数（含首次下发），超限置 DEAD 等待人工处理设备。 */
    public static final int MAX_RETRY = 12;
    /** 重试间隔（分钟）。 */
    public static final int RETRY_INTERVAL_MINUTES = 5;

    private final ZoneMapper zoneMapper;
    private final BatchMapper batchMapper;
    private final LightRecipeService recipeService;
    private final LightRecipeStageMapper stageMapper;
    private final LightOverrideService overrideService;
    private final LightDeviceMapper deviceMapper;
    private final LightDispatchMapper dispatchMapper;
    private final LightDispatchDeviceMapper deviceItemMapper;
    private final LightDeviceGateway gateway;

    public LightDispatchService(ZoneMapper zoneMapper, BatchMapper batchMapper,
                                LightRecipeService recipeService, LightRecipeStageMapper stageMapper,
                                LightOverrideService overrideService, LightDeviceMapper deviceMapper,
                                LightDispatchMapper dispatchMapper,
                                LightDispatchDeviceMapper deviceItemMapper,
                                LightDeviceGateway gateway) {
        this.zoneMapper = zoneMapper;
        this.batchMapper = batchMapper;
        this.recipeService = recipeService;
        this.stageMapper = stageMapper;
        this.overrideService = overrideService;
        this.deviceMapper = deviceMapper;
        this.dispatchMapper = dispatchMapper;
        this.deviceItemMapper = deviceItemMapper;
        this.gateway = gateway;
    }

    /** 定时任务入口：建单（幂等）→ 到点下发/重试 → 过期调整归置。 */
    @Transactional
    public String runOnce(LocalDateTime now) {
        LocalDateTime hour = LightTimeRules.effectiveHour(now);
        int created = ensureDispatches(hour);
        int sent = executeDue(now);
        int expired = overrideService.expireOverdue(now);
        String summary = "生效拍=" + hour + ", 新建下发单=" + created + ", 设备下发=" + sent + ", 过期调整=" + expired;
        log.info("[光配方下发] {}", summary);
        return summary;
    }

    // ------------------------------------------------------------------
    // 建单：仅"参数发生切换"时为生效拍建单；未生效的单允许刷新快照
    // ------------------------------------------------------------------

    int ensureDispatches(LocalDateTime hour) {
        int created = 0;
        List<Zone> zones = zoneMapper.selectList(new QueryWrapper<Zone>()
                .eq("status", PfStatus.ACTIVE).orderByAsc("id"));
        for (Zone zone : zones) {
            Batch batch = oldestActiveBatch(zone.getId());
            if (batch == null) {
                continue;
            }
            ResolvedParams params = resolve(zone, batch, hour);
            if (params == null) {
                continue;
            }
            LightDispatch existing = dispatchOf(zone.getId(), hour);
            if (existing != null) {
                // 生效前的单尚未触碰任何设备，允许随最新发布/调整刷新快照；生效后留痕不可改
                if (isUntouched(existing) && !params.sameAs(existing)) {
                    params.applyTo(existing);
                    updateParamsExplicit(existing);
                    log.info("[光配方下发] 下发单#{} 生效前刷新快照: {}", existing.getId(), params.describe());
                }
                continue;
            }
            LightDispatch last = latestDispatch(zone.getId());
            if (last != null && params.sameAs(last)) {
                continue; // 无切换不建单
            }
            createDispatch(zone, batch, params, hour);
            created++;
        }
        return created;
    }

    /**
     * 解析棚区生效参数：先钉住配方版本（无论是否有临时调整），再让调整覆盖参数。
     *  - 配方钉版：该批次当前阶段已有下发记录则沿用其配方版本（已开始阶段不受新版本影响），
     *    参数从不可变的阶段表重读——下发单的参数位可能被临时调整覆盖，只有钉版信息可信；
     *  - 无钉版（未开始阶段）取品种当前已发布版本（新版本只影响未开始阶段）；
     *  - 人工临时调整只覆盖参数位，配方钉版保留，调整结束后自动回到原配方版本。
     */
    ResolvedParams resolve(Zone zone, Batch batch, LocalDateTime hour) {
        ResolvedParams recipeParams = resolveRecipe(batch, hour);
        LightOverride override = overrideService.activeAt(zone.getId(), hour);
        if (override != null) {
            return ResolvedParams.withOverride(recipeParams, override);
        }
        if (recipeParams == null) {
            log.warn("[光配方下发] 品种无已发布光配方或当前天数无匹配阶段，跳过棚区: zoneId={}, varietyId={}",
                    zone.getId(), batch.getVarietyId());
        }
        return recipeParams;
    }

    /** 配方钉版解析：返回当前阶段应使用的配方版本参数；无已发布配方或无匹配阶段返回 null。 */
    private ResolvedParams resolveRecipe(Batch batch, LocalDateTime hour) {
        LightRecipe published = recipeService.publishedForVariety(batch.getVarietyId());
        if (published == null) {
            return null;
        }
        List<LightRecipeStage> stages = recipeService.stagesOf(published.getId());
        long days = LightTimeRules.daysSinceSow(batch.getSowDate(), hour.toLocalDate());
        LightRecipeStage stage = LightTimeRules.resolveStage(stages, days);
        if (stage == null) {
            return null;
        }
        // 已开始阶段：沿用首次下发时钉住的配方版本，参数从不可变阶段表重读
        LightDispatch pinned = dispatchMapper.selectOne(new QueryWrapper<LightDispatch>()
                .eq("batch_id", batch.getId())
                .eq("stage_code", stage.getStageCode())
                .isNotNull("recipe_id")
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (pinned != null) {
            LightRecipeStage pinnedStage = stageMapper.selectOne(new QueryWrapper<LightRecipeStage>()
                    .eq("recipe_id", pinned.getRecipeId())
                    .eq("stage_code", pinned.getStageCode())
                    .last("LIMIT 1"));
            if (pinnedStage != null) {
                return ResolvedParams.ofStage(recipeService.requireRecipe(pinned.getRecipeId()), pinnedStage);
            }
        }
        // 未开始阶段：用当前已发布版本
        return ResolvedParams.ofStage(published, stage);
    }

    private void createDispatch(Zone zone, Batch batch, ResolvedParams params, LocalDateTime hour) {
        LightDispatch dispatch = new LightDispatch();
        dispatch.setZoneId(zone.getId());
        dispatch.setGreenhouseId(zone.getGreenhouseId());
        dispatch.setBatchId(batch.getId());
        params.applyTo(dispatch);
        dispatch.setEffectiveAt(hour);
        dispatch.setStatus(LightStatus.DISPATCH_PENDING);
        try {
            dispatchMapper.insert(dispatch);
        } catch (DuplicateKeyException e) {
            // 唯一约束兜底：并发/重复触发下同一棚区同一整点只留一单
            log.info("[光配方下发] 下发单已存在，跳过: zoneId={}, effectiveAt={}", zone.getId(), hour);
            return;
        }
        List<LightDevice> devices = deviceMapper.selectList(new QueryWrapper<LightDevice>()
                .eq("zone_id", zone.getId()).orderByAsc("id"));
        for (LightDevice device : devices) {
            LightDispatchDevice item = new LightDispatchDevice();
            item.setDispatchId(dispatch.getId());
            item.setDeviceId(device.getId());
            item.setDeviceCode(device.getDeviceCode());
            item.setStatus(LightStatus.DEVICE_PENDING);
            item.setRetryCount(0);
            deviceItemMapper.insert(item);
        }
        log.info("[光配方下发] 新建下发单#{}: 棚区={}, 批次={}, {}, 设备数={}, 生效={}",
                dispatch.getId(), zone.getZoneCode(), batch.getBatchNo(), params.describe(),
                devices.size(), hour);
    }

    // ------------------------------------------------------------------
    // 下发与重试：只处理到点的单；失败设备按间隔重发，超限 DEAD
    // ------------------------------------------------------------------

    int executeDue(LocalDateTime now) {
        Set<Long> affectedDispatches = new HashSet<>();
        int sent = 0;

        // 首次下发：PENDING 且所属单已到生效时刻
        List<LightDispatchDevice> pending = deviceItemMapper.selectList(new QueryWrapper<LightDispatchDevice>()
                .eq("status", LightStatus.DEVICE_PENDING).orderByAsc("id"));
        for (LightDispatchDevice item : pending) {
            LightDispatch dispatch = dispatchMapper.selectById(item.getDispatchId());
            if (dispatch == null || dispatch.getEffectiveAt().isAfter(now)) {
                continue; // 未到生效整点，等待
            }
            sendToDevice(item, dispatch, now);
            affectedDispatches.add(dispatch.getId());
            sent++;
        }

        // 重试队列：FAILED/RETRYING 且到重试时刻；超限归置 DEAD
        List<LightDispatchDevice> retryable = deviceItemMapper.selectList(new QueryWrapper<LightDispatchDevice>()
                .in("status", LightStatus.DEVICE_FAILED, LightStatus.DEVICE_RETRYING)
                .and(w -> w.isNull("next_retry_at").or().le("next_retry_at", now))
                .orderByAsc("id"));
        for (LightDispatchDevice item : retryable) {
            if (item.getRetryCount() >= MAX_RETRY) {
                item.setStatus(LightStatus.DEVICE_DEAD);
                item.setNextRetryAt(null);
                deviceItemMapper.updateById(item);
                log.warn("[光配方下发] 设备 {} 重试 {} 次仍失败，置 DEAD: {}",
                        item.getDeviceCode(), item.getRetryCount(), item.getLastError());
            } else {
                LightDispatch dispatch = dispatchMapper.selectById(item.getDispatchId());
                sendToDevice(item, dispatch, now);
                sent++;
            }
            affectedDispatches.add(item.getDispatchId());
        }

        for (Long dispatchId : affectedDispatches) {
            refreshDispatchStatus(dispatchId);
        }
        return sent;
    }

    private void sendToDevice(LightDispatchDevice item, LightDispatch dispatch, LocalDateTime now) {
        LightDevice device = deviceMapper.selectById(item.getDeviceId());
        String error = device == null ? "设备不存在: " + item.getDeviceId() : gateway.send(device, dispatch);
        if (error == null) {
            item.setStatus(LightStatus.DEVICE_SUCCESS);
            item.setSentAt(now);
            item.setLastError(null);
            item.setNextRetryAt(null);
        } else {
            item.setRetryCount(item.getRetryCount() + 1);
            item.setLastError(error);
            if (item.getRetryCount() >= MAX_RETRY) {
                item.setStatus(LightStatus.DEVICE_DEAD);
                item.setNextRetryAt(null);
            } else {
                item.setStatus(LightStatus.DEVICE_RETRYING);
                item.setNextRetryAt(now.plusMinutes(RETRY_INTERVAL_MINUTES));
            }
            log.warn("[光配方下发] 设备 {} 下发失败（第 {} 次）: {}", item.getDeviceCode(), item.getRetryCount(), error);
        }
        deviceItemMapper.updateById(item);
    }

    /** 单状态归置：全部成功 ISSUED / 部分成功 PARTIAL / 否则 FAILED。 */
    private void refreshDispatchStatus(Long dispatchId) {
        List<LightDispatchDevice> items = deviceItemMapper.selectList(new QueryWrapper<LightDispatchDevice>()
                .eq("dispatch_id", dispatchId));
        long success = items.stream().filter(i -> LightStatus.DEVICE_SUCCESS.equals(i.getStatus())).count();
        LightDispatch dispatch = dispatchMapper.selectById(dispatchId);
        String status = success == items.size() ? LightStatus.DISPATCH_ISSUED
                : success > 0 ? LightStatus.DISPATCH_PARTIAL : LightStatus.DISPATCH_FAILED;
        if (!status.equals(dispatch.getStatus())) {
            dispatch.setStatus(status);
            dispatchMapper.updateById(dispatch);
        }
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public List<LightDispatch> listDispatches(Long zoneId, Long batchId) {
        return dispatchMapper.selectList(new QueryWrapper<LightDispatch>()
                .eq(zoneId != null, "zone_id", zoneId)
                .eq(batchId != null, "batch_id", batchId)
                .orderByDesc("effective_at"));
    }

    public List<LightDispatchDevice> listDeviceItems(Long dispatchId) {
        return deviceItemMapper.selectList(new QueryWrapper<LightDispatchDevice>()
                .eq("dispatch_id", dispatchId)
                .orderByAsc("id"));
    }

    /** 重试队列视图：所有未成功也未终结的设备项。 */
    public List<LightDispatchDevice> listRetryQueue() {
        return deviceItemMapper.selectList(new QueryWrapper<LightDispatchDevice>()
                .in("status", LightStatus.DEVICE_FAILED, LightStatus.DEVICE_RETRYING)
                .orderByAsc("next_retry_at"));
    }

    // ------------------------------------------------------------------

    /** 棚区当前在养批次：取播种最早的一批（多批次同棚时以最早者为准，确定性口径）。 */
    private Batch oldestActiveBatch(Long zoneId) {
        return batchMapper.selectOne(new QueryWrapper<Batch>()
                .eq("zone_id", zoneId)
                .in("status", PfStatus.BATCH_SOWN, PfStatus.BATCH_GROWING, PfStatus.BATCH_MOVED)
                .orderByAsc("sow_date")
                .orderByAsc("id")
                .last("LIMIT 1"));
    }

    private LightDispatch dispatchOf(Long zoneId, LocalDateTime hour) {
        return dispatchMapper.selectOne(new QueryWrapper<LightDispatch>()
                .eq("zone_id", zoneId)
                .eq("effective_at", hour)
                .last("LIMIT 1"));
    }

    private LightDispatch latestDispatch(Long zoneId) {
        return dispatchMapper.selectOne(new QueryWrapper<LightDispatch>()
                .eq("zone_id", zoneId)
                .orderByDesc("effective_at")
                .last("LIMIT 1"));
    }

    /** 未触碰任何设备的单（全部设备项仍 PENDING）才允许生效前刷新快照。 */
    private boolean isUntouched(LightDispatch dispatch) {
        Long touched = deviceItemMapper.selectCount(new QueryWrapper<LightDispatchDevice>()
                .eq("dispatch_id", dispatch.getId())
                .ne("status", LightStatus.DEVICE_PENDING));
        return touched == null || touched == 0;
    }

    /** 刷新快照：显式写全部参数列（含空值），避免 updateById 忽略 null 导致旧值残留。 */
    private void updateParamsExplicit(LightDispatch d) {
        dispatchMapper.update(null, new UpdateWrapper<LightDispatch>()
                .eq("id", d.getId())
                .set("recipe_id", d.getRecipeId())
                .set("recipe_version", d.getRecipeVersion())
                .set("stage_code", d.getStageCode())
                .set("override_id", d.getOverrideId())
                .set("spectrum_red", d.getSpectrumRed())
                .set("spectrum_blue", d.getSpectrumBlue())
                .set("spectrum_far_red", d.getSpectrumFarRed())
                .set("brightness_ppfd", d.getBrightnessPpfd())
                .set("light_on", d.getLightOn())
                .set("light_off", d.getLightOff()));
    }

    // ------------------------------------------------------------------

    /** 生效参数：配方钉版（recipeId/version/stageCode）始终保留；临时调整只覆盖参数位。 */
    static final class ResolvedParams {
        Long recipeId;
        Integer recipeVersion;
        String stageCode;
        Long overrideId;
        int spectrumRed;
        int spectrumBlue;
        int spectrumFarRed;
        int brightnessPpfd;
        String lightOn;
        String lightOff;

        static ResolvedParams ofStage(LightRecipe recipe, LightRecipeStage s) {
            ResolvedParams p = base(s.getSpectrumRed(), s.getSpectrumBlue(), s.getSpectrumFarRed(),
                    s.getBrightnessPpfd(), s.getLightOn(), s.getLightOff());
            p.recipeId = recipe.getId();
            p.recipeVersion = recipe.getVersionNo();
            p.stageCode = s.getStageCode();
            return p;
        }

        /** 调整覆盖：参数位取自调整单，配方钉版从底层解析继承（可能为空=无配方）。 */
        static ResolvedParams withOverride(ResolvedParams recipeParams, LightOverride o) {
            ResolvedParams p = base(o.getSpectrumRed(), o.getSpectrumBlue(), o.getSpectrumFarRed(),
                    o.getBrightnessPpfd(), o.getLightOn(), o.getLightOff());
            if (recipeParams != null) {
                p.recipeId = recipeParams.recipeId;
                p.recipeVersion = recipeParams.recipeVersion;
                p.stageCode = recipeParams.stageCode;
            }
            p.overrideId = o.getId();
            return p;
        }

        private static ResolvedParams base(int r, int b, int fr, int ppfd, String on, String off) {
            ResolvedParams p = new ResolvedParams();
            p.spectrumRed = r;
            p.spectrumBlue = b;
            p.spectrumFarRed = fr;
            p.brightnessPpfd = ppfd;
            p.lightOn = on;
            p.lightOff = off;
            return p;
        }

        /** 参数级比较：光谱/亮度/时段/来源（配方版本或调整单）都相同才视为无切换。 */
        boolean sameAs(LightDispatch d) {
            return spectrumRed == d.getSpectrumRed()
                    && spectrumBlue == d.getSpectrumBlue()
                    && spectrumFarRed == d.getSpectrumFarRed()
                    && brightnessPpfd == d.getBrightnessPpfd()
                    && lightOn.equals(d.getLightOn())
                    && lightOff.equals(d.getLightOff())
                    && Objects.equals(recipeId, d.getRecipeId())
                    && Objects.equals(recipeVersion, d.getRecipeVersion())
                    && Objects.equals(overrideId, d.getOverrideId());
        }

        void applyTo(LightDispatch d) {
            d.setRecipeId(recipeId);
            d.setRecipeVersion(recipeVersion);
            d.setStageCode(stageCode);
            d.setOverrideId(overrideId);
            d.setSpectrumRed(spectrumRed);
            d.setSpectrumBlue(spectrumBlue);
            d.setSpectrumFarRed(spectrumFarRed);
            d.setBrightnessPpfd(brightnessPpfd);
            d.setLightOn(lightOn);
            d.setLightOff(lightOff);
        }

        String describe() {
            String source = overrideId != null ? "临时调整#" + overrideId
                    : "配方#" + recipeId + "v" + recipeVersion + "/" + stageCode;
            return source + " R" + spectrumRed + "/B" + spectrumBlue + "/FR" + spectrumFarRed
                    + " " + brightnessPpfd + "PPFD " + lightOn + "-" + lightOff;
        }
    }
}
