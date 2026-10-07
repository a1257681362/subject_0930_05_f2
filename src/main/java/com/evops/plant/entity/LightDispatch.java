package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 整点下发单（历史执行快照）：光谱/亮度/时段全量冗余自配方或临时调整，
 * 配方后续变更不影响已生成记录。每个棚区每个生效整点至多一单，仅参数切换时建单。
 * status: PENDING 待下发 / ISSUED 全部成功 / PARTIAL 部分成功 / FAILED 全部失败
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pf_light_dispatch")
public class LightDispatch extends BaseEntity {
    private Long zoneId;
    private Long greenhouseId;
    private Long batchId;
    private Long recipeId;
    private Integer recipeVersion;
    private String stageCode;
    /** 非空表示本单参数来自人工临时调整 */
    private Long overrideId;
    private Integer spectrumRed;
    private Integer spectrumBlue;
    private Integer spectrumFarRed;
    private Integer brightnessPpfd;
    private String lightOn;
    private String lightOff;
    /** 生效整点（只取整点时刻）；到点才允许下发，保证切换整点生效 */
    private LocalDateTime effectiveAt;
    private String status;
}
