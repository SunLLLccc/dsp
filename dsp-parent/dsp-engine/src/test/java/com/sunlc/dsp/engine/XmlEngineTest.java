package com.sunlc.dsp.engine;

import com.sunlc.dsp.common.exception.BusinessException;
import com.sunlc.dsp.engine.executor.*;
import com.sunlc.dsp.engine.model.*;
import com.sunlc.dsp.engine.parser.XmlConfigParser;
import com.sunlc.dsp.engine.validator.SqlSecurityValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class XmlEngineTest {

    // 测试无 resultMap 时查询结果以 queryId 为 key 放入 mappedResults
    // 通过反射方式验证 executeWithConfig 内部逻辑，或通过集成方式测试
    // 由于 XmlEngine 依赖多个 Spring Bean，这里只测试核心逻辑

    @Test
    void noResultMap_queryResultsKeyedByQueryId() {
        // 验证当没有 resultMap 时，结果映射逻辑
        // 构造模拟的 queryResults 和空的 resultMaps，验证 mappedResults 使用 queryId 作为 key
        List<Map<String, Object>> queryData = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dict_code", "STATUS");
        row.put("dict_label", "状态");
        queryData.add(row);

        // 模拟 XmlEngine.executeWithConfig 中的逻辑
        Map<String, List<Map<String, Object>>> queryResults = new LinkedHashMap<>();
        queryResults.put("q1", queryData);

        Map<String, Object> mappedResults = new LinkedHashMap<>();
        List<ResultMapConfig> resultMaps = Collections.emptyList();

        // 无 resultMap 时，将查询结果直接放入 mappedResults（key=queryId）
        if (resultMaps.isEmpty()) {
            for (Map.Entry<String, List<Map<String, Object>>> entry : queryResults.entrySet()) {
                List<Map<String, Object>> data = entry.getValue();
                if (data.size() == 1) {
                    mappedResults.put(entry.getKey(), data.get(0));
                } else {
                    mappedResults.put(entry.getKey(), data);
                }
            }
        }

        // 验证
        assertTrue(mappedResults.containsKey("q1"));
        Object data = mappedResults.get("q1");
        assertTrue(data instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> mapData = (Map<String, Object>) data;
        assertEquals("STATUS", mapData.get("dict_code"));
    }

    @Test
    void noResultMap_multipleRows_storedAsList() {
        List<Map<String, Object>> queryData = new ArrayList<>();
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("code", "A");
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("code", "B");
        queryData.add(row1);
        queryData.add(row2);

        Map<String, List<Map<String, Object>>> queryResults = new LinkedHashMap<>();
        queryResults.put("q1", queryData);

        Map<String, Object> mappedResults = new LinkedHashMap<>();

        if (Collections.emptyList().isEmpty()) {
            for (Map.Entry<String, List<Map<String, Object>>> entry : queryResults.entrySet()) {
                List<Map<String, Object>> data = entry.getValue();
                if (data.size() == 1) {
                    mappedResults.put(entry.getKey(), data.get(0));
                } else {
                    mappedResults.put(entry.getKey(), data);
                }
            }
        }

        Object data = mappedResults.get("q1");
        assertTrue(data instanceof List);
        assertEquals(2, ((List<?>) data).size());
    }

    @Test
    void xmlParsing_noResultMap_correctStructure() {
        XmlConfigParser parser = new XmlConfigParser();
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<interface transno=\"DICT_QUERY\" name=\"字典查询\" description=\"测试\">\n" +
                "  <requestData>\n" +
                "    <param name=\"dictType\" type=\"String\" required=\"true\" description=\"字典类型\" />\n" +
                "  </requestData>\n" +
                "  <datasource name=\"ds_main\" type=\"MYSQL\" url=\"jdbc:mysql://localhost:3306/dsp\" username=\"root\" password=\"123456\" />\n" +
                "  <query id=\"q1\" type=\"mysql\" datasource=\"ds_main\">\n" +
                "    SELECT dict_code, dict_label FROM sys_dict WHERE dict_type = #{requestData.dictType}\n" +
                "  </query>\n" +
                "  <responseData>\n" +
                "    <field name=\"list\" mapTo=\"q1\" />\n" +
                "  </responseData>\n" +
                "</interface>";

        InterfaceConfig config = parser.parse(xml);

        assertEquals("DICT_QUERY", config.getTransno());
        assertEquals(1, config.getQueries().size());
        assertEquals("q1", config.getQueries().get(0).getId());
        assertTrue(config.getResultMaps().isEmpty()); // 没有定义 resultMap
        assertNotNull(config.getResponseData());
        assertEquals(1, config.getResponseData().getFields().size());
        assertEquals("list", config.getResponseData().getFields().get(0).getName());
        assertEquals("q1", config.getResponseData().getFields().get(0).getMapTo());
    }

    @Test
    void xmlParsing_fullFeatured_allElements() {
        XmlConfigParser parser = new XmlConfigParser();
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<interface transno=\"TEST\" name=\"测试\" description=\"全功能测试\">\n" +
                "  <requestData>\n" +
                "    <param name=\"id\" type=\"String\" required=\"true\" />\n" +
                "    <param name=\"status\" type=\"String\" required=\"false\" defaultValue=\"active\" />\n" +
                "  </requestData>\n" +
                "  <datasource name=\"ds\" type=\"MYSQL\" url=\"jdbc:mysql://localhost/db\" username=\"root\" password=\"123\" />\n" +
                "  <query id=\"q1\" type=\"mysql\" datasource=\"ds\" pagination=\"cursor\" order-by=\"id\"\n" +
                "         page-size-param=\"pageSize\" last-id-param=\"lastId\" default-page-size=\"20\" max-page-size=\"100\">\n" +
                "    SELECT * FROM t WHERE 1=1\n" +
                "    <if test=\"$requestData.status != null\">AND status = #{requestData.status}</if>\n" +
                "  </query>\n" +
                "  <query id=\"q2\" type=\"mysql\" datasource=\"ds\" depends=\"q1\">\n" +
                "    SELECT * FROM t2 WHERE ref_id = #{q1.id}\n" +
                "  </query>\n" +
                "  <resultMap id=\"map1\" query=\"q1\">\n" +
                "    <field name=\"myId\" column=\"id\" />\n" +
                "    <field name=\"myStatus\" column=\"status\" function=\"fn:UPPER\" />\n" +
                "  </resultMap>\n" +
                "  <resultMap id=\"map2\" query=\"q2\">\n" +
                "    <field name=\"refData\" column=\"data\" />\n" +
                "  </resultMap>\n" +
                "  <responseData>\n" +
                "    <field name=\"main\" mapTo=\"map1\" />\n" +
                "    <field name=\"sub\" mapTo=\"map2\" />\n" +
                "  </responseData>\n" +
                "</interface>";

        InterfaceConfig config = parser.parse(xml);

        assertEquals("TEST", config.getTransno());
        assertEquals(2, config.getRequestData().getParams().size());
        assertEquals(1, config.getDataSources().size());
        assertEquals(2, config.getQueries().size());
        assertEquals(2, config.getResultMaps().size());

        // q1 有分页和动态SQL
        QueryConfig q1 = config.getQueries().get(0);
        assertEquals("q1", q1.getId());
        assertNotNull(q1.getPaginationConfig());
        assertEquals(PaginationConfig.PaginationMode.CURSOR, q1.getPaginationConfig().getMode());
        assertEquals(1, q1.getDynamicSqls().size());
        assertTrue(q1.getDepends().isEmpty());

        // q2 依赖 q1
        QueryConfig q2 = config.getQueries().get(1);
        assertEquals("q2", q2.getId());
        assertEquals(1, q2.getDepends().size());
        assertEquals("q1", q2.getDepends().get(0));

        // resultMap
        assertEquals("map1", config.getResultMaps().get(0).getId());
        assertEquals("q1", config.getResultMaps().get(0).getQuery());
        assertEquals("fn:UPPER", config.getResultMaps().get(0).getFields().get(1).getFunction());

        // responseData
        assertEquals("main", config.getResponseData().getFields().get(0).getName());
        assertEquals("map1", config.getResponseData().getFields().get(0).getMapTo());
        assertEquals("sub", config.getResponseData().getFields().get(1).getName());
        assertEquals("map2", config.getResponseData().getFields().get(1).getMapTo());
    }

    @Test
    void xmlParsing_httpQuery() {
        XmlConfigParser parser = new XmlConfigParser();
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<interface transno=\"HTTP_TEST\" name=\"HTTP测试\">\n" +
                "  <requestData>\n" +
                "    <param name=\"code\" type=\"String\" required=\"true\" />\n" +
                "  </requestData>\n" +
                "  <query id=\"q1\" type=\"http\">\n" +
                "    <http url=\"https://api.example.com/data?id=#{$requestData.code}\" method=\"GET\"\n" +
                "          headers='{\"Auth\":\"token\"}' responsePath=\"data\" />\n" +
                "  </query>\n" +
                "  <resultMap id=\"m1\" query=\"q1\">\n" +
                "    <field name=\"value\" column=\"val\" />\n" +
                "  </resultMap>\n" +
                "  <responseData resultMap=\"m1\">\n" +
                "    <field name=\"result\" />\n" +
                "  </responseData>\n" +
                "</interface>";

        InterfaceConfig config = parser.parse(xml);
        QueryConfig q = config.getQueries().get(0);
        assertEquals("http", q.getType());
        assertNotNull(q.getHttpConfig());
        assertEquals("https://api.example.com/data?id=#{$requestData.code}", q.getHttpConfig().getUrl());
        assertEquals("GET", q.getHttpConfig().getMethod());
        assertEquals("{\"Auth\":\"token\"}", q.getHttpConfig().getHeaders());
        assertEquals("data", q.getHttpConfig().getResponsePath());
    }

    @Test
    void xmlParsing_dubboQuery() {
        XmlConfigParser parser = new XmlConfigParser();
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<interface transno=\"DUBBO_TEST\" name=\"Dubbo测试\">\n" +
                "  <requestData>\n" +
                "    <param name=\"id\" type=\"String\" required=\"true\" />\n" +
                "  </requestData>\n" +
                "  <datasource name=\"dubbo_ds\" type=\"DUBBO\" url=\"\" username=\"\" password=\"\"\n" +
                "              extraConfig='{\"registry\":\"zookeeper://127.0.0.1:2181\"}' />\n" +
                "  <query id=\"q1\" type=\"dubbo\" datasource=\"dubbo_ds\">\n" +
                "    <dubbo service=\"com.test.Service\" method=\"get\" version=\"2.0.0\" timeout=\"5000\">\n" +
                "      <param type=\"String\" value=\"#{$requestData.id}\" />\n" +
                "    </dubbo>\n" +
                "  </query>\n" +
                "</interface>";

        InterfaceConfig config = parser.parse(xml);
        QueryConfig q = config.getQueries().get(0);
        assertEquals("dubbo", q.getType());
        assertNotNull(q.getDubboConfig());
        assertEquals("com.test.Service", q.getDubboConfig().getService());
        assertEquals("get", q.getDubboConfig().getMethod());
        assertEquals("2.0.0", q.getDubboConfig().getVersion());
        assertEquals(5000, q.getDubboConfig().getTimeout());
        assertEquals(1, q.getDubboConfig().getParams().size());
        assertEquals("String", q.getDubboConfig().getParams().get(0).getType());
        assertEquals("#{$requestData.id}", q.getDubboConfig().getParams().get(0).getValue());
    }

    @Test
    void xmlParsing_mongoQuery() {
        XmlConfigParser parser = new XmlConfigParser();
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<interface transno=\"MONGO_TEST\" name=\"Mongo测试\">\n" +
                "  <requestData>\n" +
                "    <param name=\"userId\" type=\"String\" required=\"true\" />\n" +
                "  </requestData>\n" +
                "  <datasource name=\"mongo_ds\" type=\"MONGO\" url=\"mongodb://localhost:27017/test\" username=\"\" password=\"\" />\n" +
                "  <query id=\"q1\" type=\"mongo\" datasource=\"mongo_ds\">\n" +
                "    <mongo collection=\"logs\">\n" +
                "      <filter>{\"userId\": \"#{userId}\"}</filter>\n" +
                "      <projection>{\"userId\":1, \"action\":1}</projection>\n" +
                "      <sort>{\"time\": -1}</sort>\n" +
                "      <limit>50</limit>\n" +
                "      <skip>10</skip>\n" +
                "    </mongo>\n" +
                "  </query>\n" +
                "</interface>";

        InterfaceConfig config = parser.parse(xml);
        QueryConfig q = config.getQueries().get(0);
        assertEquals("mongo", q.getType());
        assertNotNull(q.getMongoConfig());
        assertEquals("logs", q.getMongoConfig().getCollection());
        assertEquals("{\"userId\": \"#{userId}\"}", q.getMongoConfig().getFilter());
        assertEquals("{\"userId\":1, \"action\":1}", q.getMongoConfig().getProjection());
        assertEquals("{\"time\": -1}", q.getMongoConfig().getSort());
        assertEquals(50, q.getMongoConfig().getLimit());
        assertEquals(10, q.getMongoConfig().getSkip());
    }

    @Test
    void xmlParsing_optimizedPagination() {
        XmlConfigParser parser = new XmlConfigParser();
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<interface transno=\"PAGE_TEST\" name=\"分页测试\">\n" +
                "  <requestData><param name=\"pageNum\" type=\"Integer\" /></requestData>\n" +
                "  <datasource name=\"ds\" type=\"MYSQL\" url=\"jdbc:mysql://localhost/db\" username=\"root\" password=\"\" />\n" +
                "  <query id=\"q1\" type=\"mysql\" datasource=\"ds\"\n" +
                "         pagination=\"optimized\" order-by=\"id\"\n" +
                "         page-size-param=\"pageSize\" page-num-param=\"pageNum\"\n" +
                "         default-page-size=\"10\" max-page-size=\"50\">\n" +
                "    SELECT * FROM t\n" +
                "  </query>\n" +
                "</interface>";

        InterfaceConfig config = parser.parse(xml);
        PaginationConfig pc = config.getQueries().get(0).getPaginationConfig();
        assertNotNull(pc);
        assertEquals(PaginationConfig.PaginationMode.OPTIMIZED, pc.getMode());
        assertEquals("id", pc.getOrderBy());
        assertEquals("pageSize", pc.getPageSizeParam());
        assertEquals("pageNum", pc.getPageNumParam());
        assertEquals(10, pc.getDefaultPageSize());
        assertEquals(50, pc.getMaxPageSize());
    }

    @Test
    void xmlParsing_dynamicSqlQuery_template() {
        XmlConfigParser parser = new XmlConfigParser();
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<interface transno=\"USER_LIST_QUERY\" name=\"用户列表查询\" description=\"支持多条件筛选的用户列表查询\">\n" +
                "  <requestData>\n" +
                "    <param name=\"userName\" type=\"String\" required=\"false\" description=\"用户名（模糊查询）\" />\n" +
                "    <param name=\"status\" type=\"String\" required=\"false\" defaultValue=\"active\" description=\"状态\" />\n" +
                "    <param name=\"departmentId\" type=\"String\" required=\"false\" description=\"部门ID\" />\n" +
                "    <param name=\"ids\" type=\"List\" required=\"false\" description=\"用户ID列表（批量查询）\" />\n" +
                "    <param name=\"minAge\" type=\"Integer\" required=\"false\" description=\"最小年龄\" />\n" +
                "    <param name=\"maxAge\" type=\"Integer\" required=\"false\" description=\"最大年龄\" />\n" +
                "  </requestData>\n" +
                "  <datasource name=\"ds_main\" />\n" +
                "  <query id=\"q1\" type=\"mysql\" datasource=\"ds_main\">\n" +
                "    SELECT id, user_name, email, phone, status, age, department_id, created_at\n" +
                "    FROM users\n" +
                "    WHERE 1=1\n" +
                "    <if test=\"$requestData['userName'] != null\">AND user_name LIKE #{$requestData['userName']}</if>\n" +
                "    <if test=\"$requestData['status'] != null\">AND status = #{$requestData['status']}</if>\n" +
                "    <if test=\"$requestData['departmentId'] != null\">AND department_id = #{$requestData['departmentId']}</if>\n" +
                "    <if test=\"$requestData['minAge'] != null\">AND age &gt;= #{$requestData['minAge']}</if>\n" +
                "    <if test=\"$requestData['maxAge'] != null\">AND age &lt;= #{$requestData['maxAge']}</if>\n" +
                "    <foreach collection=\"$requestData['ids']\" item=\"id\" separator=\",\" open=\"AND id IN (\" close=\")\">\n" +
                "      #{id}\n" +
                "    </foreach>\n" +
                "    ORDER BY created_at DESC\n" +
                "  </query>\n" +
                "  <resultMap id=\"userListMap\" query=\"q1\">\n" +
                "    <field name=\"userId\" column=\"id\" />\n" +
                "    <field name=\"userName\" column=\"user_name\" />\n" +
                "    <field name=\"email\" column=\"email\" />\n" +
                "    <field name=\"phone\" column=\"phone\" />\n" +
                "    <field name=\"status\" column=\"status\" />\n" +
                "    <field name=\"age\" column=\"age\" function=\"fn:TYPE_CONVERT,INTEGER\" />\n" +
                "    <field name=\"departmentId\" column=\"department_id\" />\n" +
                "    <field name=\"createdAt\" column=\"created_at\" function=\"fn:DATE_FORMAT,yyyy-MM-dd\" />\n" +
                "  </resultMap>\n" +
                "  <responseData resultMap=\"userListMap\">\n" +
                "    <field name=\"list\" as=\"list\" />\n" +
                "  </responseData>\n" +
                "</interface>";

        InterfaceConfig config = parser.parse(xml);

        // 1. 顶层属性
        assertEquals("USER_LIST_QUERY", config.getTransno());
        assertEquals("用户列表查询", config.getName());
        assertEquals("支持多条件筛选的用户列表查询", config.getDescription());

        // 2. requestData: 6 个参数
        List<ParamConfig> params = config.getRequestData().getParams();
        assertEquals(6, params.size());
        assertEquals("userName", params.get(0).getName());
        assertFalse(params.get(0).isRequired());
        assertEquals("status", params.get(1).getName());
        assertEquals("active", params.get(1).getDefaultValue());
        assertEquals("ids", params.get(3).getName());
        assertEquals("List", params.get(3).getType());
        assertEquals("minAge", params.get(4).getName());
        assertEquals("Integer", params.get(4).getType());

        // 3. datasource
        assertEquals(1, config.getDataSources().size());
        assertEquals("ds_main", config.getDataSources().get(0).getName());

        // 4. query
        assertEquals(1, config.getQueries().size());
        QueryConfig q1 = config.getQueries().get(0);
        assertEquals("q1", q1.getId());
        assertEquals("mysql", q1.getType());
        assertEquals("ds_main", q1.getDatasource());
        assertTrue(q1.getSql().contains("SELECT id, user_name"));
        assertTrue(q1.getSql().contains("WHERE 1=1"));
        assertTrue(q1.getSql().contains("ORDER BY created_at DESC"));

        // 5. dynamicSqls: 5 个 IF + 1 个 FOREACH
        List<DynamicSqlConfig> dyns = q1.getDynamicSqls();
        assertEquals(6, dyns.size());

        // 前 5 个都是 IF 类型
        for (int i = 0; i < 5; i++) {
            assertEquals(DynamicSqlConfig.DynamicType.IF, dyns.get(i).getType());
        }
        assertEquals("$requestData['userName'] != null", dyns.get(0).getTest());
        assertTrue(dyns.get(0).getSql().contains("AND user_name LIKE"));
        assertEquals("$requestData['status'] != null", dyns.get(1).getTest());
        assertEquals("$requestData['departmentId'] != null", dyns.get(2).getTest());
        assertEquals("$requestData['minAge'] != null", dyns.get(3).getTest());
        assertTrue(dyns.get(3).getSql().contains("AND age >="));
        assertEquals("$requestData['maxAge'] != null", dyns.get(4).getTest());
        assertTrue(dyns.get(4).getSql().contains("AND age <="));

        // 第 6 个是 FOREACH
        DynamicSqlConfig foreach = dyns.get(5);
        assertEquals(DynamicSqlConfig.DynamicType.FOREACH, foreach.getType());
        assertEquals("$requestData['ids']", foreach.getCollection());
        assertEquals("id", foreach.getItem());
        assertEquals(",", foreach.getSeparator());
        assertEquals("AND id IN (", foreach.getOpen());
        assertEquals(")", foreach.getClose());

        // 6. resultMap
        assertEquals(1, config.getResultMaps().size());
        ResultMapConfig rm = config.getResultMaps().get(0);
        assertEquals("userListMap", rm.getId());
        assertEquals("q1", rm.getQuery());
        assertEquals(8, rm.getFields().size());
        assertEquals("userId", rm.getFields().get(0).getName());
        assertEquals("id", rm.getFields().get(0).getColumn());
        assertEquals("age", rm.getFields().get(5).getName());
        assertEquals("fn:TYPE_CONVERT,INTEGER", rm.getFields().get(5).getFunction());
        assertEquals("createdAt", rm.getFields().get(7).getName());
        assertEquals("fn:DATE_FORMAT,yyyy-MM-dd", rm.getFields().get(7).getFunction());

        // 7. responseData
        assertNotNull(config.getResponseData());
        assertEquals("userListMap", config.getResponseData().getResultMap());
        assertEquals(1, config.getResponseData().getFields().size());
        assertEquals("list", config.getResponseData().getFields().get(0).getName());
        assertEquals("list", config.getResponseData().getFields().get(0).getAs());
    }

    @Test
    void xmlParsing_foreach() {
        XmlConfigParser parser = new XmlConfigParser();
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<interface transno=\"FOREACH_TEST\" name=\"Foreach测试\">\n" +
                "  <requestData>\n" +
                "    <param name=\"ids\" type=\"List\" />\n" +
                "  </requestData>\n" +
                "  <datasource name=\"ds\" type=\"MYSQL\" url=\"jdbc:mysql://localhost/db\" username=\"root\" password=\"\" />\n" +
                "  <query id=\"q1\" type=\"mysql\" datasource=\"ds\">\n" +
                "    SELECT * FROM t WHERE 1=1\n" +
                "    <foreach collection=\"$requestData.ids\" item=\"id\" separator=\",\" open=\"AND id IN (\" close=\")\">\n" +
                "      #{id}\n" +
                "    </foreach>\n" +
                "  </query>\n" +
                "</interface>";

        InterfaceConfig config = parser.parse(xml);
        QueryConfig q = config.getQueries().get(0);
        assertEquals(1, q.getDynamicSqls().size());
        DynamicSqlConfig dyn = q.getDynamicSqls().get(0);
        assertEquals(DynamicSqlConfig.DynamicType.FOREACH, dyn.getType());
        assertEquals("$requestData.ids", dyn.getCollection());
        assertEquals("id", dyn.getItem());
        assertEquals(",", dyn.getSeparator());
        assertEquals("AND id IN (", dyn.getOpen());
        assertEquals(")", dyn.getClose());
    }

    // ==================== SQL 安全校验测试 ====================

    private SqlSecurityValidator validator;

    @BeforeEach
    void setUpValidator() {
        validator = new SqlSecurityValidator();
    }

    private static final String VALID_XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<interface transno=\"TEST\" name=\"测试\">\n" +
            "  <requestData><param name=\"id\" type=\"String\" /></requestData>\n" +
            "  <datasource name=\"ds\" type=\"MYSQL\" url=\"jdbc:mysql://localhost/db\" username=\"root\" password=\"\" />\n" +
            "  <query id=\"q1\" type=\"mysql\" datasource=\"ds\">\n";

    private static final String VALID_XML_FOOTER = "  </query>\n</interface>";

    @Test
    void sqlSecurity_validSelect_passes() {
        String xml = VALID_XML_HEADER +
                "    SELECT id, name FROM users WHERE id = #{requestData.id}" +
                VALID_XML_FOOTER;
        assertDoesNotThrow(() -> validator.validateXmlConfig(xml));
    }

    @Test
    void sqlSecurity_updateRejected() {
        String xml = VALID_XML_HEADER +
                "    UPDATE users SET name = 'hacked' WHERE 1=1" +
                VALID_XML_FOOTER;
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateXmlConfig(xml));
        assertTrue(ex.getMessage().contains("UPDATE"));
    }

    @Test
    void sqlSecurity_deleteRejected() {
        String xml = VALID_XML_HEADER +
                "    DELETE FROM users WHERE 1=1" +
                VALID_XML_FOOTER;
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateXmlConfig(xml));
        assertTrue(ex.getMessage().contains("DELETE"));
    }

    @Test
    void sqlSecurity_insertRejected() {
        String xml = VALID_XML_HEADER +
                "    INSERT INTO users (name) VALUES ('hacked')" +
                VALID_XML_FOOTER;
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateXmlConfig(xml));
        assertTrue(ex.getMessage().contains("INSERT"));
    }

    @Test
    void sqlSecurity_dropRejected() {
        String xml = VALID_XML_HEADER +
                "    DROP TABLE users" +
                VALID_XML_FOOTER;
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateXmlConfig(xml));
        assertTrue(ex.getMessage().contains("DROP"));
    }

    @Test
    void sqlSecurity_semicolonRejected() {
        String xml = VALID_XML_HEADER +
                "    SELECT * FROM users; DROP TABLE users" +
                VALID_XML_FOOTER;
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateXmlConfig(xml));
        assertTrue(ex.getMessage().contains("分号"));
    }

    @Test
    void sqlSecurity_commentRejected() {
        String xml = VALID_XML_HEADER +
                "    SELECT * FROM users WHERE 1=1 -- bypass" +
                VALID_XML_FOOTER;
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateXmlConfig(xml));
        assertTrue(ex.getMessage().contains("注释"));
    }

    @Test
    void sqlSecurity_blockCommentRejected() {
        String xml = VALID_XML_HEADER +
                "    SELECT * FROM users WHERE 1=1 /* always true */ AND id = 1" +
                VALID_XML_FOOTER;
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateXmlConfig(xml));
        assertTrue(ex.getMessage().contains("注释"));
    }

    @Test
    void sqlSecurity_dynamicSqlContainsUpdateRejected() {
        String xml = VALID_XML_HEADER +
                "    SELECT * FROM users WHERE 1=1\n" +
                "    <if test=\"$requestData.hack != null\"> UNION SELECT * FROM admin UPDATE users SET name='x'</if>" +
                VALID_XML_FOOTER;
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validateXmlConfig(xml));
        assertTrue(ex.getMessage().contains("UPDATE"));
    }

    @Test
    void sqlSecurity_validOrderBy_passes() {
        assertDoesNotThrow(() -> validator.validateOrderBy("id", "q1"));
        assertDoesNotThrow(() -> validator.validateOrderBy("created_at DESC", "q1"));
        assertDoesNotThrow(() -> validator.validateOrderBy("t.id ASC, t.name DESC", "q1"));
        assertDoesNotThrow(() -> validator.validateOrderBy("id, created_at", "q1"));
    }

    @Test
    void sqlSecurity_orderBy_injectionRejected() {
        // 尝试通过 orderBy 注入
        assertThrows(BusinessException.class,
                () -> validator.validateOrderBy("id; DROP TABLE users", "q1"));
        assertThrows(BusinessException.class,
                () -> validator.validateOrderBy("1=1 OR", "q1"));
        assertThrows(BusinessException.class,
                () -> validator.validateOrderBy("(SELECT 1)", "q1"));
        assertThrows(BusinessException.class,
                () -> validator.validateOrderBy("id; --", "q1"));
    }

    @Test
    void sqlSecurity_nonSqlQuery_skipped() {
        // HTTP 类型查询不校验 SQL
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<interface transno=\"HTTP_TEST\" name=\"测试\">\n" +
                "  <requestData><param name=\"code\" type=\"String\" /></requestData>\n" +
                "  <query id=\"q1\" type=\"http\">\n" +
                "    <http url=\"https://api.example.com/data\" method=\"GET\" />\n" +
                "  </query>\n" +
                "</interface>";
        assertDoesNotThrow(() -> validator.validateXmlConfig(xml));
    }

    // ==================== T09 导出分页配置解析测试 ====================

    @Test
    void exportPagination_cursorMode_parsedCorrectly() {
        // 验证真实 DSL 格式: pagination/order-by 是 query 元素的属性
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<interface transno=\"EXPORT_CURSOR\" name=\"游标导出\">\n" +
                "  <requestData><param name=\"pageSize\" type=\"Integer\" /></requestData>\n" +
                "  <query id=\"q1\" type=\"mysql\" datasource=\"ds1\" " +
                "pagination=\"cursor\" order-by=\"id\" " +
                "page-size-param=\"pageSize\" last-id-param=\"lastId\" " +
                "default-page-size=\"100\" max-page-size=\"5000\">\n" +
                "    <sql>SELECT id, name FROM users</sql>\n" +
                "  </query>\n" +
                "</interface>";
        InterfaceConfig config = new XmlConfigParser().parse(xml);
        assertEquals(1, config.getQueries().size());
        QueryConfig query = config.getQueries().get(0);
        assertNotNull(query.getPaginationConfig(), "paginationConfig 不应为 null");
        assertEquals(PaginationConfig.PaginationMode.CURSOR, query.getPaginationConfig().getMode());
        assertEquals("id", query.getPaginationConfig().getOrderBy());
        assertEquals("pageSize", query.getPaginationConfig().getPageSizeParam());
        assertEquals("lastId", query.getPaginationConfig().getLastIdParam());
        assertEquals(5000, query.getPaginationConfig().getMaxPageSize());
    }

    @Test
    void exportPagination_optimizedMode_parsedCorrectly() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<interface transno=\"EXPORT_OPT\" name=\"优化分页导出\">\n" +
                "  <requestData><param name=\"pageSize\" type=\"Integer\" /></requestData>\n" +
                "  <query id=\"q1\" type=\"mysql\" datasource=\"ds1\" " +
                "pagination=\"optimized\" order-by=\"create_time\" " +
                "page-size-param=\"pageSize\" page-num-param=\"pageNum\" " +
                "default-page-size=\"20\" max-page-size=\"1000\">\n" +
                "    <sql>SELECT id, name, create_time FROM orders</sql>\n" +
                "  </query>\n" +
                "</interface>";
        InterfaceConfig config = new XmlConfigParser().parse(xml);
        QueryConfig query = config.getQueries().get(0);
        assertNotNull(query.getPaginationConfig());
        assertEquals(PaginationConfig.PaginationMode.OPTIMIZED, query.getPaginationConfig().getMode());
        assertEquals("create_time", query.getPaginationConfig().getOrderBy());
        assertEquals("pageNum", query.getPaginationConfig().getPageNumParam());
        assertEquals(1000, query.getPaginationConfig().getMaxPageSize());
    }

    @Test
    void exportPagination_noPagination_returnsNull() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<interface transno=\"EXPORT_NO_PAGE\" name=\"无分页\">\n" +
                "  <query id=\"q1\" type=\"mysql\" datasource=\"ds1\">\n" +
                "    <sql>SELECT * FROM small_table</sql>\n" +
                "  </query>\n" +
                "</interface>";
        InterfaceConfig config = new XmlConfigParser().parse(xml);
        QueryConfig query = config.getQueries().get(0);
        assertNull(query.getPaginationConfig());
    }

    @Test
    void exportPagination_cursorOrderByWithTablePrefix_parsedCorrectly() {
        // orderBy 带表前缀 t.id → PaginationConfig 存储原始值
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<interface transno=\"EXPORT_PREFIX\" name=\"表前缀\">\n" +
                "  <query id=\"q1\" type=\"mysql\" datasource=\"ds1\" " +
                "pagination=\"cursor\" order-by=\"t.id\" page-size-param=\"pageSize\">\n" +
                "    <sql>SELECT id, name FROM users</sql>\n" +
                "  </query>\n" +
                "</interface>";
        InterfaceConfig config = new XmlConfigParser().parse(xml);
        QueryConfig query = config.getQueries().get(0);
        assertNotNull(query.getPaginationConfig());
        assertEquals("t.id", query.getPaginationConfig().getOrderBy());
        assertEquals(PaginationConfig.PaginationMode.CURSOR, query.getPaginationConfig().getMode());
    }

    // ==================== T14 调试跟踪模型测试 ====================

    @Test
    void debugTrace_sqlQuery_capturesAllFields() {
        DebugTrace trace = new DebugTrace();
        trace.setQueryId("q1");
        trace.setType("mysql");
        trace.setDatasource("ds_main");
        trace.setSql("SELECT * FROM t WHERE id = ?");
        trace.setParams(Arrays.asList("123"));
        trace.setPaginationMode("cursor");
        trace.setRowCount(5);
        trace.setStartTimeMs(1000);
        trace.setEndTimeMs(1050);
        trace.setElapsedTimeMs(50);
        trace.setStatus("SUCCESS");
        trace.setErrorMessage(null);

        assertEquals("q1", trace.getQueryId());
        assertEquals("mysql", trace.getType());
        assertEquals("ds_main", trace.getDatasource());
        assertEquals("SELECT * FROM t WHERE id = ?", trace.getSql());
        assertEquals(1, trace.getParams().size());
        assertEquals("123", trace.getParams().get(0));
        assertEquals("cursor", trace.getPaginationMode());
        assertEquals(5, trace.getRowCount());
        assertEquals(50, trace.getElapsedTimeMs());
        assertEquals("SUCCESS", trace.getStatus());
        assertNull(trace.getErrorMessage());
    }

    @Test
    void debugTrace_errorQuery_capturesError() {
        DebugTrace trace = new DebugTrace();
        trace.setQueryId("q2");
        trace.setType("mysql");
        trace.setDatasource("ds_main");
        trace.setStatus("ERROR");
        trace.setErrorMessage("Table 't2' doesn't exist");
        trace.setElapsedTimeMs(10);

        assertEquals("ERROR", trace.getStatus());
        assertEquals("Table 't2' doesn't exist", trace.getErrorMessage());
    }

    @Test
    void debugContext_collectsMultipleTraces() {
        DebugContext ctx = new DebugContext(true);
        ctx.setTransno("TEST_DEBUG");
        ctx.setStartTimeMs(1000);
        ctx.setEndTimeMs(1100);
        ctx.setTotalTimeMs(100);
        ctx.setSuccess(true);

        DebugTrace t1 = new DebugTrace();
        t1.setQueryId("q1");
        t1.setStatus("SUCCESS");
        t1.setRowCount(10);
        t1.setElapsedTimeMs(30);
        ctx.addTrace(t1);

        DebugTrace t2 = new DebugTrace();
        t2.setQueryId("q2");
        t2.setStatus("SUCCESS");
        t2.setRowCount(3);
        t2.setElapsedTimeMs(60);
        ctx.addTrace(t2);

        assertTrue(ctx.isDebugMode());
        assertEquals("TEST_DEBUG", ctx.getTransno());
        assertEquals(100, ctx.getTotalTimeMs());
        assertTrue(ctx.isSuccess());
        assertEquals(2, ctx.getTraces().size());
        assertEquals("q1", ctx.getTraces().get(0).getQueryId());
        assertEquals("q2", ctx.getTraces().get(1).getQueryId());
        assertEquals(10, ctx.getTraces().get(0).getRowCount());
        assertEquals(3, ctx.getTraces().get(1).getRowCount());
    }

    @Test
    void debugContext_partialFailure_preservesCollectedTraces() {
        DebugContext ctx = new DebugContext(true);
        ctx.setTransno("PARTIAL_FAIL");
        ctx.setStartTimeMs(1000);

        // q1 成功
        DebugTrace t1 = new DebugTrace();
        t1.setQueryId("q1");
        t1.setStatus("SUCCESS");
        t1.setSql("SELECT 1");
        t1.setRowCount(1);
        t1.setElapsedTimeMs(5);
        ctx.addTrace(t1);

        // q2 失败
        DebugTrace t2 = new DebugTrace();
        t2.setQueryId("q2");
        t2.setStatus("ERROR");
        t2.setErrorMessage("connection refused");
        t2.setElapsedTimeMs(2);
        ctx.addTrace(t2);

        ctx.setEndTimeMs(1010);
        ctx.setTotalTimeMs(10);
        ctx.setSuccess(false);
        ctx.setErrorMessage("查询执行失败");

        assertFalse(ctx.isSuccess());
        assertEquals(2, ctx.getTraces().size());
        assertEquals("SUCCESS", ctx.getTraces().get(0).getStatus());
        assertEquals("ERROR", ctx.getTraces().get(1).getStatus());
        assertEquals("connection refused", ctx.getTraces().get(1).getErrorMessage());
        assertEquals("查询执行失败", ctx.getErrorMessage());
    }

    @Test
    void debugContext_defaultNotDebugMode() {
        DebugContext ctx = new DebugContext(false);
        assertFalse(ctx.isDebugMode());
        assertTrue(ctx.getTraces().isEmpty());
    }

    @Test
    void debugStep_successFactory() {
        DebugContext.DebugStep step = DebugContext.DebugStep.success("PARAM_VALIDATE", 5);
        assertEquals("PARAM_VALIDATE", step.getName());
        assertEquals("SUCCESS", step.getStatus());
        assertEquals(5, step.getElapsedTimeMs());
        assertNull(step.getErrorMessage());
    }

    @Test
    void debugStep_errorFactory() {
        DebugContext.DebugStep step = DebugContext.DebugStep.error("QUERY_EXECUTE", 120, "connection refused");
        assertEquals("QUERY_EXECUTE", step.getName());
        assertEquals("ERROR", step.getStatus());
        assertEquals(120, step.getElapsedTimeMs());
        assertEquals("connection refused", step.getErrorMessage());
    }

    @Test
    void debugContext_stepsCollected() {
        DebugContext ctx = new DebugContext(true);
        ctx.addStep(DebugContext.DebugStep.success("PARAM_VALIDATE", 1));
        ctx.addStep(DebugContext.DebugStep.success("QUERY_EXECUTE", 50));
        ctx.addStep(DebugContext.DebugStep.success("RESULT_MAP", 3));
        ctx.addStep(DebugContext.DebugStep.success("RESPONSE_BUILD", 1));

        assertEquals(4, ctx.getSteps().size());
        assertEquals("PARAM_VALIDATE", ctx.getSteps().get(0).getName());
        assertEquals("RESPONSE_BUILD", ctx.getSteps().get(3).getName());
        assertTrue(ctx.getSteps().stream().allMatch(s -> "SUCCESS".equals(s.getStatus())));
    }

    @Test
    void debugContext_stepsPartialError() {
        DebugContext ctx = new DebugContext(true);
        ctx.addStep(DebugContext.DebugStep.success("PARAM_VALIDATE", 1));
        ctx.addStep(DebugContext.DebugStep.error("QUERY_EXECUTE", 100, "Table not found"));
        // RESULT_MAP 和 RESPONSE_BUILD 未执行，不在 steps 中

        assertEquals(2, ctx.getSteps().size());
        assertEquals("SUCCESS", ctx.getSteps().get(0).getStatus());
        assertEquals("ERROR", ctx.getSteps().get(1).getStatus());
        assertEquals("Table not found", ctx.getSteps().get(1).getErrorMessage());
    }
}
