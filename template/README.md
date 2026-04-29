# XML 模板说明

| 模板 | 文件 | 覆盖场景 |
|------|------|----------|
| 01 | `01-simple-sql-query.xml` | 简单单表SQL查询 + 基础结果映射 |
| 02 | `02-dynamic-sql-query.xml` | `<if>` 条件 + `<foreach>` 集合遍历 |
| 03 | `03-cursor-pagination.xml` | 游标分页 (cursor) |
| 04 | `04-optimized-pagination.xml` | 优化分页 (optimized/子查询) |
| 05 | `05-http-query.xml` | HTTP GET 外部接口调用 |
| 06 | `06-http-post-query.xml` | HTTP POST + 请求体 + 响应路径提取 |
| 07 | `07-dubbo-query.xml` | Dubbo 泛化调用 + 注册中心配置 |
| 08 | `08-mongo-query.xml` | MongoDB 查询 (filter/projection/sort/limit/skip) |
| 09 | `09-parallel-orchestration.xml` | 多查询并行编排 (无依赖) |
| 10 | `10-dependency-orchestration.xml` | 依赖编排 (串行+并行混合, depends + #{} 引用) |
| 11 | `11-result-mapping-functions.xml` | 全部28个内置函数演示 |
| 12 | `12-multi-datasource.xml` | 多数据源 (MySQL + Doris + PostgreSQL) |
| 13 | `13-sql-http-hybrid.xml` | SQL + HTTP 混合查询 (依赖传递) |
| 14 | `14-sql-dubbo-hybrid.xml` | SQL + Dubbo 混合查询 |
| 15 | `15-no-resultmap-query.xml` | 无结果映射，直接返回原始结果 |
| 16 | `16-full-featured-composite.xml` | 综合全功能示例 |
