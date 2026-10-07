package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 温室：记录光照（光源/光周期/PPFD）与营养液循环（模式/EC/pH/循环间隔）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_greenhouse")
public class Greenhouse extends BaseEntity {
    private String ghCode;
    private String ghName;
    private String lightSource;
    private Integer photoperiodHours;
    private Integer ppfd;
    private String nutrientCycleMode;
    private BigDecimal nutrientEc;
    private BigDecimal nutrientPh;
    private Integer cycleIntervalMin;
    private String status;
    private String remark;
}
