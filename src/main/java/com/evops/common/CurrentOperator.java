package com.evops.common;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 当前操作者解析：HTTP 请求取 Shiro 登录主体；定时任务等无主体线程兜底为系统操作者(0)，
 * 任何写库路径都不会丢失操作者。
 */
public final class CurrentOperator {

    /** 系统操作者（定时任务/兜底），对应 t_pf_operator.id = 0 */
    public static final long SYSTEM_OPERATOR_ID = 0L;

    private static final Map<String, Long> ACCOUNT_TO_OPERATOR;

    static {
        Map<String, Long> m = new HashMap<>();
        m.put("bootstrap", 1L);
        ACCOUNT_TO_OPERATOR = Collections.unmodifiableMap(m);
    }

    private CurrentOperator() {
    }

    public static Long id() {
        try {
            Subject subject = SecurityUtils.getSubject();
            Object principal = subject.getPrincipal();
            if (principal == null) {
                return SYSTEM_OPERATOR_ID;
            }
            return ACCOUNT_TO_OPERATOR.getOrDefault(String.valueOf(principal), SYSTEM_OPERATOR_ID);
        } catch (Exception e) {
            // 非 Web 线程（如 Quartz / 启动回调）没有绑定 Shiro 主体
            return SYSTEM_OPERATOR_ID;
        }
    }
}
