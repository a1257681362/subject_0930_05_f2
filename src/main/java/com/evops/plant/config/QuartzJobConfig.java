package com.evops.plant.config;

import com.evops.plant.job.LightDispatchJob;
import com.evops.plant.job.StartupBatchJob;
import org.quartz.CronScheduleBuilder;
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

    public static final String LIGHT_DISPATCH_JOB_IDENTITY = "pfLightDispatchJob";

    @Bean
    public JobDetail lightDispatchJobDetail() {
        return JobBuilder.newJob(LightDispatchJob.class)
                .storeDurably()
                .withIdentity(LIGHT_DISPATCH_JOB_IDENTITY)
                .withDescription("植物光配方按棚区下发（整点生效，失败设备重试）")
                .build();
    }

    /**
     * 每 5 分钟整（0/5/10...分）触发：切换只在整点生效（effective_at 到点才下发），
     * 5 分钟节拍同时承担失败设备重试；任务幂等，重启安全。
     */
    @Bean
    public Trigger lightDispatchTrigger(JobDetail lightDispatchJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(lightDispatchJobDetail)
                .withIdentity("pfLightDispatchTrigger")
                .startNow()
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0/5 * * * ?"))
                .build();
    }
}
