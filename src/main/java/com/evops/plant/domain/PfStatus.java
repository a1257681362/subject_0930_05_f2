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
}
