package com.evops.plant.controller;

import com.evops.common.ApiResponse;
import com.evops.plant.dto.LightRecipeRequest;
import com.evops.plant.entity.LightRecipe;
import com.evops.plant.entity.LightRecipeStage;
import com.evops.plant.service.LightRecipeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/** 光配方：按品种+生长阶段配置光谱比例、亮度与时段；版本只增不改，发布只影响未开始阶段。 */
@RestController
@RequestMapping("/api/pf/light-recipes")
public class LightRecipeController {

    private final LightRecipeService recipeService;

    public LightRecipeController(LightRecipeService recipeService) {
        this.recipeService = recipeService;
    }

    /** 创建草稿版本（版本号自动递增）。 */
    @PostMapping
    public ApiResponse<LightRecipe> create(@Valid @RequestBody LightRecipeRequest request) {
        return ApiResponse.ok(recipeService.create(request));
    }

    @GetMapping
    public ApiResponse<List<LightRecipe>> list(@RequestParam(required = false) Long varietyId,
                                               @RequestParam(required = false) String status) {
        return ApiResponse.ok(recipeService.list(varietyId, status));
    }

    @GetMapping("/{recipeId}")
    public ApiResponse<LightRecipe> detail(@PathVariable Long recipeId) {
        return ApiResponse.ok(recipeService.requireRecipe(recipeId));
    }

    @GetMapping("/{recipeId}/stages")
    public ApiResponse<List<LightRecipeStage>> stages(@PathVariable Long recipeId) {
        return ApiResponse.ok(recipeService.stagesOf(recipeId));
    }

    /** 发布：同编码旧发布版归档；已开始阶段沿用快照，新版本只影响未开始阶段。 */
    @PostMapping("/{recipeId}/publish")
    public ApiResponse<LightRecipe> publish(@PathVariable Long recipeId) {
        return ApiResponse.ok(recipeService.publish(recipeId));
    }
}
