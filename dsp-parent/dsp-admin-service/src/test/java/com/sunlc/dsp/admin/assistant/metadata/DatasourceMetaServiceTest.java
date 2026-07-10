package com.sunlc.dsp.admin.assistant.metadata;

import com.sunlc.dsp.admin.assistant.ai.AssistantProperties;
import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.common.util.PasswordEncryptor;
import com.sunlc.dsp.entity.DatasourceConfig;
import com.sunlc.dsp.service.DatasourceManagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DatasourceMetaService 单测。重点验证安全约束。
 * JDBC 行为（Connection/DatabaseMetaData）需要真实连接，这里只测不依赖 JDBC 的逻辑：
 * 类型白名单过滤、系统 schema 过滤集合、数据源不存在/类型不支持/上限。
 */
@ExtendWith(MockitoExtension.class)
class DatasourceMetaServiceTest {

    @Mock private DatasourceManagerService datasourceManagerService;
    @Mock private PasswordEncryptor passwordEncryptor;

    private DatasourceMetaService service;
    private AssistantProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AssistantProperties();
        service = new DatasourceMetaService(datasourceManagerService, passwordEncryptor, properties);
    }

    @Test
    void listReadableDatasources_filtersNonJdbcAndDisabled() {
        DatasourceConfig mysql = ds("ds_main", "MYSQL", 1);
        DatasourceConfig doris = ds("ds_doris", "DORIS", 1);
        DatasourceConfig http = ds("ds_http", "HTTP", 1);    // 非 JDBC，过滤
        DatasourceConfig mongo = ds("ds_mongo", "MONGO", 1); // 非 JDBC，过滤
        DatasourceConfig disabled = ds("ds_off", "MYSQL", 0); // 禁用，过滤
        when(datasourceManagerService.listAll()).thenReturn(Arrays.asList(mysql, doris, http, mongo, disabled));

        List<ReadableDatasource> result = service.listReadableDatasources();

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> "ds_main".equals(r.getDsName())));
        assertTrue(result.stream().anyMatch(r -> "ds_doris".equals(r.getDsName())));
        assertFalse(result.stream().anyMatch(r -> "ds_http".equals(r.getDsName())));
        assertFalse(result.stream().anyMatch(r -> "ds_mongo".equals(r.getDsName())));
    }

    @Test
    void listReadableDatasources_emptyWhenAllFiltered() {
        when(datasourceManagerService.listAll()).thenReturn(Collections.emptyList());
        assertTrue(service.listReadableDatasources().isEmpty());
    }

    @Test
    void listReadableDatasources_nullListReturnsEmpty() {
        when(datasourceManagerService.listAll()).thenReturn(null);
        assertTrue(service.listReadableDatasources().isEmpty());
    }

    @Test
    void listTables_datasourceNotFound_throws() {
        when(datasourceManagerService.getByDsName("noexist")).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.listTables("noexist"));
    }

    @Test
    void listTables_unsupportedType_throws() {
        when(datasourceManagerService.getByDsName("ds_http")).thenReturn(ds("ds_http", "HTTP", 1));
        assertThrows(BusinessException.class, () -> service.listTables("ds_http"));
    }

    @Test
    void listColumns_datasourceNotFound_throws() {
        when(datasourceManagerService.getByDsName("noexist")).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.listColumns("noexist", "users"));
    }

    @Test
    void listColumns_unsupportedType_throws() {
        when(datasourceManagerService.getByDsName("ds_mongo")).thenReturn(ds("ds_mongo", "MONGO", 1));
        assertThrows(BusinessException.class, () -> service.listColumns("ds_mongo", "users"));
    }

    @Test
    void readableDatasourceNotContainsConnectionInfo() {
        DatasourceConfig cfg = ds("ds_main", "MYSQL", 1);
        cfg.setJdbcUrl("jdbc:mysql://secret-host:3306/db");
        cfg.setUsername("secret-user");
        cfg.setPassword("ENC(secret)");
        when(datasourceManagerService.listAll()).thenReturn(Collections.singletonList(cfg));

        List<ReadableDatasource> result = service.listReadableDatasources();
        assertEquals(1, result.size());
        ReadableDatasource r = result.get(0);
        // 只返回 dsName + dsType，不含连接串/用户名/密码
        assertEquals("ds_main", r.getDsName());
        assertEquals("MYSQL", r.getDsType());
        assertFalse(r.toString().contains("secret-host"));
        assertFalse(r.toString().contains("secret-user"));
        assertFalse(r.toString().contains("ENC"));
    }

    @Test
    void metadataConfigDefaultsAreReasonable() {
        // 验证配置默认值（安全上限）
        assertEquals(10, properties.getMetadata().getTimeoutSeconds());
        assertEquals(100, properties.getMetadata().getMaxTables());
        assertEquals(200, properties.getMetadata().getMaxColumns());
    }

    private DatasourceConfig ds(String name, String type, int status) {
        DatasourceConfig c = new DatasourceConfig();
        c.setDsName(name);
        c.setDsType(type);
        c.setStatus(status);
        c.setJdbcUrl("jdbc:mysql://localhost:3306/db");
        c.setUsername("root");
        c.setPassword("plain");
        return c;
    }

    // ===== isSystemSchema 过滤（必改3）=====

    @Test
    void isSystemSchema_filtersMySQL() {
        assertTrue(DatasourceMetaService.isSystemSchema("information_schema"));
        assertTrue(DatasourceMetaService.isSystemSchema("MYSQL"));
        assertTrue(DatasourceMetaService.isSystemSchema("performance_schema"));
    }

    @Test
    void isSystemSchema_filtersPostgreSQL() {
        assertTrue(DatasourceMetaService.isSystemSchema("pg_catalog"));
        assertTrue(DatasourceMetaService.isSystemSchema("PG_TOAST"));
    }

    @Test
    void isSystemSchema_filtersOracle() {
        assertTrue(DatasourceMetaService.isSystemSchema("SYSTEM"));
        assertTrue(DatasourceMetaService.isSystemSchema("XDB"));
        assertTrue(DatasourceMetaService.isSystemSchema("MDSYS"));
        assertTrue(DatasourceMetaService.isSystemSchema("OUTLN"));
        assertTrue(DatasourceMetaService.isSystemSchema("CTXSYS"));
    }

    @Test
    void isSystemSchema_filtersApexPrefix() {
        assertTrue(DatasourceMetaService.isSystemSchema("APEX_040000"));
        assertTrue(DatasourceMetaService.isSystemSchema("apex_200100"));
        assertTrue(DatasourceMetaService.isSystemSchema("FLOWS_FILES"));
    }

    @Test
    void isSystemSchema_keepsUserSchema() {
        assertFalse(DatasourceMetaService.isSystemSchema("dsp_config"));
        assertFalse(DatasourceMetaService.isSystemSchema("public"));
        assertFalse(DatasourceMetaService.isSystemSchema("BUSINESS"));
        assertFalse(DatasourceMetaService.isSystemSchema(null));
    }

    // ===== collectReadableTables JDBC 安全路径（必改4）=====

    @Test
    void collectReadableTables_tableTypesOnlyTableAndFiltersSystemSchema() throws SQLException {
        Connection conn = mock(Connection.class);
        DatabaseMetaData md = mock(DatabaseMetaData.class);
        ResultSet rs = mockTableResultSet(
                row(null, "public", "users", "用户表"),
                row(null, "information_schema", "tables", "系统表"),  // 系统schema过滤
                row(null, "mysql", "user", "MySQL系统"),              // 系统schema过滤
                row(null, "public", "orders", "订单表"));
        when(conn.getMetaData()).thenReturn(md);
        when(md.getTables(null, null, null, new String[]{"TABLE"})).thenReturn(rs);

        List<TableMeta> tables = DatasourceMetaService.collectReadableTables(conn, 100);

        assertEquals(2, tables.size());
        assertEquals("users", tables.get(0).getTableName());
        assertEquals("orders", tables.get(1).getTableName());
    }

    @Test
    void collectReadableTables_respectsMaxTablesLimit() throws SQLException {
        Connection conn = mock(Connection.class);
        DatabaseMetaData md = mock(DatabaseMetaData.class);
        ResultSet rs = mockTableResultSet(
                row(null, "public", "t1", ""),
                row(null, "public", "t2", ""),
                row(null, "public", "t3", ""));
        when(conn.getMetaData()).thenReturn(md);
        when(md.getTables(null, null, null, new String[]{"TABLE"})).thenReturn(rs);

        // maxTables=1，只返回第一张
        List<TableMeta> tables = DatasourceMetaService.collectReadableTables(conn, 1);

        assertEquals(1, tables.size());
        assertEquals("t1", tables.get(0).getTableName());
    }

    @Test
    void collectReadableTables_returnsEmptyWhenNoTables() throws SQLException {
        Connection conn = mock(Connection.class);
        DatabaseMetaData md = mock(DatabaseMetaData.class);
        // 空 ResultSet：next() 直接 false
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        when(conn.getMetaData()).thenReturn(md);
        when(md.getTables(null, null, null, new String[]{"TABLE"})).thenReturn(rs);

        List<TableMeta> tables = DatasourceMetaService.collectReadableTables(conn, 100);
        assertTrue(tables.isEmpty());
    }

    @Test
    void whitelistKey_containsSchemaAndTable() {
        TableMeta t1 = new TableMeta("cat1", "schema_a", "users", "");
        TableMeta t2 = new TableMeta("cat2", "schema_b", "users", "");
        // 不同 schema 下同名表，whitelistKey 不同（防止混淆）
        assertFalse(t1.whitelistKey().equals(t2.whitelistKey()));
        assertTrue(t1.whitelistKey().contains("schema_a"));
        assertTrue(t1.whitelistKey().contains("users"));
    }

    @Test
    void collectReadableTables_doesNotIncludeViewType() throws SQLException {
        // getTables 传入的 tableTypes 是 {"TABLE"}，不含 VIEW
        Connection conn = mock(Connection.class);
        DatabaseMetaData md = mock(DatabaseMetaData.class);
        ResultSet rs = mockTableResultSet(row(null, "public", "v_view", ""));
        when(conn.getMetaData()).thenReturn(md);
        when(md.getTables(null, null, null, new String[]{"TABLE"})).thenReturn(rs);

        // 这里验证的是调用时传了 {"TABLE"}，mock 不模拟过滤（由 DB 实际过滤），
        // 但能验证代码传了正确的 tableTypes 数组
        DatasourceMetaService.collectReadableTables(conn, 100);
        org.mockito.Mockito.verify(md).getTables(null, null, null, new String[]{"TABLE"});
    }

    /**
     * 构造 mock ResultSet：按行数据模拟 next() + getString()。
     * 用 Answer 按当前行索引返回值（避免 thenReturn 序列与交叉 getString 调用错乱）。
     */
    private ResultSet mockTableResultSet(String[]... rows) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        final int[] currentRow = {-1};
        // next()：首次 true 并推进行号，超出后 false
        when(rs.next()).thenAnswer(inv -> {
            currentRow[0]++;
            return currentRow[0] < rows.length;
        });
        // getString(columnLabel) 按 currentRow 返回对应列
        when(rs.getString("TABLE_CAT")).thenAnswer(inv ->
                currentRow[0] >= 0 && currentRow[0] < rows.length ? rows[currentRow[0]][0] : null);
        when(rs.getString("TABLE_SCHEM")).thenAnswer(inv ->
                currentRow[0] >= 0 && currentRow[0] < rows.length ? rows[currentRow[0]][1] : null);
        when(rs.getString("TABLE_NAME")).thenAnswer(inv ->
                currentRow[0] >= 0 && currentRow[0] < rows.length ? rows[currentRow[0]][2] : null);
        when(rs.getString("REMARKS")).thenAnswer(inv ->
                currentRow[0] >= 0 && currentRow[0] < rows.length ? rows[currentRow[0]][3] : null);
        return rs;
    }

    private String[] row(String catalog, String schema, String tableName, String remarks) {
        return new String[]{catalog, schema, tableName, remarks};
    }
}
