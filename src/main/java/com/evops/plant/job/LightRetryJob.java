package com.evops.plant.job;

import com.evops.plant.service.LightDispatchService;
import com.evops.plant.support.SpringContextHolder;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * 失败设备重试任务：每 5 分钟扫描重试队列到期记录，按冻结快照重发，
 * 成功置 DONE，超过 {@link LightDispatchService#MAX_ATTEMPTS} 次置 GIVEN_UP 待人工介入。
 */
public class LightRetryJob extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(LightRetryJob.class);

    @Override
    protected void executeInternal(JobExecutionContext context) {
        try {
            int succeeded = SpringContextHolder.getBean(LightDispatchService.class).retryDue();
            if (succeeded > 0) {
                log.info("[光配方重试任务] 重试成功 {} 个棚区", succeeded);
            }
        } catch (Exception e) {
            log.error("[光配方重试任务] 执行失败: {}", e.getMessage(), e);
        }
    }
}
