package com.evops.plant.config;

import com.evops.plant.job.StartupBatchJob;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 起步任务调度：应用启动后立即执行一次，之后每 6 小时巡检一次。
 * 使用 RAMJobStore（默认），任务幂等，重启安全。
 */
@Configuration
public class QuartzJobConfig {

    public static final String STARTUP_JOB_IDENTITY = "pfStartupBatchJob";

    @Bean
    public JobDetail startupBatchJobDetail() {
        return JobBuilder.newJob(StartupBatchJob.class)
                .storeDurably()
                .withIdentity(STARTUP_JOB_IDENTITY)
                .withDescription("植物工厂种植批次台账起步核对")
                .build();
    }

    @Bean
    public Trigger startupBatchTrigger(JobDetail startupBatchJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(startupBatchJobDetail)
                .withIdentity("pfStartupBatchTrigger")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInHours(6)
                        .repeatForever())
                .build();
    }
}
