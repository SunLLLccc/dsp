package com.sunlc.dsp.engine.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB查询配置模型
 *
 * XML配置示例:
 * <query id="q1" type="mongo" datasource="mongo_main">
 *   <mongo collection="users">
 *     <filter>{"status": "#{status}"}</filter>
 *     <projection>{"name":1, "age":1}</projection>
 *     <sort>{"createdTime": -1}</sort>
 *     <limit>100</limit>
 *   </mongo>
 * </query>
 */
@Data
public class MongoQueryConfig {

    /** 集合名称 */
    private String collection;

    /** 查询过滤条件（JSON格式，支持#{}参数替换） */
    private String filter;

    /** 返回字段投影（JSON格式） */
    private String projection;

    /** 排序（JSON格式） */
    private String sort;

    /** 限制返回条数 */
    private Integer limit;

    /** 跳过条数（用于分页） */
    private Integer skip;
}
