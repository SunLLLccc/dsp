package com.fintechervision.dsp.engine.executor;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class SqlExecutor {

    private final JdbcTemplate jdbcTemplate;

    public SqlExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> query(String datasource, String sql, Object... params) {
        log.debug("执行SQL查询: datasource={}, sql={}, params={}", datasource, sql, Arrays.toString(params));
        try {
            DynamicDataSourceContextHolder.push(datasource);
            return jdbcTemplate.queryForList(sql, params);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    public List<Map<String, Object>> query(String datasource, String sql) {
        log.debug("执行SQL查询: datasource={}, sql={}", datasource, sql);
        try {
            DynamicDataSourceContextHolder.push(datasource);
            return jdbcTemplate.queryForList(sql);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }
}
