package com.fintechervision.dsp.engine.executor;

import com.fintechervision.dsp.engine.model.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ResultMapperTest {

    private final ResultMapper resultMapper = new ResultMapper();

    // ==================== mapResult 测试 ====================

    @Test
    void mapResult_emptyQueryResult_returnsEmptyMap() {
        ResultMapConfig config = new ResultMapConfig();
        config.setId("testMap");
        config.setFields(Collections.emptyList());

        Object result = resultMapper.mapResult(Collections.emptyList(), config);
        assertEquals(Collections.emptyMap(), result);
    }

    @Test
    void mapResult_nullQueryResult_returnsEmptyMap() {
        ResultMapConfig config = new ResultMapConfig();
        config.setId("testMap");

        Object result = resultMapper.mapResult(null, config);
        assertEquals(Collections.emptyMap(), result);
    }

    @Test
    void mapResult_noFields_singleRow_returnsRawRow() {
        ResultMapConfig config = new ResultMapConfig();
        config.setId("testMap");
        config.setFields(Collections.emptyList());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("name", "test");

        Object result = resultMapper.mapResult(Collections.singletonList(row), config);
        assertEquals(row, result);
    }

    @Test
    void mapResult_noFields_multipleRows_returnsList() {
        ResultMapConfig config = new ResultMapConfig();
        config.setId("testMap");
        config.setFields(Collections.emptyList());

        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("id", 1);
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("id", 2);

        Object result = resultMapper.mapResult(Arrays.asList(row1, row2), config);
        assertTrue(result instanceof List);
        assertEquals(2, ((List<?>) result).size());
    }

    @Test
    void mapResult_withFields_singleRow_returnsMappedMap() {
        ResultMapConfig config = new ResultMapConfig();
        config.setId("testMap");
        ResultMapConfig.FieldMapping field1 = new ResultMapConfig.FieldMapping();
        field1.setName("userId");
        field1.setColumn("id");
        ResultMapConfig.FieldMapping field2 = new ResultMapConfig.FieldMapping();
        field2.setName("userName");
        field2.setColumn("user_name");
        config.setFields(Arrays.asList(field1, field2));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 100);
        row.put("user_name", "Alice");

        Object result = resultMapper.mapResult(Collections.singletonList(row), config);
        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> mapped = (Map<String, Object>) result;
        assertEquals(100, mapped.get("userId"));
        assertEquals("Alice", mapped.get("userName"));
        assertFalse(mapped.containsKey("id"));
        assertFalse(mapped.containsKey("user_name"));
    }

    @Test
    void mapResult_withFunction_appliesFunction() {
        ResultMapConfig config = new ResultMapConfig();
        config.setId("testMap");
        ResultMapConfig.FieldMapping field = new ResultMapConfig.FieldMapping();
        field.setName("nameUpper");
        field.setColumn("name");
        field.setFunction("fn:UPPER");
        config.setFields(Collections.singletonList(field));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "alice");

        Object result = resultMapper.mapResult(Collections.singletonList(row), config);
        @SuppressWarnings("unchecked")
        Map<String, Object> mapped = (Map<String, Object>) result;
        assertEquals("ALICE", mapped.get("nameUpper"));
    }

    @Test
    void mapResult_withIFF_functionWorks() {
        ResultMapConfig config = new ResultMapConfig();
        config.setId("testMap");
        ResultMapConfig.FieldMapping field = new ResultMapConfig.FieldMapping();
        field.setName("statusText");
        field.setColumn("status");
        field.setFunction("fn:IFF,活跃,停用");
        config.setFields(Collections.singletonList(field));

        // status = "true" -> should return "活跃"
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("status", "true");
        Object result1 = resultMapper.mapResult(Collections.singletonList(row1), config);
        assertEquals("活跃", ((Map<?, ?>) result1).get("statusText"));

        // status = "false" -> should return "停用"
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("status", "false");
        Object result2 = resultMapper.mapResult(Collections.singletonList(row2), config);
        assertEquals("停用", ((Map<?, ?>) result2).get("statusText"));
    }

    // ==================== buildResponse 测试 ====================

    @Test
    void buildResponse_nullConfig_returnsMappedResults() {
        Map<String, Object> mappedResults = new LinkedHashMap<>();
        mappedResults.put("userMap", Collections.singletonMap("name", "test"));

        Object result = resultMapper.buildResponse(null, mappedResults);
        assertEquals(mappedResults, result);
    }

    @Test
    void buildResponse_emptyFields_withResultMap_returnsData() {
        ResponseDataConfig config = new ResponseDataConfig();
        config.setResultMap("userMap");
        config.setFields(Collections.emptyList());

        Map<String, Object> mappedResults = new LinkedHashMap<>();
        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put("name", "Alice");
        mappedResults.put("userMap", userData);

        Object result = resultMapper.buildResponse(config, mappedResults);
        assertEquals(userData, result);
    }

    @Test
    void buildResponse_resultMapAndField_returnsWrappedData() {
        ResponseDataConfig config = new ResponseDataConfig();
        config.setResultMap("userMap");

        ResponseFieldConfig field = new ResponseFieldConfig();
        field.setName("user");
        config.setFields(Collections.singletonList(field));

        Map<String, Object> mappedResults = new LinkedHashMap<>();
        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put("name", "Alice");
        mappedResults.put("userMap", userData);

        Object result = resultMapper.buildResponse(config, mappedResults);
        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result;
        assertEquals(userData, response.get("user"));
    }

    @Test
    void buildResponse_mapToAsResultMapId_returnsReferencedData() {
        ResponseDataConfig config = new ResponseDataConfig();

        // mapTo 引用 resultMap ID "userStatsMap"
        ResponseFieldConfig field1 = new ResponseFieldConfig();
        field1.setName("userStats");
        field1.setMapTo("userStatsMap");

        // mapTo 引用 resultMap ID "orderStatsMap"
        ResponseFieldConfig field2 = new ResponseFieldConfig();
        field2.setName("orderStats");
        field2.setMapTo("orderStatsMap");

        config.setFields(Arrays.asList(field1, field2));

        Map<String, Object> mappedResults = new LinkedHashMap<>();
        Map<String, Object> userStats = Collections.singletonMap("total", 100);
        Map<String, Object> orderStats = Collections.singletonMap("total", 500);
        mappedResults.put("userStatsMap", userStats);
        mappedResults.put("orderStatsMap", orderStats);

        Object result = resultMapper.buildResponse(config, mappedResults);
        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result;
        assertEquals(userStats, response.get("userStats"));
        assertEquals(orderStats, response.get("orderStats"));
    }

    @Test
    void buildResponse_mapToAsSubField_extractsValue() {
        ResponseDataConfig config = new ResponseDataConfig();
        config.setResultMap("userMap");

        // mapTo = "name" 不是 resultMap ID，应视为从默认 resultMap 数据中提取子字段
        ResponseFieldConfig field = new ResponseFieldConfig();
        field.setName("userName");
        field.setMapTo("name");
        config.setFields(Collections.singletonList(field));

        Map<String, Object> mappedResults = new LinkedHashMap<>();
        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put("name", "Alice");
        userData.put("age", 20);
        mappedResults.put("userMap", userData);

        Object result = resultMapper.buildResponse(config, mappedResults);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result;
        assertEquals("Alice", response.get("userName"));
    }

    @Test
    void buildResponse_mapToPriority_resultMapIdOverSubField() {
        // 当 mapTo 既是 resultMap ID 又可能是子字段名时，优先作为 resultMap ID
        ResponseDataConfig config = new ResponseDataConfig();

        ResponseFieldConfig field = new ResponseFieldConfig();
        field.setName("data");
        field.setMapTo("orderMap"); // orderMap 存在于 mappedResults 中
        config.setFields(Collections.singletonList(field));

        Map<String, Object> mappedResults = new LinkedHashMap<>();
        Map<String, Object> orderData = new LinkedHashMap<>();
        orderData.put("amount", 999);
        mappedResults.put("orderMap", orderData);

        Object result = resultMapper.buildResponse(config, mappedResults);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result;
        // 应该返回整个 orderMap 的数据，而不是尝试从 null 默认 resultMap 中提取子字段
        assertEquals(orderData, response.get("data"));
    }

    @Test
    void buildResponse_withFunction_appliesFunction() {
        ResponseDataConfig config = new ResponseDataConfig();
        config.setResultMap("userMap");

        ResponseFieldConfig field = new ResponseFieldConfig();
        field.setName("nameUpper");
        field.setMapTo("name");
        field.setFunction("fn:UPPER");
        config.setFields(Collections.singletonList(field));

        Map<String, Object> mappedResults = new LinkedHashMap<>();
        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put("name", "alice");
        mappedResults.put("userMap", userData);

        Object result = resultMapper.buildResponse(config, mappedResults);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result;
        assertEquals("ALICE", response.get("nameUpper"));
    }

    @Test
    void buildResponse_listMapToSubField_extractsFromList() {
        ResponseDataConfig config = new ResponseDataConfig();
        config.setResultMap("userListMap");

        // mapTo = "name" 不是 resultMap ID，从 list 中每项提取 name
        ResponseFieldConfig field = new ResponseFieldConfig();
        field.setName("names");
        field.setMapTo("name");
        config.setFields(Collections.singletonList(field));

        Map<String, Object> mappedResults = new LinkedHashMap<>();
        List<Map<String, Object>> listData = new ArrayList<>();
        listData.add(Collections.singletonMap("name", "Alice"));
        listData.add(Collections.singletonMap("name", "Bob"));
        mappedResults.put("userListMap", listData);

        Object result = resultMapper.buildResponse(config, mappedResults);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) result;
        assertTrue(response.get("names") instanceof List);
        List<?> names = (List<?>) response.get("names");
        assertEquals(Arrays.asList("Alice", "Bob"), names);
    }
}
