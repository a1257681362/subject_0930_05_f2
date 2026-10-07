package com.evops.plant.domain;

/** 植物光配方业务状态常量。 */
public final class LightStatus {
    private LightStatus() {
    }

    // 配方版本
    public static final String RECIPE_DRAFT = "DRAFT";
    public static final String RECIPE_PUBLISHED = "PUBLISHED";
    public static final String RECIPE_ARCHIVED = "ARCHIVED";

    // 下发单
    public static final String DISPATCH_PENDING = "PENDING";
    public static final String DISPATCH_ISSUED = "ISSUED";
    public static final String DISPATCH_PARTIAL = "PARTIAL";
    public static final String DISPATCH_FAILED = "FAILED";

    // 设备执行项（重试队列）
    public static final String DEVICE_PENDING = "PENDING";
    public static final String DEVICE_SUCCESS = "SUCCESS";
    public static final String DEVICE_FAILED = "FAILED";
    public static final String DEVICE_RETRYING = "RETRYING";
    public static final String DEVICE_DEAD = "DEAD";

    // 人工临时调整
    public static final String OVERRIDE_ACTIVE = "ACTIVE";
    public static final String OVERRIDE_EXPIRED = "EXPIRED";
    public static final String OVERRIDE_REVOKED = "REVOKED";

    // 设备
    public static final String DEVICE_ACTIVE = "ACTIVE";
}
