package com.evops.plant.gateway;

/** 棚区灯具下发指令（由下发流水的快照内容构造）。 */
public class LightCommand {
    private final Long greenhouseId;
    private final Long zoneId;
    private final Long batchId;
    private final int brightnessPct;
    private final int ratioRedPct;
    private final int ratioGreenPct;
    private final int ratioBluePct;
    private final int ratioWhitePct;
    private final String source;

    public LightCommand(Long greenhouseId, Long zoneId, Long batchId,
                        int brightnessPct, int ratioRedPct, int ratioGreenPct,
                        int ratioBluePct, int ratioWhitePct, String source) {
        this.greenhouseId = greenhouseId;
        this.zoneId = zoneId;
        this.batchId = batchId;
        this.brightnessPct = brightnessPct;
        this.ratioRedPct = ratioRedPct;
        this.ratioGreenPct = ratioGreenPct;
        this.ratioBluePct = ratioBluePct;
        this.ratioWhitePct = ratioWhitePct;
        this.source = source;
    }

    public Long getGreenhouseId() { return greenhouseId; }
    public Long getZoneId() { return zoneId; }
    public Long getBatchId() { return batchId; }
    public int getBrightnessPct() { return brightnessPct; }
    public int getRatioRedPct() { return ratioRedPct; }
    public int getRatioGreenPct() { return ratioGreenPct; }
    public int getRatioBluePct() { return ratioBluePct; }
    public int getRatioWhitePct() { return ratioWhitePct; }
    public String getSource() { return source; }

    /** 失败设备匹配键：温室:分区（分区为空用 *）。 */
    public String failKey() {
        return greenhouseId + ":" + (zoneId == null ? "*" : zoneId);
    }
}
