package com.evops.plant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.CurrentOperator;
import com.evops.plant.domain.LightParamChecks;
import com.evops.plant.domain.LightStatus;
import com.evops.plant.dto.LightRecipeRequest;
import com.evops.plant.dto.LightStageRequest;
import com.evops.plant.entity.LightRecipe;
import com.evops.plant.entity.LightRecipeStage;
import com.evops.plant.entity.Variety;
import com.evops.plant.mapper.LightRecipeMapper;
import com.evops.plant.mapper.LightRecipeStageMapper;
import com.evops.plant.mapper.VarietyMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 光配方：按品种+生长阶段配置光谱比例、亮度与时段。
 * 版本只增不改；发布新版本只影响未开始的阶段（已开始阶段沿用下发快照）。
 */
@Service
public class LightRecipeService {

    private final LightRecipeMapper recipeMapper;
    private final LightRecipeStageMapper stageMapper;
    private final VarietyMapper varietyMapper;

    public LightRecipeService(LightRecipeMapper recipeMapper, LightRecipeStageMapper stageMapper,
                              VarietyMapper varietyMapper) {
        this.recipeMapper = recipeMapper;
        this.stageMapper = stageMapper;
        this.varietyMapper = varietyMapper;
    }

    /** 创建草稿版本：版本号 = 该配方编码当前最大版本 + 1。 */
    @Transactional
    public LightRecipe create(LightRecipeRequest request) {
        Variety variety = varietyMapper.selectById(request.getVarietyId());
        if (variety == null) {
            throw new IllegalArgumentException("品种不存在: " + request.getVarietyId());
        }
        validateStages(request.getStages());

        LightRecipe recipe = new LightRecipe();
        recipe.setRecipeCode(request.getRecipeCode());
        recipe.setVarietyId(variety.getId());
        recipe.setVersionNo(nextVersionNo(request.getRecipeCode()));
        recipe.setStatus(LightStatus.RECIPE_DRAFT);
        recipe.setRemark(request.getRemark());
        try {
            recipeMapper.insert(recipe);
        } catch (DuplicateKeyException e) {
            // 唯一约束兜底：并发创建同版本时只允许一个成功
            throw new IllegalStateException("配方版本冲突，请重试: " + request.getRecipeCode());
        }
        insertStages(recipe.getId(), request.getStages());
        return recipe;
    }

    /** 发布：同配方编码的旧发布版归档；已开始阶段不受影响（沿用快照），未开始阶段用新版本。 */
    @Transactional
    public LightRecipe publish(Long recipeId) {
        LightRecipe recipe = requireRecipe(recipeId);
        if (!LightStatus.RECIPE_DRAFT.equals(recipe.getStatus())) {
            throw new IllegalStateException("仅草稿版本可发布，当前状态: " + recipe.getStatus());
        }
        List<LightRecipeStage> stages = stagesOf(recipeId);
        if (stages.isEmpty()) {
            throw new IllegalStateException("配方没有阶段明细，不能发布: " + recipe.getRecipeCode());
        }

        LightRecipe current = publishedForCode(recipe.getRecipeCode());
        if (current != null) {
            current.setStatus(LightStatus.RECIPE_ARCHIVED);
            recipeMapper.updateById(current);
        }
        recipe.setStatus(LightStatus.RECIPE_PUBLISHED);
        recipe.setPublishedAt(LocalDateTime.now());
        recipe.setPublishedBy(CurrentOperator.id());
        recipeMapper.updateById(recipe);
        return recipe;
    }

    public LightRecipe requireRecipe(Long recipeId) {
        LightRecipe recipe = recipeMapper.selectById(recipeId);
        if (recipe == null) {
            throw new IllegalArgumentException("光配方不存在: " + recipeId);
        }
        return recipe;
    }

    public List<LightRecipe> list(Long varietyId, String status) {
        return recipeMapper.selectList(new QueryWrapper<LightRecipe>()
                .eq(varietyId != null, "variety_id", varietyId)
                .eq(status != null, "status", status)
                .orderByAsc("recipe_code")
                .orderByDesc("version_no"));
    }

    public List<LightRecipeStage> stagesOf(Long recipeId) {
        return stageMapper.selectList(new QueryWrapper<LightRecipeStage>()
                .eq("recipe_id", recipeId)
                .orderByAsc("day_from"));
    }

    /** 品种当前已发布版本（无则 null）。 */
    public LightRecipe publishedForVariety(Long varietyId) {
        return recipeMapper.selectOne(new QueryWrapper<LightRecipe>()
                .eq("variety_id", varietyId)
                .eq("status", LightStatus.RECIPE_PUBLISHED)
                .orderByDesc("version_no")
                .last("LIMIT 1"));
    }

    private LightRecipe publishedForCode(String recipeCode) {
        return recipeMapper.selectOne(new QueryWrapper<LightRecipe>()
                .eq("recipe_code", recipeCode)
                .eq("status", LightStatus.RECIPE_PUBLISHED)
                .last("LIMIT 1"));
    }

    private int nextVersionNo(String recipeCode) {
        LightRecipe latest = recipeMapper.selectOne(new QueryWrapper<LightRecipe>()
                .eq("recipe_code", recipeCode)
                .orderByDesc("version_no")
                .last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    private void insertStages(Long recipeId, List<LightStageRequest> stages) {
        for (LightStageRequest s : stages) {
            LightRecipeStage stage = new LightRecipeStage();
            stage.setRecipeId(recipeId);
            stage.setStageCode(s.getStageCode());
            stage.setStageName(s.getStageName());
            stage.setDayFrom(s.getDayFrom());
            stage.setDayTo(s.getDayTo());
            stage.setSpectrumRed(s.getSpectrumRed());
            stage.setSpectrumBlue(s.getSpectrumBlue());
            stage.setSpectrumFarRed(s.getSpectrumFarRed());
            stage.setBrightnessPpfd(s.getBrightnessPpfd());
            stage.setLightOn(s.getLightOn());
            stage.setLightOff(s.getLightOff());
            stageMapper.insert(stage);
        }
    }

    /**
     * 阶段链校验：从播种第 0 天开始、区间连续衔接不重叠、仅末段可开放；
     * 每段光谱合计 100、时段合法。与 LightTimeRules 的左闭右开归属口径一致。
     */
    static void validateStages(List<? extends LightStageRequest> stages) {
        List<LightStageRequest> sorted = new ArrayList<>(stages);
        sorted.sort(Comparator.comparing(LightStageRequest::getDayFrom));
        if (sorted.get(0).getDayFrom() != 0) {
            throw new IllegalArgumentException("阶段链必须从播种第 0 天开始");
        }
        for (int i = 0; i < sorted.size(); i++) {
            LightStageRequest s = sorted.get(i);
            LightParamChecks.checkSpectrum(s.getSpectrumRed(), s.getSpectrumBlue(), s.getSpectrumFarRed());
            LightParamChecks.checkBrightness(s.getBrightnessPpfd());
            LightParamChecks.checkPeriod(s.getLightOn(), s.getLightOff());
            if (s.getDayTo() != null && s.getDayTo() <= s.getDayFrom()) {
                throw new IllegalArgumentException("阶段结束天数必须大于起始天数: " + s.getStageCode());
            }
            boolean last = i == sorted.size() - 1;
            if (last) {
                continue; // 末段允许 day_to 空（开放段）
            }
            if (s.getDayTo() == null) {
                throw new IllegalArgumentException("仅最后一个阶段允许不填结束天数: " + s.getStageCode());
            }
            LightStageRequest next = sorted.get(i + 1);
            if (!next.getDayFrom().equals(s.getDayTo())) {
                throw new IllegalArgumentException("阶段区间必须连续衔接: "
                        + s.getStageCode() + " 结束于第 " + s.getDayTo() + " 天，"
                        + next.getStageCode() + " 起始于第 " + next.getDayFrom() + " 天");
            }
        }
    }
}
