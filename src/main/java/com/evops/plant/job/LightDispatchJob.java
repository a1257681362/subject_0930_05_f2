package com.evops.plant.job;

import com.evops.plant.service.LightDispatchService;
import com.evops.plant.support.SpringContextHolder;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.LocalDateTime;

/**
 * 光配方下发任务：每 5 分钟整点节拍触发一次。
 * 业务层幂等——切换只在整点生效（effective_at 到点才下发），失败设备进重试队列；
 * 该线程无 Shiro 主体，操作者兜底为系统(0)。
 */
public class LightDispatchJob extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(LightDispatchJob.class);

    @Override
    protected void executeInternal(JobExecutionContext context) {
        try {
            LightDispatchService dispatchService = SpringContextHolder.getBean(LightDispatchService.class);
            dispatchService.runOnce(LocalDateTime.now());
        } catch (Exception e) {
            // 幂等任务异常不外抛，避免 Quartz 反复刷错误；下次触发仍会重试
            log.error("[光配方下发] 任务执行失败: {}", e.getMessage(), e);
        }
    }
}
