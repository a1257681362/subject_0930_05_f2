package com.evops.plant.job;

import com.evops.plant.entity.Batch;
import com.evops.plant.service.BatchService;
import com.evops.plant.support.SpringContextHolder;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * 定时起步任务：确保"植物工厂种植批次台账起步"批次存在。
 * 业务层幂等——每次重启/每次触发都检查，已存在则跳过，绝不多写记录；
 * 该线程无 Shiro 主体，操作者兜底为系统(0)，不丢操作者。
 */
public class StartupBatchJob extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(StartupBatchJob.class);

    @Override
    protected void executeInternal(JobExecutionContext context) {
        try {
            BatchService batchService = SpringContextHolder.getBean(BatchService.class);
            Batch batch = batchService.ensureStartupBatch();
            log.info("[起步任务] 台账起步批次核对完成: batchNo={}, status={}, startupFlag={}",
                    batch.getBatchNo(), batch.getStatus(), batch.getStartupFlag());
        } catch (Exception e) {
            // 幂等任务异常不外抛，避免 Quartz 反复刷错误；下次触发仍会重试
            log.error("[起步任务] 核对失败: {}", e.getMessage(), e);
        }
    }
}
