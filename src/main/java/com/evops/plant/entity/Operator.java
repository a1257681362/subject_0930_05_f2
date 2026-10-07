package com.evops.plant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_pf_operator")
public class Operator {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String operatorNo;
    private String name;
    private String status;
    private LocalDateTime createTime;
}
