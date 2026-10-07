package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 设备分区，隶属于温室。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_zone")
public class Zone extends BaseEntity {
    private Long greenhouseId;
    private String zoneCode;
    private String zoneName;
    private String functionType;
    private String equipmentDesc;
    private String status;
}
