package com.evops.plant.domain;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/** 光配方参数校验：光谱三项合计 100、时段 HH:mm 且起止不同。配方与人工临时调整共用。 */
public final class LightParamChecks {

    private LightParamChecks() {
    }

    public static void checkSpectrum(Integer red, Integer blue, Integer farRed) {
        if (red == null || blue == null || farRed == null) {
            throw new IllegalArgumentException("光谱比例红/蓝/远红三项均不能为空");
        }
        if (red < 0 || blue < 0 || farRed < 0) {
            throw new IllegalArgumentException("光谱比例不能为负数");
        }
        if (red + blue + farRed != 100) {
            throw new IllegalArgumentException("光谱比例红/蓝/远红合计必须为 100，当前: " + (red + blue + farRed));
        }
    }

    public static void checkBrightness(Integer brightnessPpfd) {
        if (brightnessPpfd == null || brightnessPpfd < 1) {
            throw new IllegalArgumentException("亮度 PPFD 必须为正整数");
        }
    }

    /** 校验并解析 HH:mm 时段；起止相同视为非法（不表示任何开灯区间）。 */
    public static void checkPeriod(String lightOn, String lightOff) {
        LocalTime on = parseTime(lightOn, "开灯时间");
        LocalTime off = parseTime(lightOff, "关灯时间");
        if (on.equals(off)) {
            throw new IllegalArgumentException("开灯与关灯时间不能相同: " + lightOn);
        }
    }

    public static LocalTime parseTime(String hhmm, String fieldName) {
        if (hhmm == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        try {
            return LocalTime.parse(hhmm);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + "格式必须为 HH:mm: " + hhmm);
        }
    }
}
