package com.evops.plant.service;

import com.evops.plant.dto.LightStageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 配方阶段链校验：从第 0 天开始、连续衔接、仅末段开放、光谱合计 100。 */
class LightRecipeServiceValidateStagesTest {

    private static LightStageRequest stage(String code, Integer from, Integer to,
                                           int r, int b, int fr) {
        LightStageRequest s = new LightStageRequest();
        s.setStageCode(code);
        s.setStageName(code);
        s.setDayFrom(from);
        s.setDayTo(to);
        s.setSpectrumRed(r);
        s.setSpectrumBlue(b);
        s.setSpectrumFarRed(fr);
        s.setBrightnessPpfd(200);
        s.setLightOn("06:00");
        s.setLightOff("22:00");
        return s;
    }

    @Test
    @DisplayName("合法链：[0,14)+[14,∞) 通过")
    void validChain() {
        List<LightStageRequest> stages = Arrays.asList(
                stage("SEEDLING", 0, 14, 60, 30, 10),
                stage("GROWING", 14, null, 70, 20, 10));
        assertDoesNotThrow(() -> LightRecipeService.validateStages(stages));
    }

    @Test
    @DisplayName("光谱合计不为 100 拒绝")
    void spectrumMustSumTo100() {
        List<LightStageRequest> stages = Collections.singletonList(
                stage("SEEDLING", 0, null, 60, 30, 20));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LightRecipeService.validateStages(stages));
        assertTrue(e.getMessage().contains("合计必须为 100"));
    }

    @Test
    @DisplayName("阶段链不从第 0 天开始拒绝")
    void chainMustStartAtZero() {
        List<LightStageRequest> stages = Collections.singletonList(
                stage("GROWING", 1, null, 70, 20, 10));
        assertThrows(IllegalArgumentException.class, () -> LightRecipeService.validateStages(stages));
    }

    @Test
    @DisplayName("阶段区间断档或重叠拒绝（与左闭右开归属口径一致）")
    void chainMustBeContiguous() {
        List<LightStageRequest> gap = Arrays.asList(
                stage("SEEDLING", 0, 14, 60, 30, 10),
                stage("GROWING", 15, null, 70, 20, 10));
        assertThrows(IllegalArgumentException.class, () -> LightRecipeService.validateStages(gap));

        List<LightStageRequest> overlap = Arrays.asList(
                stage("SEEDLING", 0, 15, 60, 30, 10),
                stage("GROWING", 14, null, 70, 20, 10));
        assertThrows(IllegalArgumentException.class, () -> LightRecipeService.validateStages(overlap));
    }

    @Test
    @DisplayName("仅末段允许开放（day_to 空）")
    void onlyLastStageMayBeOpenEnded() {
        List<LightStageRequest> stages = Arrays.asList(
                stage("SEEDLING", 0, null, 60, 30, 10),
                stage("GROWING", 14, null, 70, 20, 10));
        assertThrows(IllegalArgumentException.class, () -> LightRecipeService.validateStages(stages));
    }
}
