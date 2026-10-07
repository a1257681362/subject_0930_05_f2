package com.evops.plant.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * 业务唯一编号生成：H2 序列重启不回退，配合数据库唯一约束保证批次号/采收单号全局唯一。
 */
@Component
public class NumberGenerator {

    private static final DateTimeFormatter PREFIX_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final JdbcTemplate jdbcTemplate;

    public NumberGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 种植批次号：PFB-yyMMdd-序列号 */
    public String nextBatchNo() {
        return "PFB-" + java.time.LocalDate.now().format(PREFIX_DATE) + "-" + nextValue("seq_pf_batch_no");
    }

    /** 采收单号：PFH-yyMMdd-序列号 */
    public String nextHarvestNo() {
        return "PFH-" + java.time.LocalDate.now().format(PREFIX_DATE) + "-" + nextValue("seq_pf_harvest_no");
    }

    private long nextValue(String sequence) {
        Long value = jdbcTemplate.queryForObject("SELECT NEXT VALUE FOR " + sequence, Long.class);
        if (value == null) {
            throw new IllegalStateException("编号序列取值失败: " + sequence);
        }
        return value;
    }
}
