package com.evops.plant.job;

import com.evops.plant.service.LightDispatchService;
import com.evops.plant.support.SpringContextHolder;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * 光配方整点下发任务：每小时第 0 分触发（cron 0 0 * * * ?）。
 * 业务层按"棚区 + 整点"幂等，重复触发/重启不多发；无 Shiro 主体，操作者兜底系统(0)。
 */
public class LightDispatchJob extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(LightDispatchJob.class);

    @Override
    protected void executeInternal(JobExecutionContext context) {
        try {
            int created = SpringContextHolder.getBean(LightDispatchService.class).dispatchTick();
            log.info("[光配方整点任务] 本次新下发 {} 个棚区", created);
        } catch (Exception e) {
            // 单棚区异常已在业务层隔离；整体异常不外抛，下个整点继续
            log.error("[光配方整点任务] 执行失败: {}", e.getMessage(), e);
        }
    }
}
