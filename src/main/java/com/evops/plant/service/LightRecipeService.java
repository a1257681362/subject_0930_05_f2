package com.evops.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.plant.domain.GrowthStage;
import com.evops.plant.domain.GrowthStageRule;
import com.evops.plant.domain.PfStatus;
import com.evops.plant.domain.TimeSlotMatcher;
import com.evops.plant.dto.LightRecipeDetail;
import com.evops.plant.dto.LightRecipeRequest;
import com.evops.plant.entity.LightRecipe;
import com.evops.plant.entity.LightRecipeSlot;
import com.evops.plant.entity.LightRecipeStage;
import com.evops.plant.entity.Variety;
import com.evops.plant.mapper.LightRecipeMapper;
import com.evops.plant.mapper.LightRecipeSlotMapper;
import com.evops.plant.mapper.LightRecipeStageMapper;
import com.evops.plant.mapper.VarietyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 光配方配置：按品种版本化管理。
 *
 * <p>规则：
 * <ul>
 *   <li>草稿 DRAFT 可改；发布 PUBLISHED 后只追加、不再修改，改配方一律发新版本；</li>
 *   <li>发布不立即生效——effective_at 取发布时刻的<b>下一整点</b>，配方整点切换；</li>
 *   <li>新版本只影响"未开始"阶段（下发服务在批次进入阶段时才绑定版本，见 LightDispatchService）；</li>
 *   <li>阶段区间、时段全天覆盖、光谱比例口径在发布前统一校验（GrowthStageRule / TimeSlotMatcher）。</li>
 * </ul>
 */
@Service
public class LightRecipeService {

    /** HH:mm 与 H:mm 都接受 */
    private static final DateTimeFormatter HHMM = new DateTimeFormatterBuilder()
            .appendPattern("H:mm")
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .toFormatter();

    private final LightRecipeMapper recipeMapper;
    private final LightRecipeStageMapper stageMapper;
    private final LightRecipeSlotMapper slotMapper;
    private final VarietyMapper varietyMapper;

    public LightRecipeService(LightRecipeMapper recipeMapper, LightRecipeStageMapper stageMapper,
                              LightRecipeSlotMapper slotMapper, VarietyMapper varietyMapper) {
        this.recipeMapper = recipeMapper;
        this.stageMapper = stageMapper;
        this.slotMapper = slotMapper;
        this.varietyMapper = varietyMapper;
    }

    /** 创建草稿配方（版本号为该品种当前最大版本 +1）。 */
    @Transactional
    public LightRecipe createDraft(LightRecipeRequest request) {
        if (request.getVarietyId() == null) {
            throw new IllegalArgumentException("品种不能为空");
        }
        Variety variety = varietyMapper.selectById(request.getVarietyId());
        if (variety == null) {
            throw new IllegalArgumentException("品种不存在: " + request.getVarietyId());
        }
        validateStages(request);

        LightRecipe recipe = new LightRecipe();
        recipe.setRecipeCode("LRC-" + variety.getVarietyCode() + "-" + nextVersion(variety.getId()));
        recipe.setVarietyId(variety.getId());
        recipe.setVersionNo(nextVersion(variety.getId()));
        recipe.setStatus(PfStatus.RECIPE_DRAFT);
        recipe.setRemark(request.getRemark());
        recipeMapper.insert(recipe);
        insertStagesAndSlots(recipe, request);
        return recipe;
    }

    /**
     * 发布配方：状态置 PUBLISHED，生效时间为发布时刻的<b>下一整点</b>。
     * 已发布/不存在的配方不可再发布。
     */
    @Transactional
    public LightRecipe publish(Long recipeId) {
        return publish(recipeId, LocalDateTime.now());
    }

    /** 可注入发布时刻的重载（临界时间测试用）。 */
    @Transactional
    public LightRecipe publish(Long recipeId, LocalDateTime publishTime) {
        LightRecipe recipe = requireRecipe(recipeId);
        if (PfStatus.RECIPE_PUBLISHED.equals(recipe.getStatus())) {
            throw new IllegalStateException("配方已发布，不能重复发布；调整请发新版本: " + recipe.getRecipeCode());
        }
        LocalDateTime now = publishTime.withSecond(0).withNano(0);
        LocalDateTime nextHour = now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
        recipe.setStatus(PfStatus.RECIPE_PUBLISHED);
        recipe.setPublishedAt(now);
        recipe.setEffectiveAt(nextHour);
        recipeMapper.updateById(recipe);
        return recipe;
    }

    public LightRecipeDetail getDetail(Long recipeId) {
        LightRecipe recipe = requireRecipe(recipeId);
        return loadDetail(recipe);
    }

    public List<LightRecipe> listByVariety(Long varietyId) {
        return recipeMapper.selectList(new QueryWrapper<LightRecipe>()
                .eq(varietyId != null, "variety_id", varietyId)
                .orderByDesc("version_no"));
    }

    /**
     * 取指定时刻对品种生效的配方（PUBLISHED 且 effective_at &lt;= when），取版本号最大者。
     * 未到生效时间的新版本不会被选用——这是"切换在整点生效"的配置侧保证。
     */
    public LightRecipe effectiveRecipe(Long varietyId, LocalDateTime when) {
        List<LightRecipe> recipes = recipeMapper.selectList(new QueryWrapper<LightRecipe>()
                .eq("variety_id", varietyId)
                .eq("status", PfStatus.RECIPE_PUBLISHED)
                .le("effective_at", when)
                .orderByDesc("version_no"));
        return recipes.isEmpty() ? null : recipes.get(0);
    }

    /** 加载配方的阶段与时段（按阶段顺序、时段起点排序）。 */
    public LightRecipeDetail loadDetail(LightRecipe recipe) {
        List<LightRecipeStage> stages = stageMapper.selectList(new QueryWrapper<LightRecipeStage>()
                .eq("recipe_id", recipe.getId()));
        stages.sort(Comparator.comparingInt(s -> GrowthStage.ofCode(s.getStageCode()).order()));

        LightRecipeDetail detail = new LightRecipeDetail();
        detail.setRecipe(recipe);
        List<LightRecipeDetail.StageView> views = new ArrayList<>();
        for (LightRecipeStage stage : stages) {
            List<LightRecipeSlot> slots = slotMapper.selectList(new QueryWrapper<LightRecipeSlot>()
                    .eq("stage_id", stage.getId())
                    .orderByAsc("start_minute"));
            LightRecipeDetail.StageView view = new LightRecipeDetail.StageView();
            view.setStage(stage);
            view.setSlots(slots);
            views.add(view);
        }
        detail.setStages(views);
        return detail;
    }

    // ------------------------------------------------------------------ internal

    private int nextVersion(Long varietyId) {
        Integer max = recipeMapper.selectObjs(new QueryWrapper<LightRecipe>()
                .select("COALESCE(MAX(version_no), 0) AS m")
                .eq("variety_id", varietyId)).stream()
                .findFirst().map(o -> ((Number) o).intValue()).orElse(0);
        return max + 1;
    }

    private LightRecipe requireRecipe(Long recipeId) {
        LightRecipe recipe = recipeMapper.selectById(recipeId);
        if (recipe == null) {
            throw new IllegalArgumentException("光配方不存在: " + recipeId);
        }
        return recipe;
    }

    /** 按统一口径校验阶段与时段（阶段归属/时间片临界无歧义）。 */
    private void validateStages(LightRecipeRequest request) {
        List<GrowthStageRule.StageRange> ranges = new ArrayList<>();
        for (LightRecipeRequest.StageDto stageDto : request.getStages()) {
            GrowthStage stage;
            try {
                stage = GrowthStage.ofCode(stageDto.getStageCode() == null
                        ? null : stageDto.getStageCode().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("未知生长阶段: " + stageDto.getStageCode());
            }
            ranges.add(new GrowthStageRule.StageRange(stage,
                    stageDto.getFromProgress(), stageDto.getToProgress()));
            validateBrightness(stageDto.getDefaultBrightnessPct(), "阶段默认亮度");

            List<TimeSlotMatcher.Slot> slots = new ArrayList<>();
            for (LightRecipeRequest.SlotDto slotDto : stageDto.getSlots()) {
                int start = parseMinute(slotDto.getStart());
                int end = parseMinute(slotDto.getEnd());
                // 跨午夜时段允许 end=0(00:00 即 1440 分钟端点)；普通匹配中 24:00 用 1440 表达
                TimeSlotMatcher.Slot slot = new TimeSlotMatcher.Slot(start, end == 0 && start != 0 ? 1440 : end);
                slots.add(slot);
                validateBrightness(slotDto.getBrightnessPct(), "时段亮度");
                validateRatios(slotDto);
            }
            TimeSlotMatcher.validateFullDayCoverage(slots);
        }
        GrowthStageRule.validate(ranges);
    }

    private void validateBrightness(Integer value, String field) {
        if (value == null || value < 0 || value > 100) {
            throw new IllegalArgumentException(field + "必须在 0~100 之间");
        }
    }

    /** 光谱四项比例：给光时必须合计 100；关灯时段（亮度 0）允许全 0。 */
    private void validateRatios(LightRecipeRequest.SlotDto slot) {
        int sum = slot.getRatioRedPct() + slot.getRatioGreenPct()
                + slot.getRatioBluePct() + slot.getRatioWhitePct();
        if (slot.getBrightnessPct() == 0) {
            if (sum != 0) {
                throw new IllegalArgumentException("关灯时段（亮度0）光谱比例应全部为0");
            }
        } else if (sum != 100) {
            throw new IllegalArgumentException("给光时段红/绿/蓝/白比例之和必须为100，实际: " + sum);
        }
    }

    private int parseMinute(String hhmm) {
        if (hhmm == null || hhmm.trim().isEmpty()) {
            throw new IllegalArgumentException("时段时间不能为空");
        }
        try {
            return java.time.LocalTime.parse(hhmm.trim(), HHMM).toSecondOfDay() / 60;
        } catch (Exception e) {
            throw new IllegalArgumentException("时段时间格式应为 HH:mm: " + hhmm);
        }
    }

    private void insertStagesAndSlots(LightRecipe recipe, LightRecipeRequest request) {
        for (LightRecipeRequest.StageDto stageDto : request.getStages()) {
            LightRecipeStage stage = new LightRecipeStage();
            stage.setRecipeId(recipe.getId());
            stage.setStageCode(stageDto.getStageCode().toUpperCase());
            stage.setFromProgress(stageDto.getFromProgress());
            stage.setToProgress(stageDto.getToProgress());
            stage.setDefaultBrightnessPct(stageDto.getDefaultBrightnessPct());
            stage.setRemark(stageDto.getRemark());
            stageMapper.insert(stage);

            for (LightRecipeRequest.SlotDto slotDto : stageDto.getSlots()) {
                int start = parseMinute(slotDto.getStart());
                int end = parseMinute(slotDto.getEnd());
                LightRecipeSlot slot = new LightRecipeSlot();
                slot.setStageId(stage.getId());
                slot.setStartMinute(start);
                slot.setEndMinute(end == 0 && start != 0 ? 1440 : end);
                slot.setBrightnessPct(slotDto.getBrightnessPct());
                slot.setRatioRedPct(slotDto.getRatioRedPct());
                slot.setRatioGreenPct(slotDto.getRatioGreenPct());
                slot.setRatioBluePct(slotDto.getRatioBluePct());
                slot.setRatioWhitePct(slotDto.getRatioWhitePct());
                slotMapper.insert(slot);
            }
        }
    }

    /** 跨午夜存储统一：end=1440 表示到次日 00:00（匹配口径见 TimeSlotMatcher）。 */
    public static String formatMinute(int minute) {
        return String.format("%02d:%02d", minute / 60, minute % 60);
    }
}
