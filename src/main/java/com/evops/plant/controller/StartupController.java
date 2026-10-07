package com.evops.plant.controller;

import com.evops.common.ApiResponse;
import com.evops.plant.entity.Batch;
import com.evops.plant.service.BatchService;
import com.evops.plant.support.SpringContextHolder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** 起步任务核对接口：重启后可手动触发并检查起步批次（幂等，不多写）。 */
@RestController
@RequestMapping("/api/pf/startup")
public class StartupController {

    private final Scheduler scheduler;

    public StartupController(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** 手动触发一次起步定时任务。 */
    @PostMapping("/run")
    public ApiResponse<Map<String, Object>> runNow() throws SchedulerException {
        boolean exists = scheduler.checkExists(JobKey.jobKey("pfStartupBatchJob"));
        if (exists) {
            scheduler.triggerJob(JobKey.jobKey("pfStartupBatchJob"));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("jobExists", exists);
        result.put("triggered", true);
        return ApiResponse.ok(result);
    }

    /** 查看起步批次当前状态（不存在时 data 为 null）。 */
    @GetMapping("/batch")
    public ApiResponse<Batch> startupBatch() {
        return ApiResponse.ok(SpringContextHolder.getBean(BatchService.class)
                .listBatches(null).stream()
                .filter(b -> BatchService.STARTUP_FLAG_YES.equals(b.getStartupFlag()))
                .findFirst().orElse(null));
    }
}
