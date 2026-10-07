package com.evops.plant.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

/**
 * 下发快照载荷：写入 t_pf_light_dispatch.payload_json 的内容。
 * 历史执行保留快照——重试时也以快照内容重建指令，配方后续改动不溯及既往。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LightPayload {

    private Long recipeId;
    private Integer recipeVersionNo;
    private String stageCode;
    private Integer slotStartMinute;
    private Integer brightnessPct;
    private Integer ratioRedPct;
    private Integer ratioGreenPct;
    private Integer ratioBluePct;
    private Integer ratioWhitePct;
    private String source;
    private LocalDateTime snapshotAt;
    /** 人工调整时留痕：审批人与原因 */
    private Long approverId;
    private String reason;

    public String toJson(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("光配方快照序列化失败", e);
        }
    }

    public static LightPayload fromJson(ObjectMapper objectMapper, String json) {
        try {
            return objectMapper.readValue(json, LightPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("光配方快照反序列化失败", e);
        }
    }

    public Long getRecipeId() { return recipeId; }
    public void setRecipeId(Long recipeId) { this.recipeId = recipeId; }
    public Integer getRecipeVersionNo() { return recipeVersionNo; }
    public void setRecipeVersionNo(Integer recipeVersionNo) { this.recipeVersionNo = recipeVersionNo; }
    public String getStageCode() { return stageCode; }
    public void setStageCode(String stageCode) { this.stageCode = stageCode; }
    public Integer getSlotStartMinute() { return slotStartMinute; }
    public void setSlotStartMinute(Integer slotStartMinute) { this.slotStartMinute = slotStartMinute; }
    public Integer getBrightnessPct() { return brightnessPct; }
    public void setBrightnessPct(Integer brightnessPct) { this.brightnessPct = brightnessPct; }
    public Integer getRatioRedPct() { return ratioRedPct; }
    public void setRatioRedPct(Integer ratioRedPct) { this.ratioRedPct = ratioRedPct; }
    public Integer getRatioGreenPct() { return ratioGreenPct; }
    public void setRatioGreenPct(Integer ratioGreenPct) { this.ratioGreenPct = ratioGreenPct; }
    public Integer getRatioBluePct() { return ratioBluePct; }
    public void setRatioBluePct(Integer ratioBluePct) { this.ratioBluePct = ratioBluePct; }
    public Integer getRatioWhitePct() { return ratioWhitePct; }
    public void setRatioWhitePct(Integer ratioWhitePct) { this.ratioWhitePct = ratioWhitePct; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(LocalDateTime snapshotAt) { this.snapshotAt = snapshotAt; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
