# XML DSL 配置参考

`template/` 目录下有覆盖所有场景的模板。本文档描述 XML 配置的完整语法。

## 核心结构

```xml
<interface transno="xxx">
  <request>  <param name="xxx" type="string" required="true"/> </request>
  <queries>
    <query id="q1" type="mysql" datasource="ds1" depends="q0">  <!-- depends 用于 DAG 依赖排序 -->
      <sql>SELECT ... WHERE col = #{paramName} AND <if test="param != null">...</if>
        <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
      </sql>
    </query>
  </queries>
  <result-map>  <!-- 字段映射，支持 function/alias/format -->
    <field source="q1.col_a" alias="userName" function="UPPER"/>
  </result-map>
</interface>
```

## DSL 关键语法

### 参数引用

- `#{requestData.paramName}` — 请求参数
- `#{queryId.columnName}` — 上游查询结果

### 动态 SQL

- **条件判断**: `<if test="...">...</if>`
- **循环展开**: `<foreach collection="#{requestData.ids}" item="id" separator="," open="(" close=")">#{id}</foreach>`

### SpEL 注意事项

XML 中用 `$` 前缀（如 `#{requestData.xxx}`），`DynamicSqlHandler` 内部将 `$` 替换为 `#` 后交给 SpEL 解析。

### 分页

```xml
<!-- 游标分页 -->
<query pagination="cursor" order-by="id" page-size-param="pageSize" last-id-param="lastId" max-page-size="1000">

<!-- 优化子查询分页 -->
<query pagination="optimized" order-by="id" page-size-param="pageSize" last-id-param="lastId">
```

### 数据源类型

| type 值 | 执行器 | 说明 |
|---------|--------|------|
| `mysql` / `doris` / `sql` / `oracle` / `postgresql` | SqlExecutor | JDBC 查询 |
| `http` | HttpExecutor | HTTP 调用（GET/POST） |
| `dubbo` | DubboExecutor | Dubbo 泛化调用 |
| `mongo` | MongoExecutor | MongoDB 查询 |

### 内置函数

```
fn:DATE_FORMAT,yyyy-MM-dd HH:mm:ss
fn:JSON_EXTRACT,data.status
fn:NVL,默认值
fn:IFF,条件,true值,false值
fn:CONCAT,arg1,arg2
fn:UPPER
```

完整函数列表参见 `engine.function.FunctionRegistry`。
