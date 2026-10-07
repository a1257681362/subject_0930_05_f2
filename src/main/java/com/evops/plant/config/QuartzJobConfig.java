package com.evops.plant.config;

import com.evops.plant.job.LightDispatchJob;
import com.evops.plant.job.LightRetryJob;
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
 * 定时任务调度：
 * <ul>
 *   <li>起步核对任务：启动立即一次，之后每 6 小时巡检；</li>
 *   <li>光配方整点下发任务：启动立即补跑当前整点，之后每小时第 0 分触发（配方整点生效）；</li>
 *   <li>失败设备重试任务：启动 1 分钟后开始，之后每 5 分钟扫描重试队列。</li>
 * </ul>
 * 使用 RAMJobStore（默认），任务均幂等，重启安全。
 */
@Configuration
public class QuartzJobConfig {

    public static final String STARTUP_JOB_IDENTITY = "pfStartupBatchJob";
    public static final String LIGHT_DISPATCH_JOB_IDENTITY = "pfLightDispatchJob";
    public static final String LIGHT_RETRY_JOB_IDENTITY = "pfLightRetryJob";

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

    @Bean
    public JobDetail lightDispatchJobDetail() {
        return JobBuilder.newJob(LightDispatchJob.class)
                .storeDurably()
                .withIdentity(LIGHT_DISPATCH_JOB_IDENTITY)
                .withDescription("植物光配方整点按棚区下发")
                .build();
    }

    /** 整点触发：每小时第 0 分 0 秒（配方切换整点生效）。 */
    @Bean
    public Trigger lightDispatchTrigger(JobDetail lightDispatchJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(lightDispatchJobDetail)
                .withIdentity("pfLightDispatchTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 * * * ?"))
                .build();
    }

    /**
     * 启动补跑：cron 只在整点触发，单独加一条启动 30 秒后执行一次的触发器
     * （等起步批次先建好；业务层幂等，安全）。
     */
    @Bean
    public Trigger lightDispatchStartupTrigger(JobDetail lightDispatchJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(lightDispatchJobDetail)
                .withIdentity("pfLightDispatchStartupTrigger")
                .startAt(java.util.Date.from(java.time.Instant.now().plusSeconds(30)))
                .build();
    }

    @Bean
    public JobDetail lightRetryJobDetail() {
        return JobBuilder.newJob(LightRetryJob.class)
                .storeDurably()
                .withIdentity(LIGHT_RETRY_JOB_IDENTITY)
                .withDescription("植物光配方失败设备重试队列扫描")
                .build();
    }

    /** 每 5 分钟扫描到期的失败重试记录（退避间隔在业务层控制）。 */
    @Bean
    public Trigger lightRetryTrigger(JobDetail lightRetryJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(lightRetryJobDetail)
                .withIdentity("pfLightRetryTrigger")
                .startAt(java.util.Date.from(java.time.Instant.now().plusSeconds(60)))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(5)
                        .repeatForever())
                .build();
    }
}
