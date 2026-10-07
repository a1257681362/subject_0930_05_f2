package com.evops.plant.domain;

/** 植物工厂台账业务状态常量。 */
public final class PfStatus {
    private PfStatus() {
    }

    public static final String ACTIVE = "ACTIVE";

    // 播种盘
    public static final String TRAY_IDLE = "IDLE";
    public static final String TRAY_IN_USE = "IN_USE";

    // 种植批次
    public static final String BATCH_SOWN = "SOWN";
    public static final String BATCH_GROWING = "GROWING";
    public static final String BATCH_MOVED = "MOVED";
    public static final String BATCH_HARVESTED = "HARVESTED";

    // 质检
    public static final String QC_PENDING = "PENDING";
    public static final String QC_PASSED = "PASSED";
    public static final String QC_REJECTED = "REJECTED";

    // 光配方版本
    public static final String RECIPE_DRAFT = "DRAFT";
    public static final String RECIPE_PUBLISHED = "PUBLISHED";

    // 批次阶段版本绑定
    public static final String BINDING_ACTIVE = "ACTIVE";
    public static final String BINDING_FINISHED = "FINISHED";

    // 光控下发结果
    public static final String DISPATCH_SUCCESS = "SUCCESS";
    public static final String DISPATCH_FAILED = "FAILED";
    /** 下发来源：RECIPE 配方整点切换 / OVERRIDE 人工临时调整 / RESTORE 到期恢复 / RETRY 失败重试 */
    public static final String SOURCE_RECIPE = "RECIPE";
    public static final String SOURCE_OVERRIDE = "OVERRIDE";
    public static final String SOURCE_RESTORE = "RESTORE";
    public static final String SOURCE_RETRY = "RETRY";

    // 失败重试队列
    public static final String RETRY_PENDING = "PENDING";
    public static final String RETRY_DONE = "DONE";
    public static final String RETRY_GIVEN_UP = "GIVEN_UP";

    // 人工临时调整
    public static final String OVERRIDE_ACTIVE = "ACTIVE";
    public static final String OVERRIDE_EXPIRED = "EXPIRED";
    public static final String OVERRIDE_REVOKED = "REVOKED";
}
