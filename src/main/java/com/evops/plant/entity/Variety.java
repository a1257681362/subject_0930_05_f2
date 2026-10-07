package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 种植品种。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_variety")
public class Variety extends BaseEntity {
    private String varietyCode;
    private String varietyName;
    private String category;
    private Integer growDays;
    private String status;
}
