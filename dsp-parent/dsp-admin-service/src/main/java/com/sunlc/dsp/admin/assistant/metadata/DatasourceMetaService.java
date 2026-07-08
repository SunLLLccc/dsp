package com.sunlc.dsp.admin.assistant.metadata;

import com.alibaba.druid.pool.DruidDataSource;
import com.sunlc.dsp.admin.assistant.ai.AssistantProperties;
import com.sunlc.dsp.common.enums.ErrorCode;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.common.util.PasswordEncryptor;
import com.sunlc.dsp.entity.DatasourceConfig;
import com.sunlc.dsp.service.DatasourceManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据源元数据读取服务（Text2API T1）。
 *
 * <p>安全约束（程序级强制，见 design.md 5.2）：
 * <ul>
 *   <li>只读结构（getMetaData），绝不执行业务 SQL</li>
 *   <li>tableTypes 只 TABLE（一期不含 VIEW）</li>
 *   <li>过滤系统 schema（含 Oracle 系统 schema + APEX 前缀）</li>
 *   <li>listColumns 的 table 参数用 whitelistKey 精确匹配（防止多 schema 同名表混淆）</li>
 *   <li>白名单范围与 listTables 完全一致（共用 collectReadableTables + maxTables 限制）</li>
 *   <li>返回数据不含 jdbcUrl/username/password/extraConfig</li>
 *   <li>日志脱敏（不打印完整 URL / SQLException 原文 / 堆栈）</li>
 *   <li>JDBC URL 前缀与 dsType 校验一致</li>
 *   <li>超时 / 最大表数 / 最大字段数可配置（connect 与 socket 均用 timeoutSeconds）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceMetaService {

    /** 一期支持的 JDBC 类型。 */
    private static final Set<String> READABLE_TYPES = new HashSet<>(Arrays.asList(
            "MYSQL", "DORIS", "ORACLE", "POSTGRESQL"));

    /** JDBC 类型 → URL 前缀（与 DatasourceManagerServiceImpl.ALLOWED_JDBC_TYPES 对齐）。 */
    private static final Map<String, String> TYPE_URL_PREFIX = Map.of(
            "MYSQL", "jdbc:mysql:",
            "DORIS", "jdbc:mysql:",
            "ORACLE", "jdbc:oracle:",
            "POSTGRESQL", "jdbc:postgresql:");

    private final DatasourceManagerService datasourceManagerService;
    private final PasswordEncryptor passwordEncryptor;
    private final AssistantProperties assistantProperties;

    /** 列出可读元数据的 JDBC SQL 类已启用数据源（不含连接串/密码）。 */
    public List<ReadableDatasource> listReadableDatasources() {
        List<DatasourceConfig> all = datasourceManagerService.listAll();
        if (all == null) {
            return Collections.emptyList();
        }
        return all.stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                .filter(c -> isReadableType(c.getDsType()))
                .map(c -> new ReadableDatasource(c.getDsName(), c.getDsType()))
                .collect(Collectors.toList());
    }

    /** 列出表（只 TABLE，过滤系统 schema，限制 maxTables）。 */
    public List<TableMeta> listTables(String dsName) {
        return withConnection(dsName, conn ->
                collectReadableTables(conn, assistantProperties.getMetadata().getMaxTables()));
    }

    /**
     * 列出字段结构。
     * tableKey 必须是 {@link TableMeta#whitelistKey()} 返回的精确 key，
     * 防止多 schema 下同名表混淆。
     */
    public List<ColumnMeta> listColumns(String dsName, String tableKey) {
        return withConnection(dsName, conn -> {
            TableMeta matched = findTableInWhitelist(conn, tableKey);
            if (matched == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "表不存在或不在可读白名单内: " + tableKey);
            }
            DatabaseMetaData md = conn.getMetaData();
            int limit = assistantProperties.getMetadata().getMaxColumns();
            Set<String> pkColumns = new HashSet<>();
            try (ResultSet pkRs = md.getPrimaryKeys(matched.getCatalog(), matched.getSchema(), matched.getTableName())) {
                while (pkRs.next()) {
                    pkColumns.add(pkRs.getString("COLUMN_NAME"));
                }
            }
            List<ColumnMeta> columns = new ArrayList<>();
            try (ResultSet rs = md.getColumns(matched.getCatalog(), matched.getSchema(), matched.getTableName(), null)) {
                while (rs.next() && columns.size() < limit) {
                    ColumnMeta col = new ColumnMeta();
                    col.setColumnName(rs.getString("COLUMN_NAME"));
                    col.setDataType(rs.getString("DATA_TYPE"));
                    col.setTypeName(rs.getString("TYPE_NAME"));
                    col.setColumnSize(rs.getInt("COLUMN_SIZE"));
                    col.setNullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    col.setColumnComment(rs.getString("REMARKS"));
                    col.setPrimaryKey(pkColumns.contains(col.getColumnName()));
                    columns.add(col);
                }
            }
            log.debug("listColumns: dsName={}, tableKey={}, 返回 {} 个字段", dsName, tableKey, columns.size());
            return columns;
        });
    }

    // ===== 公共表收集逻辑（listTables 与 findTableInWhitelist 共用，保证白名单范围一致）=====

    /**
     * 收集可读表：tableTypes 只 TABLE，过滤系统 schema，限制 maxTables。
     * listTables 和 findTableInWhitelist 必须共用此方法，保证「用户能选的表」和「能读字段的表」完全一致。
     * package-private + static，便于单测 mock Connection/DatabaseMetaData/ResultSet。
     */
    static List<TableMeta> collectReadableTables(Connection conn, int maxTables) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        List<TableMeta> tables = new ArrayList<>();
        try (ResultSet rs = md.getTables(null, null, null, new String[]{"TABLE"})) {
            while (rs.next() && tables.size() < maxTables) {
                String catalog = rs.getString("TABLE_CAT");
                String schema = rs.getString("TABLE_SCHEM");
                String tableName = rs.getString("TABLE_NAME");
                String remarks = rs.getString("REMARKS");
                if (isSystemSchema(schema)) {
                    continue;
                }
                tables.add(new TableMeta(catalog, schema, tableName, remarks));
            }
        }
        return tables;
    }

    /** 在白名单中按精确 whitelistKey 匹配（防止多 schema 同名表混淆）。 */
    private TableMeta findTableInWhitelist(Connection conn, String tableKey) throws SQLException {
        for (TableMeta t : collectReadableTables(conn, assistantProperties.getMetadata().getMaxTables())) {
            if (tableKey.equals(t.whitelistKey())) {
                return t;
            }
        }
        return null;
    }

    // ===== 系统 schema 过滤（含 Oracle + APEX 前缀）=====

    private static final Set<String> SYSTEM_SCHEMAS = new HashSet<>(Arrays.asList(
            // MySQL
            "information_schema", "mysql", "performance_schema", "sys",
            // PostgreSQL
            "pg_catalog", "pg_toast", "pg_internal",
            // Oracle 常见系统 schema
            "system", "sys", "xdb", "mdsys", "outln", "dbsnmp", "ctxsys",
            "ordsys", "wmsys", "olapsys", "exfsys", "lbacsys", "flows_files",
            "anonymous", "appqossys", "audsys", "dvsyseb", "gsmadmin_internal",
            "remote_scheduler_agent", "oracle_ocm"));

    /** 判断是否系统 schema（Set.contains + apex_ 前缀匹配）。 */
    static boolean isSystemSchema(String schema) {
        if (schema == null) {
            return false;
        }
        String lower = schema.toLowerCase();
        if (SYSTEM_SCHEMAS.contains(lower)) {
            return true;
        }
        // Oracle APEX schema 前缀
        return lower.startsWith("apex_") || lower.startsWith("flows_");
    }

    // ===== 连接管理 =====

    private <T> T withConnection(String dsName, ConnectionAction<T> action) {
        DatasourceConfig config = datasourceManagerService.getByDsName(dsName);
        if (config == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数据源不存在: " + dsName);
        }
        String dsType = config.getDsType() != null ? config.getDsType().toUpperCase() : "";
        if (!isReadableType(dsType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的数据源类型，仅支持 JDBC SQL 类: " + dsName);
        }
        // JDBC URL 前缀与 dsType 校验一致（建议改1）
        String expectedPrefix = TYPE_URL_PREFIX.get(dsType);
        String jdbcUrl = config.getJdbcUrl();
        if (jdbcUrl == null || !jdbcUrl.toLowerCase().startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "JDBC URL 与数据源类型不匹配: " + dsName);
        }

        String rawPassword = config.getPassword();
        if (rawPassword != null && rawPassword.startsWith("ENC(")) {
            rawPassword = decryptQuietly(rawPassword);
        }
        if (rawPassword == null) {
            throw new BusinessException(ErrorCode.DATASOURCE_ERROR, "数据源密码无法解析: " + dsName);
        }

        DataSource dataSource = null;
        try {
            dataSource = buildTempDataSource(config, dsType, rawPassword);
            try (Connection conn = dataSource.getConnection()) {
                return action.execute(conn);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            String errorCategory = classifyError(e);
            log.warn("元数据读取失败: dsName={}, dsType={}, errorCategory={}", dsName, dsType, errorCategory);
            throw new BusinessException(ErrorCode.DATASOURCE_ERROR,
                    "数据源元数据读取失败，请检查数据源配置或网络");
        } finally {
            closeQuietly(dataSource);
        }
    }

    private boolean isReadableType(String dsType) {
        return dsType != null && READABLE_TYPES.contains(dsType.toUpperCase());
    }

    private DataSource buildTempDataSource(DatasourceConfig config, String dsType, String password) {
        DruidDataSource ds = new DruidDataSource();
        ds.setUrl(config.getJdbcUrl());
        ds.setUsername(config.getUsername());
        ds.setPassword(password);
        ds.setDriverClassName(resolveDriver(dsType));
        ds.setInitialSize(1);
        ds.setMaxActive(2);
        ds.setMinIdle(1);
        // connect 与 socket 均用 timeoutSeconds（建议改2：不 ×10）
        int timeoutMs = assistantProperties.getMetadata().getTimeoutSeconds() * 1000;
        ds.setConnectTimeout(timeoutMs);
        ds.setSocketTimeout(timeoutMs);
        try {
            ds.init();
        } catch (SQLException e) {
            closeQuietly(ds);
            throw new RuntimeException("初始化临时数据源失败", e);
        }
        return ds;
    }

    private String resolveDriver(String dsType) {
        switch (dsType) {
            case "MYSQL": case "DORIS": return "com.mysql.cj.jdbc.Driver";
            case "ORACLE": return "oracle.jdbc.OracleDriver";
            case "POSTGRESQL": return "org.postgresql.Driver";
            default: return "com.mysql.cj.jdbc.Driver";
        }
    }

    private String decryptQuietly(String encPassword) {
        try {
            return passwordEncryptor.decrypt(encPassword);
        } catch (Exception e) {
            log.warn("密码解密失败: errorCategory={}", e.getClass().getSimpleName());
            return null;
        }
    }

    private String classifyError(Exception e) {
        String exClass = e.getClass().getSimpleName();
        if (e instanceof SQLException) return "SQL_ERROR(" + exClass + ")";
        if (e instanceof java.net.ConnectException) return "CONNECTION_REFUSED";
        if (e instanceof java.net.UnknownHostException) return "UNKNOWN_HOST";
        return "UNKNOWN(" + exClass + ")";
    }

    private void closeQuietly(DataSource ds) {
        if (ds == null) return;
        try {
            if (ds instanceof AutoCloseable) {
                ((AutoCloseable) ds).close();
            }
        } catch (Exception ignored) {
        }
    }

    @FunctionalInterface
    private interface ConnectionAction<T> {
        T execute(Connection conn) throws SQLException;
    }
}
