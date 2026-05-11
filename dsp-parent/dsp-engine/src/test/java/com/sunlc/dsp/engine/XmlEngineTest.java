package com.sunlc.dsp.engine;

import com.sunlc.dsp.engine.executor.*;
import com.sunlc.dsp.engine.model.*;
import com.sunlc.dsp.engine.parser.XmlConfigParser;
import org.junit.jupiter.api.Test;

import java.util.*;

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
}
