package com.evops.plant.controller;

import com.evops.common.ApiResponse;
import com.evops.plant.dto.LightOverrideRequest;
import com.evops.plant.dto.LightRecipeDetail;
import com.evops.plant.dto.LightRecipeRequest;
import com.evops.plant.entity.LightDispatch;
import com.evops.plant.entity.LightDispatchRetry;
import com.evops.plant.entity.LightManualOverride;
import com.evops.plant.entity.LightRecipe;
import com.evops.plant.service.LightDispatchService;
import com.evops.plant.service.LightRecipeService;
import com.evops.plant.support.SpringContextHolder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 植物光配方接口（HTTP Basic）：
 * 配方配置（草稿/发布/查询）、人工临时调整（强制结束时间+审批人）、
 * 下发流水/重试队列查询、定时任务手动触发（联调用）。
 */
@RestController
@RequestMapping("/api/pf/light")
public class LightRecipeController {

    private final LightRecipeService recipeService;
    private final LightDispatchService dispatchService;
    private final Scheduler scheduler;

    public LightRecipeController(LightRecipeService recipeService,
                                 LightDispatchService dispatchService, Scheduler scheduler) {
        this.recipeService = recipeService;
        this.dispatchService = dispatchService;
        this.scheduler = scheduler;
    }

    // ------------------------------------------------ 配方配置

    /** 创建草稿配方（按品种+生长阶段+时段配置光谱比例与亮度）。 */
    @PostMapping("/recipes")
    public ApiResponse<LightRecipe> createDraft(@Valid @RequestBody LightRecipeRequest request) {
        return ApiResponse.ok(recipeService.createDraft(request));
    }

    /** 发布配方：生效时间为发布时刻的下一整点（整点切换，不立即打断当前阶段/时段）。 */
    @PostMapping("/recipes/{id}/publish")
    public ApiResponse<LightRecipe> publish(@PathVariable Long id) {
        return ApiResponse.ok(recipeService.publish(id));
    }

    @GetMapping("/recipes/{id}")
    public ApiResponse<LightRecipeDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(recipeService.getDetail(id));
    }

    @GetMapping("/recipes")
    public ApiResponse<List<LightRecipe>> list(@RequestParam(required = false) Long varietyId) {
        return ApiResponse.ok(recipeService.listByVariety(varietyId));
    }

    // ------------------------------------------------ 人工临时调整

    /** 登记人工临时调整（必须带 endAt 结束时间与 approverId 审批人，否则 400）。 */
    @PostMapping("/overrides")
    public ApiResponse<LightManualOverride> createOverride(@Valid @RequestBody LightOverrideRequest request) {
        return ApiResponse.ok(dispatchService.createOverride(request));
    }

    /** 提前撤销人工调整（留痕为 REVOKED，下一整点恢复配方）。 */
    @PostMapping("/overrides/{id}/revoke")
    public ApiResponse<LightManualOverride> revokeOverride(@PathVariable Long id) {
        return ApiResponse.ok(dispatchService.revokeOverride(id));
    }

    @GetMapping("/overrides")
    public ApiResponse<List<LightManualOverride>> listOverrides(
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(dispatchService.listOverrides(status));
    }

    // ------------------------------------------------ 执行记录

    @GetMapping("/dispatches")
    public ApiResponse<List<LightDispatch>> listDispatches(
            @RequestParam(required = false) String result) {
        return ApiResponse.ok(dispatchService.listDispatches(result));
    }

    @GetMapping("/retries")
    public ApiResponse<List<LightDispatchRetry>> listRetries(
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(dispatchService.listRetries(status));
    }

    // ------------------------------------------------ 定时任务手动触发

    /** 手动触发整点巡检（按当前整点，幂等）。 */
    @PostMapping("/dispatch/run")
    public ApiResponse<Map<String, Object>> runDispatch() throws SchedulerException {
        JobKey key = JobKey.jobKey("pfLightDispatchJob");
        boolean exists = scheduler.checkExists(key);
        if (exists) {
            scheduler.triggerJob(key);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("jobExists", exists);
        result.put("triggered", true);
        return ApiResponse.ok(result);
    }

    /** 手动触发失败重试扫描。 */
    @PostMapping("/retries/run")
    public ApiResponse<Map<String, Object>> runRetry() throws SchedulerException {
        JobKey key = JobKey.jobKey("pfLightRetryJob");
        boolean exists = scheduler.checkExists(key);
        if (exists) {
            scheduler.triggerJob(key);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("jobExists", exists);
        result.put("triggered", true);
        return ApiResponse.ok(result);
    }

    /** 不经过 Quartz、同步执行一次整点巡检（便于起步核对/联调即时拿到结果）。 */
    @PostMapping("/dispatch/tick")
    public ApiResponse<Map<String, Object>> tickNow() {
        int created = SpringContextHolder.getBean(LightDispatchService.class).dispatchTick();
        Map<String, Object> result = new HashMap<>();
        result.put("created", created);
        return ApiResponse.ok(result);
    }
}
