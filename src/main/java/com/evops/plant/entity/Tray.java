package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 播种盘。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_tray")
public class Tray extends BaseEntity {
    private String trayCode;
    private String spec;
    private Integer holeCount;
    /** IDLE-空闲 IN_USE-使用中 */
    private String status;
}
