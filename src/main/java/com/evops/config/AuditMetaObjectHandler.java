package com.evops.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.evops.common.CurrentOperator;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充：create_by/update_by/create_time/update_time。
 * 操作者统一经 CurrentOperator 解析，定时任务线程兜底系统(0)，不丢操作者。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long operatorId = CurrentOperator.id();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createBy", Long.class, operatorId);
        this.strictInsertFill(metaObject, "updateBy", Long.class, operatorId);
    }

    @Override
    public void updateFill(MetaObject entity) {
        Long operatorId = CurrentOperator.id();
        this.strictUpdateFill(entity, "updateTime", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(entity, "updateBy", Long.class, operatorId);
    }
}
