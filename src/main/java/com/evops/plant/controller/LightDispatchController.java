package com.evops.plant.controller;

import com.evops.common.ApiResponse;
import com.evops.plant.entity.LightDispatch;
import com.evops.plant.entity.LightDispatchDevice;
import com.evops.plant.service.LightDispatchService;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 光配方下发：整点下发单（历史快照）与设备重试队列查询，支持手动触发一拍。 */
@RestController
@RequestMapping("/api/pf/light-dispatches")
public class LightDispatchController {

    private final LightDispatchService dispatchService;
    private final Scheduler scheduler;

    public LightDispatchController(LightDispatchService dispatchService, Scheduler scheduler) {
        this.dispatchService = dispatchService;
        this.scheduler = scheduler;
    }

    @GetMapping
    public ApiResponse<List<LightDispatch>> list(@RequestParam(required = false) Long zoneId,
                                                 @RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(dispatchService.listDispatches(zoneId, batchId));
    }

    /** 下发单的设备执行项（含重试次数与失败原因）。 */
    @GetMapping("/{dispatchId}/devices")
    public ApiResponse<List<LightDispatchDevice>> devices(@PathVariable Long dispatchId) {
        return ApiResponse.ok(dispatchService.listDeviceItems(dispatchId));
    }

    /** 重试队列：所有未成功也未终结的设备项。 */
    @GetMapping("/retry-queue")
    public ApiResponse<List<LightDispatchDevice>> retryQueue() {
        return ApiResponse.ok(dispatchService.listRetryQueue());
    }

    /** 手动触发一次下发任务（幂等；切换仍在整点生效）。 */
    @PostMapping("/run")
    public ApiResponse<Map<String, Object>> runNow() throws SchedulerException {
        boolean exists = scheduler.checkExists(JobKey.jobKey("pfLightDispatchJob"));
        if (exists) {
            scheduler.triggerJob(JobKey.jobKey("pfLightDispatchJob"));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("jobExists", exists);
        result.put("triggered", true);
        return ApiResponse.ok(result);
    }
}
