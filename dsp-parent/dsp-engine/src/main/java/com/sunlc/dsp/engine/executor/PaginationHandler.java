package com.sunlc.dsp.engine.executor;

import com.sunlc.dsp.engine.model.PaginationConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PaginationHandler {

    public PaginationResult rewrite(String sql, PaginationConfig paginationConfig,
                                     Map<String, Object> params, List<Object> sqlParams) {
        if (paginationConfig == null || paginationConfig.getMode() == null) {
            return new PaginationResult(sql, sqlParams, false, null);
        }

        int pageSize = getPageSize(params, paginationConfig);
        String orderBy = paginationConfig.getOrderBy();
        if (orderBy == null || orderBy.isEmpty()) {
            orderBy = "id";
        }

        switch (paginationConfig.getMode()) {
            case CURSOR:
                return rewriteCursor(sql, orderBy, pageSize, paginationConfig, params, sqlParams);
            case OPTIMIZED:
                return rewriteOptimized(sql, orderBy, pageSize, paginationConfig, params, sqlParams);
            default:
                return new PaginationResult(sql, sqlParams, false, null);
        }
    }

    private PaginationResult rewriteCursor(String sql, String orderBy, int pageSize,
                                             PaginationConfig config, Map<String, Object> params,
                                             List<Object> sqlParams) {
        StringBuilder sb = new StringBuilder(sql);
        Object lastId = params.get(config.getLastIdParam());
        boolean hasLastId = lastId != null && !lastId.toString().isEmpty();

        if (hasLastId) {
            sb.append(" AND ").append(orderBy).append(" > ?");
            sqlParams.add(convertId(lastId));
        }

        sb.append(" ORDER BY ").append(orderBy);
        sb.append(" LIMIT ?");
        sqlParams.add(pageSize);

        String newSql = sb.toString();
        log.debug("游标分页SQL改写: {} -> {}", sql, newSql);
        return new PaginationResult(newSql, sqlParams, true, "cursor");
    }

    private PaginationResult rewriteOptimized(String sql, String orderBy, int pageSize,
                                                PaginationConfig config, Map<String, Object> params,
                                                List<Object> sqlParams) {
        int pageNum = getPageNum(params, config);
        int offset = (pageNum - 1) * pageSize;

        if (offset == 0) {
            String newSql = sql + " ORDER BY " + orderBy + " LIMIT ?";
            sqlParams.add(pageSize);
            log.debug("优化分页(第一页): {} -> {}", sql, newSql);
            return new PaginationResult(newSql, sqlParams, true, "optimized");
        }

        String upperSql = sql.toUpperCase();
        int fromIndex = upperSql.indexOf(" FROM ") + 6;
        int whereIndex = upperSql.indexOf(" WHERE ");

        String afterFrom;
        String whereClause = "";
        if (whereIndex > 0) {
            afterFrom = sql.substring(fromIndex, whereIndex).trim();
            whereClause = sql.substring(whereIndex);
        } else {
            afterFrom = sql.substring(fromIndex).trim();
        }

        String tableName = afterFrom.split("\\s+")[0];

        StringBuilder sb = new StringBuilder();
        sb.append(sql);
        if (whereIndex > 0) {
            sb.append(" AND ").append(orderBy).append(" >= (SELECT ").append(orderBy)
              .append(" FROM ").append(tableName).append(" ").append(whereClause)
              .append(" ORDER BY ").append(orderBy).append(" LIMIT ?, 1)");
        } else {
            sb.append(" WHERE ").append(orderBy).append(" >= (SELECT ").append(orderBy)
              .append(" FROM ").append(tableName)
              .append(" ORDER BY ").append(orderBy).append(" LIMIT ?, 1)");
        }

        sqlParams.add(offset);
        sb.append(" ORDER BY ").append(orderBy).append(" LIMIT ?");
        sqlParams.add(pageSize);

        String newSql = sb.toString();
        log.debug("优化分页SQL改写: {} -> {}", sql, newSql);
        return new PaginationResult(newSql, sqlParams, true, "optimized");
    }

    private int getPageSize(Map<String, Object> params, PaginationConfig config) {
        Object value = params.get(config.getPageSizeParam());
        if (value == null) return config.getDefaultPageSize();
        try {
            int pageSize = Integer.parseInt(value.toString());
            return Math.min(pageSize, config.getMaxPageSize());
        } catch (NumberFormatException e) {
            return config.getDefaultPageSize();
        }
    }

    private int getPageNum(Map<String, Object> params, PaginationConfig config) {
        Object value = params.get(config.getPageNumParam());
        if (value == null) return 1;
        try {
            int pageNum = Integer.parseInt(value.toString());
            return Math.max(pageNum, 1);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private Object convertId(Object idValue) {
        if (idValue == null) return null;
        try {
            return Long.parseLong(idValue.toString());
        } catch (NumberFormatException e) {
            return idValue;
        }
    }

    public static class PaginationResult {
        public final String sql;
        public final List<Object> params;
        public final boolean paginated;
        public final String mode;

        public PaginationResult(String sql, List<Object> params, boolean paginated, String mode) {
            this.sql = sql;
            this.params = params;
            this.paginated = paginated;
            this.mode = mode;
        }
    }
}
