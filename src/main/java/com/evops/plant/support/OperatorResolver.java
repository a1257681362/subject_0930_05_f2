package com.evops.plant.support;

import com.evops.common.CurrentOperator;
import com.evops.plant.entity.Operator;
import com.evops.plant.mapper.OperatorMapper;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 操作者姓名解析（带缓存），用于业务留痕快照，任何记录都带操作者，不允许为空。 */
@Component
public class OperatorResolver {

    private final OperatorMapper operatorMapper;
    private final ConcurrentMap<Long, String> nameCache = new ConcurrentHashMap<>();

    public OperatorResolver(OperatorMapper operatorMapper) {
        this.operatorMapper = operatorMapper;
    }

    public String currentOperatorName() {
        return nameOf(CurrentOperator.id());
    }

    public String nameOf(Long operatorId) {
        if (operatorId == null) {
            return "系统（定时任务）";
        }
        return nameCache.computeIfAbsent(operatorId, id -> {
            Operator operator = operatorMapper.selectById(id);
            if (operator != null && operator.getName() != null) {
                return operator.getName();
            }
            return id == CurrentOperator.SYSTEM_OPERATOR_ID ? "系统（定时任务）" : ("操作者#" + id);
        });
    }
}
