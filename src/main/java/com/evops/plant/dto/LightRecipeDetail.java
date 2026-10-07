package com.evops.plant.dto;

import com.evops.plant.entity.LightRecipe;
import com.evops.plant.entity.LightRecipeSlot;
import com.evops.plant.entity.LightRecipeStage;
import lombok.Data;

import java.util.List;

/** 光配方详情（头 + 阶段 + 时段）。 */
@Data
public class LightRecipeDetail {
    private LightRecipe recipe;
    private List<StageView> stages;

    @Data
    public static class StageView {
        private LightRecipeStage stage;
        private List<LightRecipeSlot> slots;
    }
}
