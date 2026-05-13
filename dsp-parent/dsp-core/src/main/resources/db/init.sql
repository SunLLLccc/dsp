-- ============================================================
-- DSP 数据服务平台 - 数据库初始化脚本
-- 对应需求文档第7节 核心数据模型
-- ============================================================

CREATE DATABASE IF NOT EXISTS dsp_config DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE dsp_config;

-- -----------------------------------------------------------
-- 1. 接口基础信息表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS interface_info (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    transno         VARCHAR(64)     NOT NULL COMMENT '接口编码（唯一标识）',
    name            VARCHAR(128)    NOT NULL COMMENT '接口名称',
    system_name     VARCHAR(128)    DEFAULT NULL COMMENT '所属系统',
    system_id       BIGINT          DEFAULT NULL COMMENT '所属系统ID',
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-草稿 1-待审批 2-已驳回 3-已发布 4-已下线',
    description     VARCHAR(512)    DEFAULT NULL COMMENT '接口描述',
    current_version INT             DEFAULT 0 COMMENT '当前生效版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '更新人',
    updated_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_transno (transno)
) ENGINE=InnoDB COMMENT='接口基础信息表';

-- -----------------------------------------------------------
-- 2. 接口版本表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS interface_version (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    transno         VARCHAR(64)     NOT NULL COMMENT '接口编码',
    version_no      INT             NOT NULL COMMENT '版本号',
    input_schema    MEDIUMTEXT      DEFAULT NULL COMMENT '输入报文JSON Schema',
    output_schema   MEDIUMTEXT      DEFAULT NULL COMMENT '输出报文JSON Schema',
    change_log      VARCHAR(512)    DEFAULT NULL COMMENT '变更说明',
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-草稿 1-待审批 2-已驳回 3-已发布',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '提交人',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    published_time  DATETIME        DEFAULT NULL COMMENT '发布时间',
    PRIMARY KEY (id),
    KEY idx_transno (transno),
    UNIQUE KEY uk_transno_version (transno, version_no)
) ENGINE=InnoDB COMMENT='接口版本表';

-- -----------------------------------------------------------
-- 3. 审批记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS approval_record (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    transno         VARCHAR(64)     NOT NULL COMMENT '接口编码',
    version_no      INT             NOT NULL COMMENT '版本号',
    status          TINYINT         DEFAULT 0 COMMENT '审批状态：0-待审批 1-已通过 2-已驳回',
    applicant       VARCHAR(64)     NOT NULL COMMENT '申请人',
    apply_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    approver        VARCHAR(64)     DEFAULT NULL COMMENT '审批人',
    approve_time    DATETIME        DEFAULT NULL COMMENT '审批时间',
    reject_reason   VARCHAR(512)    DEFAULT NULL COMMENT '驳回原因',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_transno_version (transno, version_no)
) ENGINE=InnoDB COMMENT='审批记录表';

-- -----------------------------------------------------------
-- 4. 数据源配置表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS datasource_config (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    ds_name         VARCHAR(64)     NOT NULL COMMENT '数据源名称（Dynamic-DS使用的名称）',
    ds_type         VARCHAR(32)     NOT NULL COMMENT '数据源类型：MYSQL/DORIS/MONGODB/HTTP/DUBBO',
    jdbc_url        VARCHAR(512)    DEFAULT NULL COMMENT 'JDBC连接地址（关系型数据源）',
    username        VARCHAR(64)     DEFAULT NULL COMMENT '用户名',
    password        VARCHAR(256)    DEFAULT NULL COMMENT '密码（加密存储）',
    extra_config    TEXT            DEFAULT NULL COMMENT '扩展配置（JSON格式，如HTTP地址、Dubbo注册中心等）',
    pool_config     VARCHAR(512)    DEFAULT NULL COMMENT '连接池配置（JSON格式）',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ds_name (ds_name)
) ENGINE=InnoDB COMMENT='数据源配置表';

-- -----------------------------------------------------------
-- 5. 接口-数据源关联表（多对多）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS interface_datasource (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    transno         VARCHAR(64)     NOT NULL COMMENT '接口编码',
    ds_name         VARCHAR(64)     NOT NULL COMMENT '数据源名称',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_transno_ds (transno, ds_name)
) ENGINE=InnoDB COMMENT='接口-数据源关联表';

-- -----------------------------------------------------------
-- 6. 导出任务表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS export_task (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    transno         VARCHAR(64)     NOT NULL COMMENT '接口编码',
    params_snapshot TEXT            DEFAULT NULL COMMENT '参数快照',
    export_type     TINYINT         NOT NULL DEFAULT 1 COMMENT '导出类型：1-在线 2-离线',
    file_format     VARCHAR(16)     NOT NULL DEFAULT 'XLSX' COMMENT '文件格式：XLSX/CSV/TXT',
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-待处理 1-处理中 2-已完成 3-失败',
    file_path       VARCHAR(512)    DEFAULT NULL COMMENT '文件存储路径',
    total_rows      BIGINT          DEFAULT 0 COMMENT '总行数',
    progress        INT             DEFAULT 0 COMMENT '进度百分比',
    error_msg       VARCHAR(512)    DEFAULT NULL COMMENT '错误信息',
    apply_user      VARCHAR(64)     DEFAULT NULL COMMENT '申请人',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    finished_time   DATETIME        DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (id),
    KEY idx_transno (transno),
    KEY idx_status (status)
) ENGINE=InnoDB COMMENT='导出任务表';

-- -----------------------------------------------------------
-- 7. 应用授权表（appId → transno白名单）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_auth (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    app_id          VARCHAR(64)     NOT NULL COMMENT '应用ID',
    app_name        VARCHAR(128)    NOT NULL COMMENT '应用名称',
    app_secret      VARCHAR(128)    NOT NULL COMMENT '应用密钥（用于签发JWT）',
    allowed_transnos TEXT           DEFAULT NULL COMMENT '授权接口列表（逗号分隔，*表示全部）',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_id (app_id)
) ENGINE=InnoDB COMMENT='应用授权表';

-- -----------------------------------------------------------
-- 8. 操作审计日志表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    app_id          VARCHAR(64)     DEFAULT NULL COMMENT '应用ID',
    transno         VARCHAR(64)     DEFAULT NULL COMMENT '接口编码',
    operation       VARCHAR(32)     NOT NULL COMMENT '操作类型：QUERY/EXPORT/PUBLISH/OFFLINE等',
    request_data    TEXT            DEFAULT NULL COMMENT '请求参数',
    response_code   VARCHAR(16)     DEFAULT NULL COMMENT '响应状态码',
    cost_time       BIGINT          DEFAULT NULL COMMENT '耗时（毫秒）',
    ip              VARCHAR(64)     DEFAULT NULL COMMENT '请求IP',
    operator        VARCHAR(64)     DEFAULT NULL COMMENT '操作人',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_transno (transno),
    KEY idx_created_time (created_time)
) ENGINE=InnoDB COMMENT='操作审计日志表';

-- -----------------------------------------------------------
-- 9. 接口模板信息表（维护各接口的XML配置）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS interface_template (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    transno         VARCHAR(64)     NOT NULL COMMENT '接口编码',
    system_name     VARCHAR(128)    DEFAULT NULL COMMENT '所属系统',
    system_id       BIGINT          DEFAULT NULL COMMENT '所属系统ID',
    interface_name  VARCHAR(128)    DEFAULT NULL COMMENT '接口名称',
    xml_content     MEDIUMTEXT      DEFAULT NULL COMMENT 'XML配置内容',
    version_no      INT             NOT NULL DEFAULT 1 COMMENT '当前版本号',
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-草稿 1-待审批 2-已驳回 3-已发布 4-已下线',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '更新人',
    updated_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_transno (transno)
) ENGINE=InnoDB COMMENT='接口模板信息表';

-- -----------------------------------------------------------
-- 10. 接口模板历史表（XML模板变更记录）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS interface_template_history (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    template_id     BIGINT          NOT NULL COMMENT '关联模板表ID',
    transno         VARCHAR(64)     NOT NULL COMMENT '接口编码',
    system_name     VARCHAR(128)    DEFAULT NULL COMMENT '所属系统',
    interface_name  VARCHAR(128)    DEFAULT NULL COMMENT '接口名称',
    xml_content     MEDIUMTEXT      DEFAULT NULL COMMENT 'XML配置内容',
    version_no      INT             NOT NULL COMMENT '版本号',
    change_log      VARCHAR(512)    DEFAULT NULL COMMENT '变更说明',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_transno (transno),
    KEY idx_template_id (template_id)
) ENGINE=InnoDB COMMENT='接口模板历史表';

-- ============================================================
-- 11. 接口模板初始数据（16个示例模板）
-- ============================================================

-- ============================================================
-- 14. 接口模板初始数据（16个示例模板）
-- ============================================================

INSERT INTO interface_template (transno, system_name, interface_name, xml_content, version_no, status, created_by) VALUES
('USER_GET_BY_ID', '示例系统', '根据ID查询用户', '<?xml version="1.0" encoding="UTF-8"?>
<!--
  模板01: 简单单表SQL查询
  场景: 最基础的查询，单数据源、单SQL、无动态条件、无分页、简单结果映射
  适用: 根据ID查详情、字典查询等简单场景

  要点:
    - datasource: 引用管理后台已配置的数据源（通过数据源管理页面配置）
    - resultMap: 数据库字段→输出字段映射，支持 function 函数转换
    - responseData: resultMap 属性指定默认结果映射，field name 为输出键名
-->
<interface transno="USER_GET_BY_ID" name="根据ID查询用户" description="根据用户ID查询用户详细信息">

    <!-- 请求参数定义 -->
    <requestData>
        <param name="userId" type="String" required="true" description="用户ID" />
    </requestData>

    <!-- 引用已配置的数据源（通过管理后台数据源管理页面添加，dsName 为唯一键） -->
    <datasource name="ds_main" />

    <!-- SQL查询: 使用 #{$requestData[''paramName'']} 引用请求参数（$前缀+SpEL括号语法） -->
    <query id="q1" type="mysql" datasource="ds_main">
        SELECT id, user_name, email, phone, status, created_at, updated_at
        FROM users
        WHERE id = #{$requestData[''userId'']}
    </query>

    <!-- 结果映射: column=数据库字段, name=输出字段, function=fn:函数名,参数 -->
    <resultMap id="userMap" query="q1">
        <field name="userId" column="id" />
        <field name="userName" column="user_name" />
        <field name="email" column="email" />
        <field name="phone" column="phone" />
        <field name="status" column="status" />
        <field name="createdAt" column="created_at" function="fn:DATE_FORMAT,yyyy-MM-dd HH:mm:ss" />
        <field name="updatedAt" column="updated_at" function="fn:DATE_FORMAT,yyyy-MM-dd HH:mm:ss" />
    </resultMap>

    <!-- 响应组装: resultMap 指定默认结果映射, field name 作为输出键 -->
    <responseData resultMap="userMap">
        <field name="user" as="map" />
    </responseData>

</interface>', 1, 3, 'system'),

('USER_LIST_QUERY', '示例系统', '用户列表查询', '<?xml version="1.0" encoding="UTF-8"?>
<!--
  模板02: 动态SQL查询
  场景: 多条件可选查询，使用 <if> 条件判断和 <foreach> 集合遍历
  适用: 列表筛选、多条件组合查询

  要点:
    - <if test="SpEL表达式">: 条件为true时追加SQL片段
      表达式语法: $requestData[''paramName''] != null (注意$前缀，引擎内部转为#)
    - <foreach>: 集合遍历生成IN语句
      collection: SpEL表达式指向集合参数
      item: 迭代变量名，在SQL中用 #{item} 引用
      separator/open/close: 分隔符和包裹符号
-->
<interface transno="USER_LIST_QUERY" name="用户列表查询" description="支持多条件筛选的用户列表查询">

    <requestData>
        <param name="userName" type="String" required="false" description="用户名（模糊查询）" />
        <param name="status" type="String" required="false" defaultValue="active" description="状态" />
        <param name="departmentId" type="String" required="false" description="部门ID" />
        <param name="ids" type="List" required="false" description="用户ID列表（批量查询）" />
        <param name="minAge" type="Integer" required="false" description="最小年龄" />
        <param name="maxAge" type="Integer" required="false" description="最大年龄" />
    </requestData>

    <!-- 引用已配置的数据源 -->
    <datasource name="ds_main" />

    <query id="q1" type="mysql" datasource="ds_main">
        SELECT id, user_name, email, phone, status, age, department_id, created_at
        FROM users
        WHERE 1=1
        <if test="$requestData[''userName''] != null">AND user_name LIKE #{$requestData[''userName'']}</if>
        <if test="$requestData[''status''] != null">AND status = #{$requestData[''status'']}</if>
        <if test="$requestData[''departmentId''] != null">AND department_id = #{$requestData[''departmentId'']}</if>
        <if test="$requestData[''minAge''] != null">AND age &gt;= #{$requestData[''minAge'']}</if>
        <if test="$requestData[''maxAge''] != null">AND age &lt;= #{$requestData[''maxAge'']}</if>
        <foreach collection="$requestData[''ids'']" item="id" separator="," open="AND id IN (" close=")">
            #{id}
        </foreach>
        ORDER BY created_at DESC
    </query>

    <resultMap id="userListMap" query="q1">
        <field name="userId" column="id" />
        <field name="userName" column="user_name" />
        <field name="email" column="email" />
        <field name="phone" column="phone" />
        <field name="status" column="status" />
        <field name="age" column="age" function="fn:TYPE_CONVERT,INTEGER" />
        <field name="departmentId" column="department_id" />
        <field name="createdAt" column="created_at" function="fn:DATE_FORMAT,yyyy-MM-dd" />
    </resultMap>

    <responseData resultMap="userListMap">
        <field name="list" as="list" />
    </responseData>

</interface>', 1, 3, 'system'),

('ORDER_LIST_CURSOR', '示例系统', '订单游标分页查询', '<?xml version="1.0" encoding="UTF-8"?>
<!--
  模板03: 游标分页查询
  场景: 大数据量列表查询，使用游标分页（cursor-based pagination）避免深分页性能问题
  适用: 移动端下拉加载、海量数据分页浏览

  要点:
    - pagination="cursor": 游标分页模式，引擎自动追加 WHERE id > lastId LIMIT pageSize
    - order-by: 游标排序字段（默认id）
    - page-size-param: 请求中每页条数的参数名
    - last-id-param: 请求中上一页最后ID的参数名
    - default-page-size / max-page-size: 默认和最大每页条数
-->
<interface transno="ORDER_LIST_CURSOR" name="订单游标分页查询" description="基于游标的订单列表分页查询">

    <requestData>
        <param name="status" type="String" required="false" description="订单状态" />
        <param name="lastId" type="String" required="false" description="上一页最后记录ID（首页不传）" />
        <param name="pageSize" type="Integer" required="false" defaultValue="20" description="每页条数" />
    </requestData>

    <!-- 引用已配置的数据源 -->
    <datasource name="ds_main" />

    <query id="q1" type="mysql" datasource="ds_main"
           pagination="cursor" order-by="id"
           page-size-param="pageSize" last-id-param="lastId"
           default-page-size="20" max-page-size="500">
        SELECT id, order_no, customer_name, amount, status, created_at
        FROM orders
        WHERE 1=1
        <if test="$requestData[''status''] != null">AND status = #{$requestData[''status'']}</if>
    </query>

    <resultMap id="orderMap" query="q1">
        <field name="orderId" column="id" />
        <field name="orderNo" column="order_no" />
        <field name="customerName" column="customer_name" />
        <field name="amount" column="amount" function="fn:ROUND,2" />
        <field name="status" column="status" />
        <field name="createdAt" column="created_at" function="fn:DATE_FORMAT,yyyy-MM-dd HH:mm:ss" />
    </resultMap>

    <responseData resultMap="orderMap">
        <field name="list" as="list" />
    </responseData>

</interface>', 1, 3, 'system'),

('REPORT_LIST_OPTIMIZED', '示例系统', '报表优化分页查询', '<?xml version="1.0" encoding="UTF-8"?>
<!--
  模板04: 优化分页查询
  场景: 深分页优化查询，使用子查询方式避免大OFFSET性能问题
  适用: PC端传统分页、报表翻页查询

  要点:
    - pagination="optimized": 优化分页模式（子查询方式）
    - 引擎自动改写SQL:
      第1页(offset=0): ORDER BY id LIMIT pageSize
      第N页(offset>0): WHERE id >= (SELECT id FROM table ORDER BY id LIMIT offset, 1) ORDER BY id LIMIT pageSize
    - page-num-param: 页码参数名（从1开始）
    - 其他属性同游标分页
-->
<interface transno="REPORT_LIST_OPTIMIZED" name="报表优化分页查询" description="使用子查询优化的深分页报表查询">

    <requestData>
        <param name="reportType" type="String" required="false" description="报表类型" />
        <param name="pageNum" type="Integer" required="false" defaultValue="1" description="页码（从1开始）" />
        <param name="pageSize" type="Integer" required="false" defaultValue="20" description="每页条数" />
    </requestData>

    <!-- 引用已配置的数据源 -->
    <datasource name="ds_main" />

    <query id="q1" type="mysql" datasource="ds_main"
           pagination="optimized" order-by="id"
           page-size-param="pageSize" page-num-param="pageNum"
           default-page-size="20" max-page-size="100">
        SELECT id, report_name, report_type, generated_by, file_size, status, created_at
        FROM report_records
        WHERE 1=1
        <if test="$requestData[''reportType''] != null">AND report_type = #{$requestData[''reportType'']}</if>
    </query>

    <resultMap id="reportMap" query="q1">
        <field name="id" column="id" />
        <field name="reportName" column="report_name" />
        <field name="reportType" column="report_type" />
        <field name="generatedBy" column="generated_by" />
        <field name="fileSize" column="file_size" function="fn:TYPE_CONVERT,LONG" />
        <field name="status" column="status" />
        <field name="createdAt" column="created_at" function="fn:DATE_FORMAT,yyyy-MM-dd HH:mm:ss" />
    </resultMap>

    <responseData resultMap="reportMap">
        <field name="list" as="list" />
    </responseData>

</interface>', 1, 3, 'system'),

('WEATHER_QUERY', '示例系统', '天气查询', '<?xml version="1.0" encoding="UTF-8"?>
<!--
  模板05: HTTP外部接口调用
  场景: 调用外部HTTP API获取数据，支持GET/POST、请求头、请求体、响应路径提取
  适用: 聚合第三方接口数据、微服务间HTTP调用
  说明: HTTP查询不需要 datasource 配置，直接在 <http> 标签中定义URL和请求参数
-->
<interface transno="WEATHER_QUERY" name="天气查询" description="调用外部天气API查询城市天气信息">

    <requestData>
        <param name="cityCode" type="String" required="true" description="城市编码" />
        <param name="token" type="String" required="true" description="API访问令牌" />
    </requestData>

    <!-- HTTP查询不需要datasource配置，无需声明 <datasource> -->

    <!-- HTTP GET 请求示例 -->
    <query id="q1" type="http">
        <http url="https://api.weather.com/v1/current?city=#{$requestData[''cityCode'']}"
              method="GET"
              headers=''{"Authorization":"Bearer #{$requestData[''token'']}","X-Request-Id":"#{$requestData[''cityCode'']}"}''
              responsePath="data.current" />
    </query>

    <resultMap id="weatherMap" query="q1">
        <field name="temperature" column="temperature" function="fn:TYPE_CONVERT,DOUBLE" />
        <field name="humidity" column="humidity" function="fn:TYPE_CONVERT,INTEGER" />
        <field name="description" column="description" />
        <field name="windSpeed" column="wind_speed" function="fn:TYPE_CONVERT,DOUBLE" />
        <field name="updateTime" column="update_time" />
    </resultMap>

    <responseData resultMap="weatherMap">
        <field name="weather" as="map" />
    </responseData>

</interface>', 1, 3, 'system'),

('RISK_CHECK', '示例系统', '风控检查', '<?xml version="1.0" encoding="UTF-8"?>
<!--
  模板06: HTTP POST请求调用
  场景: 调用外部POST接口，携带请求体
  适用: 提交数据到外部系统、调用需要请求体的API
  说明: HTTP查询不需要 datasource 配置，直接在 <http> 标签中定义URL和请求参数
       参数替换语法: #{$requestData[''paramName'']} 用于URL/headers/body中的参数替换
-->
<interface transno="RISK_CHECK" name="风控检查" description="调用风控系统POST接口进行风险评估">

    <requestData>
        <param name="userId" type="String" required="true" description="用户ID" />
        <param name="amount" type="String" required="true" description="交易金额" />
        <param name="token" type="String" required="true" description="认证令牌" />
    </requestData>

    <!-- HTTP查询不需要datasource配置，无需声明 <datasource> -->

    <!-- HTTP POST 请求示例 -->
    <query id="q1" type="http">
        <http url="https://risk.internal.com/api/v1/evaluate"
              method="POST"
              headers=''{"Authorization":"Bearer #{$requestData[''token'']}","Content-Type":"application/json"}''
              body=''{"userId":"#{$requestData[''userId'']}","amount":"#{$requestData[''amount'']}","scene":"TRADE"}''
              responsePath="data.riskResult" />
    </query>

    <resultMap id="riskMap" query="q1">
        <field name="riskLevel" column="risk_level" />
        <field name="riskScore" column="risk_score" function="fn:TYPE_CONVERT,INTEGER" />
        <field name="decision" column="decision" />
        <field name="reason" column="reason" function="fn:IFNULL,未提供原因" />
    </resultMap>

    <responseData resultMap="riskMap">
        <field name="riskResult" as="map" />
    </responseData>

</interface>', 1, 3, 'system'),

('CUSTOMER_PROFILE', '示例系统', '客户画像查询', '<?xml version="1.0" encoding="UTF-8"?>
<!--
  模板07: Dubbo泛化调用
  场景: 通过Dubbo泛化调用远程服务，无需引入服务接口JAR包
  适用: 调用内部Dubbo微服务获取数据

  要点:
    - datasource: 引用管理后台已配置的DUBBO类型数据源（extraConfig中配置注册中心地址）
    - <dubbo>标签: service(接口全限定名), method(方法名), version(默认1.0.0), group, timeout(默认3000ms)
    - <param>: 方法参数, type=Java类型, value支持 #{$requestData[''paramName'']} 参数替换
    - 引擎内部通过 DubboExecutor 获取 RegistryConfig 进行泛化调用
-->
<interface transno="CUSTOMER_PROFILE" name="客户画像查询" description="通过Dubbo调用客户服务获取客户画像信息">

    <requestData>
        <param name="customerId" type="String" required="true" description="客户ID" />
        <param name="sceneType" type="String" required="false" defaultValue="FULL" description="场景类型" />
    </requestData>

    <!-- 引用已配置的Dubbo数据源（管理后台配置，类型选DUBBO，extraConfig中填写注册中心地址） -->
    <datasource name="dubbo_ds" />

    <query id="q1" type="dubbo" datasource="dubbo_ds">
        <dubbo service="com.fintechervision.customer.CustomerProfileService"
               method="getProfile"
               version="1.0.0"
               group="production"
               timeout="5000">
            <param type="String" value="#{$requestData[''customerId'']}" />
            <param type="String" value="#{$requestData[''sceneType'']}" />
        </dubbo>
    </query>

    <resultMap id="profileMap" query="q1">
        <field name="customerId" column="customerId" />
        <field name="customerName" column="customerName" function="fn:UPPER" />
        <field name="level" column="level" function="fn:IFNULL,UNKNOWN" />
        <field name="totalAssets" column="totalAssets" function="fn:ROUND,2" />
        <field name="riskPreference" column="riskPreference" />
        <field name="tags" column="tags" function="fn:JSON_EXTRACT,list" />
    </resultMap>

    <responseData resultMap="profileMap">
        <field name="profile" as="map" />
    </responseData>

</interface>', 1, 3, 'system'),

('USER_LOG_QUERY', '示例系统', '用户操作日志查询', '<?xml version="1.0" encoding="UTF-8"?>
<!--
  模板08: MongoDB查询
  场景: 查询MongoDB集合数据，支持过滤、投影、排序、分页
  适用: 日志查询、非结构化数据查询

  要点:
    - datasource: 引用管理后台已配置的MONGO类型数据源（url格式 mongodb://host:port/database）
    - <mongo>标签: collection(集合名), 子元素 filter/projection/sort/limit/skip
    - filter中参数替换: #{paramName} — 引擎会替换为请求参数值
      注意: MongoDB参数替换使用扁平参数名(如 #{userId})，不是 #{$requestData[''userId'']}
      因为 MongoExecutor 将 requestData 和 previousResults 合并为扁平Map
    - 需要引入 spring-boot-starter-data-mongodb 依赖
-->
<interface transno="USER_LOG_QUERY" name="用户操作日志查询" description="从MongoDB查询用户操作日志">

    <requestData>
        <param name="userId" type="String" required="true" description="用户ID" />
        <param name="action" type="String" required="false" description="操作类型" />
        <param name="startDate" type="String" required="false" description="开始日期(yyyy-MM-dd)" />
    </requestData>

    <!-- 引用已配置的MongoDB数据源（管理后台配置，类型选MONGO，url填写 mongodb://host:port/database） -->
    <datasource name="mongo_main" />

    <query id="q1" type="mongo" datasource="mongo_main">
        <mongo collection="user_operation_logs">
            <filter>{"userId": "#{userId}"}</filter>
            <projection>{"userId":1, "action":1, "detail":1, "ip":1, "createdTime":1}</projection>
            <sort>{"createdTime": -1}</sort>
            <limit>50</limit>
            <skip>0</skip>
        </mongo>
    </query>

    <resultMap id="logMap" query="q1">
        <field name="userId" column="userId" />
        <field name="action" column="action" />
        <field name="detail" column="detail" />
        <field name="ip" column="ip" />
        <field name="createdTime" column="createdTime" />
    </resultMap>

    <responseData resultMap="logMap">
        <field name="list" as="list" />
    </responseData>

</interface>', 1, 3, 'system'),

('DASHBOARD_OVERVIEW', '示例系统', '仪表盘概览', '<?xml version="1.0" encoding="UTF-8"?>
<!--
  模板09: 多查询并行编排
  场景: 多个独立查询并行执行，互不依赖，结果汇总到响应中
  适用: 仪表盘、聚合多个独立数据源的概览页面

  要点:
    - 没有 depends 属性的查询自动并行执行（引擎使用 CompletableFuture + 线程池）
    - responseData 中 mapTo 属性引用 resultMap 的 id，将结果组装到指定输出键
    - 多个并行查询的结果通过各自的 resultMap 映射后统一组装
-->
<interface transno="DASHBOARD_OVERVIEW" name="仪表盘概览" description="并行查询多个数据源聚合仪表盘数据">

    <requestData>
        <param name="dateRange" type="String" required="false" defaultValue="7d" description="日期范围" />
    </requestData>

    <!-- 引用已配置的数据源 -->
    <datasource name="ds_main" />

    <!-- 查询1: 用户统计（无depends，并行执行） -->
    <query id="q_user_stats" type="mysql" datasource="ds_main">
        SELECT COUNT(*) AS total_users,
               SUM(CASE WHEN status = ''active'' THEN 1 ELSE 0 END) AS active_users,
               SUM(CASE WHEN created_at &gt;= DATE_SUB(NOW(), INTERVAL 7 DAY) THEN 1 ELSE 0 END) AS new_users
        FROM users
    </query>

    <!-- 查询2: 订单统计（无depends，并行执行） -->
    <query id="q_order_stats" type="mysql" datasource="ds_main">
        SELECT COUNT(*) AS total_orders,
               COALESCE(SUM(amount), 0) AS total_amount,
               COALESCE(AVG(amount), 0) AS avg_amount
        FROM orders
        WHERE created_at &gt;= DATE_SUB(NOW(), INTERVAL 7 DAY)
    </query>

    <!-- 查询3: 接口调用统计（无depends，并行执行） -->
    <query id="q_api_stats" type="mysql" datasource="ds_main">
        SELECT COUNT(*) AS total_calls,
               SUM(CASE WHEN status = ''SUCCESS'' THEN 1 ELSE 0 END) AS success_calls,
               SUM(CASE WHEN status = ''FAIL'' THEN 1 ELSE 0 END) AS fail_calls
        FROM api_call_log
        WHERE created_at &gt;= DATE_SUB(NOW(), INTERVAL 7 DAY)
    </query>

    <!-- 各查询结果映射 -->
    <resultMap id="userStatsMap" query="q_user_stats">
        <field name="totalUsers" column="total_users" function="fn:TYPE_CONVERT,LONG" />
        <field name="activeUsers" column="active_users" function="fn:TYPE_CONVERT,LONG" />
        <field name="newUsers" column="new_users" function="fn:TYPE_CONVERT,LONG" />
    </resultMap>

    <resultMap id="orderStatsMap" query="q_order_stats">
        <field name="totalOrders" column="total_orders" function="fn:TYPE_CONVERT,LONG" />
        <field name="totalAmount" column="total_amount" function="fn:ROUND,2" />
        <field name="avgAmount" column="avg_amount" function="fn:ROUND,2" />
    </resultMap>

    <resultMap id="apiStatsMap" query="q_api_stats">
        <field name="totalCalls" column="total_calls" function="fn:TYPE_CONVERT,LONG" />
        <field name="successCalls" column="success_calls" function="fn:TYPE_CONVERT,LONG" />
        <field name="failCalls" column="fail_calls" function="fn:TYPE_CONVERT,LONG" />
    </resultMap>

    <!-- 汇总响应: mapTo 引用 resultMap 的 id，将各查询结果组装到对应输出键 -->
    <responseData>
        <field name="userStats" mapTo="userStatsMap" as="map" />
        <field name="orderStats" mapTo="orderStatsMap" as="map" />
        <field name="apiStats" mapTo="apiStatsMap" as="map" />
    </responseData>

</interface>', 1, 3, 'system'),

('USER_DETAIL_AGGREGATE', '示例系统', '用户详情聚合', '<?xml version="1.0" encoding="UTF-8"?>
<!--
  模板10: 多查询依赖编排（串行+并行混合）
  场景: 查询之间存在依赖关系，后续查询需要前序查询的结果作为参数
  适用: 级联查询、先查后聚合、跨表关联查询

  要点:
    - depends="q1,q2": 当前查询依赖 q1 和 q2 都完成后才执行
    - 依赖查询的结果通过 #{queryId.fieldName} 引用
    - 单行结果: 引擎存储为 Map，通过 #{q1.fieldName} 直接引用
    - 多行结果: 引擎存储为 List<Map>，需要通过索引或特殊方式引用
    - 同层依赖的查询并行执行（如q2_dept和q2_perms都依赖q1_user，它们之间并行）
-->
<interface transno="USER_DETAIL_AGGREGATE" name="用户详情聚合" description="先查用户基本信息，再根据结果查询关联数据">

    <requestData>
        <param name="userId" type="String" required="true" description="用户ID" />
    </requestData>

    <!-- 引用已配置的数据源 -->
    <datasource name="ds_main" />

    <!-- 第一步: 查询用户基本信息（无依赖，先执行） -->
    <query id="q1_user" type="mysql" datasource="ds_main">
        SELECT id, user_name, department_id, level, email
        FROM users
        WHERE id = #{$requestData[''userId'']}
    </query>

    <!-- 第二步: 根据用户所属部门查询部门信息（依赖q1，与q2_perms并行） -->
    <query id="q2_dept" type="mysql" datasource="ds_main" depends="q1_user">
        SELECT id, dept_name, manager_id, description
        FROM departments
        WHERE id = #{$q1_user[''department_id'']}
    </query>

    <!-- 第二步(并行): 根据用户等级查询权限列表（依赖q1，与q2_dept并行） -->
    <query id="q2_perms" type="mysql" datasource="ds_main" depends="q1_user">
        SELECT permission_code, permission_name, resource_type
        FROM permissions
        WHERE min_level &lt;= #{$q1_user[''level'']}
    </query>

    <!-- 第三步: 根据部门经理ID查询经理信息（依赖q2_dept） -->
    <query id="q3_manager" type="mysql" datasource="ds_main" depends="q2_dept">
        SELECT id, user_name, email, phone
        FROM users
        WHERE id = #{$q2_dept[''manager_id'']}
    </query>

    <!-- 结果映射 -->
    <resultMap id="userMap" query="q1_user">
        <field name="userId" column="id" />
        <field name="userName" column="user_name" />
        <field name="departmentId" column="department_id" />
        <field name="level" column="level" function="fn:TYPE_CONVERT,INTEGER" />
        <field name="email" column="email" />
    </resultMap>

    <resultMap id="deptMap" query="q2_dept">
        <field name="deptId" column="id" />
        <field name="deptName" column="dept_name" />
        <field name="managerId" column="manager_id" />
        <field name="description" column="description" />
    </resultMap>

    <resultMap id="permMap" query="q2_perms">
        <field name="code" column="permission_code" />
        <field name="name" column="permission_name" />
        <field name="resourceType" column="resource_type" />
    </resultMap>

    <resultMap id="managerMap" query="q3_manager">
        <field name="managerId" column="id" />
        <field name="managerName" column="user_name" />
        <field name="email" column="email" />
        <field name="phone" column="phone" />
    </resultMap>

    <!-- 组装响应: mapTo 引用 resultMap 的 id -->
    <responseData>
        <field name="user" mapTo="userMap" as="map" />
        <field name="department" mapTo="deptMap" as="map" />
        <field name="permissions" mapTo="permMap" as="list" />
        <field name="manager" mapTo="managerMap" as="map" />
    </responseData>

</interface>', 1, 3, 'system'),

('DATA_TRANSFORM_DEMO', '示例系统', '数据转换演示', '<?xml version="1.0" encoding="UTF-8"?>
<!--
  模板11: 结果映射与函数转换
  场景: 展示所有26个内置函数的使用方式
  适用: 需要对查询结果进行数据转换和格式化的场景
  说明: function="fn:函数名,参数1,参数2" — 第一个参数始终是字段值(自动传入)，后续参数来自逗号分隔列表

  函数分类:
    日期: DATE_FORMAT, DATE_ADD, DATE_SUB, WORKDAYS
    字符串: UPPER, LOWER, TRIM, CONCAT, CONCAT_WS, SUBSTRING, REPLACE, PAD_LEFT, PAD_RIGHT, LENGTH, LIKE_MATCH, REGEX_MATCH
    类型: TYPE_CONVERT
    数学: ROUND, CEIL, FLOOR
    空值: IFNULL, NVL
    条件: IFF
    JSON: JSON_EXTRACT
    聚合: SUM, AVG, COUNT, MAX, MIN
-->
<interface transno="DATA_TRANSFORM_DEMO" name="数据转换演示" description="演示所有内置函数的结果映射转换">

    <requestData>
        <param name="userId" type="String" required="true" description="用户ID" />
    </requestData>

    <!-- 引用已配置的数据源 -->
    <datasource name="ds_main" />

    <query id="q1" type="mysql" datasource="ds_main">
        SELECT id, user_name, email, phone, amount, rate, json_ext,
               created_at, birthday, status, remark, start_date, end_date
        FROM user_detail
        WHERE id = #{$requestData[''userId'']}
    </query>

    <resultMap id="transformMap" query="q1">

        <!-- ====== 日期函数 (4个) ====== -->
        <!-- DATE_FORMAT: 格式化日期时间，参数: 原始日期, 格式模式 -->
        <field name="createdAt" column="created_at" function="fn:DATE_FORMAT,yyyy-MM-dd HH:mm:ss" />
        <!-- DATE_ADD: 日期加天数，参数: 原始日期, 天数 -->
        <field name="expireDate" column="created_at" function="fn:DATE_ADD,30" />
        <!-- DATE_SUB: 日期减天数，参数: 原始日期, 天数 -->
        <field name="startDate" column="created_at" function="fn:DATE_SUB,7" />
        <!-- WORKDAYS: 计算两个日期间的工作日数，参数: 开始日期, 结束日期 -->
        <field name="workdays" column="start_date" function="fn:WORKDAYS,2026-12-31" />

        <!-- ====== 字符串函数 (12个) ====== -->
        <!-- UPPER: 转大写 -->
        <field name="userNameUpper" column="user_name" function="fn:UPPER" />
        <!-- LOWER: 转小写 -->
        <field name="emailLower" column="email" function="fn:LOWER" />
        <!-- TRIM: 去首尾空格 -->
        <field name="phoneTrimmed" column="phone" function="fn:TRIM" />
        <!-- CONCAT: 字符串拼接(所有参数拼接)，参数: 值1, 值2, ... -->
        <field name="displayName" column="user_name" function="fn:CONCAT,-DSP" />
        <!-- CONCAT_WS: 带分隔符拼接，参数: 分隔符, 值1, 值2, ... (注意: 第一个参数是分隔符，字段值作为后续参数之一) -->
        <field name="userEmailPair" column="user_name" function="fn:CONCAT_WS,|,邮箱前缀" />
        <!-- SUBSTRING: 截取子串，参数: 原始字符串, 起始位置, 结束位置(可选) -->
        <field name="phonePrefix" column="phone" function="fn:SUBSTRING,0,3" />
        <!-- REPLACE: 字符串替换，参数: 原始字符串, 目标, 替换值 -->
        <field name="emailMasked" column="email" function="fn:REPLACE,@,***@" />
        <!-- PAD_LEFT: 左补齐到指定长度，参数: 原始字符串, 目标长度, 补齐字符 -->
        <field name="userIdPadded" column="id" function="fn:PAD_LEFT,8,0" />
        <!-- PAD_RIGHT: 右补齐到指定长度，参数: 原始字符串, 目标长度, 补齐字符 -->
        <field name="userNamePadded" column="user_name" function="fn:PAD_RIGHT,10,." />
        <!-- LENGTH: 字符串长度 -->
        <field name="nameLength" column="user_name" function="fn:LENGTH" />
        <!-- LIKE_MATCH: SQL LIKE模式匹配(返回布尔)，参数: 原始字符串, LIKE模式(%和_通配) -->
        <field name="emailMatched" column="email" function="fn:LIKE_MATCH,%@%.com" />
        <!-- REGEX_MATCH: 正则表达式匹配(返回布尔)，参数: 原始字符串, 正则表达式 -->
        <field name="phoneValid" column="phone" function="fn:REGEX_MATCH,1[3-9]\d{9}" />

        <!-- ====== 类型转换函数 (1个) ====== -->
        <!-- TYPE_CONVERT: 转换为指定类型，参数: 原始值, 目标类型(STRING/INTEGER/LONG/DOUBLE) -->
        <field name="amountDouble" column="amount" function="fn:TYPE_CONVERT,DOUBLE" />
        <field name="idLong" column="id" function="fn:TYPE_CONVERT,LONG" />

        <!-- ====== 数学函数 (3个) ====== -->
        <!-- ROUND: 四舍五入，参数: 数值, 小数位数(可选,默认0) -->
        <field name="amountRounded" column="amount" function="fn:ROUND,2" />
        <!-- CEIL: 向上取整 -->
        <field name="amountCeil" column="amount" function="fn:CEIL" />
        <!-- FLOOR: 向下取整 -->
        <field name="amountFloor" column="amount" function="fn:FLOOR" />

        <!-- ====== 空值处理函数 (2个) ====== -->
        <!-- IFNULL: 空值替换，参数: 原始值, 替换值 -->
        <field name="remarkOrDefault" column="remark" function="fn:IFNULL,暂无备注" />
        <!-- NVL: 返回第一个非空值，参数: 值1, 值2, ... (字段值+后续参数中取第一个非null) -->
        <field name="contactInfo" column="phone" function="fn:NVL,联系方式未知" />

        <!-- ====== 条件函数 (1个) ====== -->
        <!-- IFF: 条件判断，参数: 布尔条件, 真值, 假值
             注意: 第一个参数是字段值，当字段值为"true"字符串时返回第二个参数，否则返回第三个参数
             适用于status等布尔型字段 -->
        <field name="statusText" column="status" function="fn:IFF,活跃,停用" />

        <!-- ====== JSON函数 (1个) ====== -->
        <!-- JSON_EXTRACT: 从JSON字符串中按路径提取值，参数: JSON字符串, 路径 -->
        <field name="extTag" column="json_ext" function="fn:JSON_EXTRACT,tag" />

        <!-- ====== 聚合函数 (5个) ====== -->
        <!-- SUM: 求和，参数: 数值1, 数值2, ... (字段值+后续参数)
             注意: 聚合函数在单行映射中不常用，多用于responseData层面对多个字段聚合 -->
        <field name="amountSum" column="amount" function="fn:SUM,0" />
        <!-- AVG: 求平均值，参数: 数值1, 数值2, ... -->
        <field name="amountAvg" column="amount" function="fn:AVG,0" />
        <!-- COUNT: 统计非空值个数，参数: 值1, 值2, ... -->
        <field name="validCount" column="amount" function="fn:COUNT,extra" />
        <!-- MAX: 取最大值，参数: 数值1, 数值2, ... -->
        <field name="amountMax" column="amount" function="fn:MAX,0" />
        <!-- MIN: 取最小值，参数: 数值1, 数值2, ... -->
        <field name="amountMin" column="amount" function="fn:MIN,999999" />

    </resultMap>

    <responseData resultMap="transformMap">
        <field name="detail" as="map" />
    </responseData>

</interface>', 1, 3, 'system'),

('CROSS_DB_REPORT', '示例系统', '跨库报表查询', '<?xml version="1.0" encoding="UTF-8"?>
<!--
  模板12: 多数据源SQL查询
  场景: 同一接口中查询不同数据源（如MySQL + Doris + PostgreSQL）
  适用: 跨库数据聚合、OLTP + OLAP 混合查询

  要点:
    - 支持的SQL类型: mysql, doris, sql, oracle, postgresql
    - 不同query通过 datasource 属性指定不同的数据源
    - 无 depends 的查询自动并行执行
-->
<interface transno="CROSS_DB_REPORT" name="跨库报表查询" description="同时查询MySQL业务库和Doris分析库，聚合生成报表">

    <requestData>
        <param name="merchantId" type="String" required="true" description="商户ID" />
        <param name="startDate" type="String" required="false" description="开始日期" />
        <param name="endDate" type="String" required="false" description="结束日期" />
    </requestData>

    <!-- 引用已配置的各数据源（管理后台数据源管理页面配置） -->
    <datasource name="ds_mysql" />
    <datasource name="ds_doris" />
    <datasource name="ds_pg" />

    <!-- 查询1: 从MySQL查商户基本信息（无依赖，并行执行） -->
    <query id="q1_merchant" type="mysql" datasource="ds_mysql">
        SELECT id, merchant_name, contact_name, phone, status, created_at
        FROM merchants
        WHERE id = #{$requestData[''merchantId'']}
    </query>

    <!-- 查询2: 从Doris查交易统计（无依赖，并行执行） -->
    <query id="q2_trade_stats" type="doris" datasource="ds_doris">
        SELECT merchant_id,
               COUNT(*) AS trade_count,
               SUM(amount) AS total_amount,
               AVG(amount) AS avg_amount
        FROM trade_fact
        WHERE merchant_id = #{$requestData[''merchantId'']}
        <if test="$requestData[''startDate''] != null">AND trade_date &gt;= #{$requestData[''startDate'']}</if>
        <if test="$requestData[''endDate''] != null">AND trade_date &lt;= #{$requestData[''endDate'']}</if>
        GROUP BY merchant_id
    </query>

    <!-- 查询3: 从PostgreSQL查配置信息（无依赖，并行执行） -->
    <query id="q3_config" type="postgresql" datasource="ds_pg">
        SELECT config_key, config_value
        FROM merchant_config
        WHERE merchant_id = #{$requestData[''merchantId'']}
    </query>

    <resultMap id="merchantMap" query="q1_merchant">
        <field name="merchantId" column="id" />
        <field name="merchantName" column="merchant_name" />
        <field name="contactName" column="contact_name" />
        <field name="phone" column="phone" />
        <field name="status" column="status" />
        <field name="createdAt" column="created_at" function="fn:DATE_FORMAT,yyyy-MM-dd" />
    </resultMap>

    <resultMap id="tradeStatsMap" query="q2_trade_stats">
        <field name="tradeCount" column="trade_count" function="fn:TYPE_CONVERT,LONG" />
        <field name="totalAmount" column="total_amount" function="fn:ROUND,2" />
        <field name="avgAmount" column="avg_amount" function="fn:ROUND,2" />
    </resultMap>

    <resultMap id="configMap" query="q3_config">
        <field name="configKey" column="config_key" />
        <field name="configValue" column="config_value" />
    </resultMap>

    <!-- 汇总响应: mapTo 引用各 resultMap 的 id -->
    <responseData>
        <field name="merchant" mapTo="merchantMap" as="map" />
        <field name="tradeStats" mapTo="tradeStatsMap" as="map" />
        <field name="configs" mapTo="configMap" as="list" />
    </responseData>

</interface>', 1, 3, 'system'),

('USER_WITH_EXT_INFO', '示例系统', '用户信息聚合查询', '<?xml version="1.0" encoding="UTF-8"?>
<!--
  模板13: SQL + HTTP 混合查询（依赖编排）
  场景: 先从数据库查基础数据，再用查到的数据调用外部HTTP接口补充信息
  适用: 内部数据 + 外部接口聚合场景

  要点:
    - q2 依赖 q1（depends="q1_local"），q1 完成后才执行 q2
    - q1 单行结果通过 #{$q1_local[''fieldName'']} 引用，引擎将单行结果存储为 Map
    - HTTP查询中用 #{$q1_local[''fieldName'']} 引用依赖查询结果（注意$前缀）
-->
<interface transno="USER_WITH_EXT_INFO" name="用户信息聚合查询" description="先查本地用户数据，再调外部接口补充扩展信息">

    <requestData>
        <param name="userId" type="String" required="true" description="用户ID" />
        <param name="token" type="String" required="true" description="外部接口令牌" />
    </requestData>

    <!-- 引用已配置的数据源 -->
    <datasource name="ds_main" />

    <!-- 第一步: 查本地用户数据 -->
    <query id="q1_local" type="mysql" datasource="ds_main">
        SELECT id, user_name, email, phone, ext_code, created_at
        FROM users
        WHERE id = #{$requestData[''userId'']}
    </query>

    <!-- 第二步: 用q1查到的ext_code调用外部接口（依赖q1） -->
    <query id="q2_external" type="http" depends="q1_local">
        <http url="https://external-api.com/user/profile/#{$q1_local[''ext_code'']}"
              method="GET"
              headers=''{"Authorization":"Bearer #{$requestData[''token'']}"}''
              responsePath="data" />
    </query>

    <resultMap id="localMap" query="q1_local">
        <field name="userId" column="id" />
        <field name="userName" column="user_name" />
        <field name="email" column="email" />
        <field name="phone" column="phone" />
        <field name="extCode" column="ext_code" />
    </resultMap>

    <resultMap id="extMap" query="q2_external">
        <field name="creditScore" column="creditScore" function="fn:TYPE_CONVERT,INTEGER" />
        <field name="memberLevel" column="memberLevel" function="fn:IFNULL,0" />
        <field name="lastLoginTime" column="lastLoginTime" />
    </resultMap>

    <!-- 组装响应: mapTo 引用各 resultMap 的 id -->
    <responseData>
        <field name="localInfo" mapTo="localMap" as="map" />
        <field name="externalInfo" mapTo="extMap" as="map" />
    </responseData>

</interface>', 1, 3, 'system'),

('LOAN_RISK_ASSESS', '示例系统', '贷款风险评估', '<?xml version="1.0" encoding="UTF-8"?>
<!--
  模板14: SQL + Dubbo 混合查询
  场景: 先查数据库获取参数，再用参数调用Dubbo服务
  适用: 本地数据 + 内部微服务聚合场景

  要点:
    - SQL查询和Dubbo查询可使用不同的数据源
    - Dubbo参数值用 #{$q1_loan[''fieldName'']} 引用依赖查询结果
    - Dubbo参数类型支持: String, Integer/Int, Long/Long, Double/double, Float/float, Boolean/boolean
-->
<interface transno="LOAN_RISK_ASSESS" name="贷款风险评估" description="查库获取用户贷款信息，再调Dubbo风控服务评估风险">

    <requestData>
        <param name="loanId" type="String" required="true" description="贷款申请ID" />
    </requestData>

    <!-- 引用已配置的数据源 -->
    <datasource name="ds_main" />
    <datasource name="dubbo_ds" />

    <!-- 第一步: 查贷款申请信息 -->
    <query id="q1_loan" type="mysql" datasource="ds_main">
        SELECT id, applicant_id, amount, term_months, product_code, apply_time
        FROM loan_application
        WHERE id = #{$requestData[''loanId'']}
    </query>

    <!-- 第二步: 用q1的结果调Dubbo风控服务（依赖q1） -->
    <query id="q2_risk" type="dubbo" datasource="dubbo_ds" depends="q1_loan">
        <dubbo service="com.fintechervision.risk.RiskAssessService"
               method="assess"
               version="1.0.0"
               timeout="5000">
            <param type="String" value="#{$q1_loan[''applicant_id'']}" />
            <param type="String" value="#{$q1_loan[''product_code'']}" />
            <param type="Double" value="#{$q1_loan[''amount'']}" />
        </dubbo>
    </query>

    <resultMap id="loanMap" query="q1_loan">
        <field name="loanId" column="id" />
        <field name="applicantId" column="applicant_id" />
        <field name="amount" column="amount" function="fn:ROUND,2" />
        <field name="termMonths" column="term_months" function="fn:TYPE_CONVERT,INTEGER" />
        <field name="productCode" column="product_code" />
        <field name="applyTime" column="apply_time" function="fn:DATE_FORMAT,yyyy-MM-dd HH:mm:ss" />
    </resultMap>

    <resultMap id="riskMap" query="q2_risk">
        <field name="riskLevel" column="riskLevel" />
        <field name="riskScore" column="riskScore" function="fn:TYPE_CONVERT,INTEGER" />
        <field name="suggestion" column="suggestion" function="fn:IFNULL,需人工审核" />
        <field name="rejectReason" column="rejectReason" function="fn:IFNULL,无" />
    </resultMap>

    <!-- 组装响应: mapTo 引用各 resultMap 的 id -->
    <responseData>
        <field name="loanInfo" mapTo="loanMap" as="map" />
        <field name="riskResult" mapTo="riskMap" as="map" />
    </responseData>

</interface>', 1, 3, 'system'),

('DICT_QUERY', '示例系统', '字典查询', '<?xml version="1.0" encoding="UTF-8"?>
<!--
  模板15: 无结果映射的简单查询
  场景: 不需要字段映射，直接返回查询原始结果
  适用: 简单字典查询、配置查询等不需要转换字段的场景
  说明: 无 resultMap 时，引擎将查询结果以 queryId 为key放入响应中
       单行结果返回 Map，多行结果返回 List
       responseData 中通过 mapTo 引用 queryId 获取原始结果
-->
<interface transno="DICT_QUERY" name="字典查询" description="查询数据字典，直接返回原始结果">

    <requestData>
        <param name="dictType" type="String" required="true" description="字典类型编码" />
    </requestData>

    <!-- 引用已配置的数据源 -->
    <datasource name="ds_main" />

    <query id="q1" type="mysql" datasource="ds_main">
        SELECT dict_code, dict_label, dict_value, sort_order, remark
        FROM sys_dict
        WHERE dict_type = #{$requestData[''dictType'']}
        ORDER BY sort_order
    </query>

    <!-- 不定义 resultMap，引擎将原始查询结果以 queryId 为key放入 mappedResults -->
    <!-- 通过 responseData 的 mapTo 引用 queryId 获取原始结果 -->
    <responseData>
        <field name="list" mapTo="q1" as="list" />
    </responseData>

</interface>', 1, 3, 'system'),

('TRADE_DETAIL_AGGREGATE', '示例系统', '交易详情聚合查询', '<?xml version="1.0" encoding="UTF-8"?>
<!--
  模板16: 综合全功能示例
  场景: 综合使用所有引擎功能 — 多数据源、多查询类型、依赖编排、动态SQL、
        分页、函数转换、结果映射、响应组装
  适用: 复杂业务聚合接口的参考模板

  业务场景: 交易详情聚合查询
  编排流程 (DAG):
    q1_trades(MySQL+动态SQL+游标分页) ──┬──> q2_risk(HTTP)
                                         └──> q3_customer(Dubbo)
    q4_logs(MongoDB) ── 并行，无依赖

  引擎特性覆盖:
    ✅ 多数据源 (MySQL + Dubbo + MongoDB)
    ✅ 依赖编排 (depends + CompletableFuture DAG)
    ✅ 动态SQL (<if> 条件)
    ✅ 游标分页 (pagination="cursor")
    ✅ HTTP/Dubbo/MongoDB 查询
    ✅ 函数转换 (DATE_FORMAT, ROUND, IFNULL, IFF, TYPE_CONVERT)
    ✅ 结果映射 + 响应组装 (mapTo引用)
-->
<interface transno="TRADE_DETAIL_AGGREGATE" name="交易详情聚合查询" description="综合查询交易详情，聚合多数据源信息">

    <!-- ====== 请求参数 ====== -->
    <requestData>
        <param name="tradeId" type="String" required="true" description="交易ID" />
        <param name="status" type="String" required="false" description="交易状态" />
        <param name="productType" type="String" required="false" description="产品类型" />
        <param name="token" type="String" required="true" description="认证令牌" />
        <param name="lastId" type="String" required="false" description="分页游标（上一页最后ID）" />
        <param name="pageSize" type="Integer" required="false" defaultValue="20" description="每页条数" />
    </requestData>

    <!-- ====== 数据源配置（引用管理后台已配置的数据源） ====== -->
    <datasource name="ds_mysql" />
    <datasource name="dubbo_ds" />
    <datasource name="ds_mongo" />

    <!-- ====== 查询定义 ====== -->

    <!-- 查询1: MySQL查询交易列表（带动态SQL + 游标分页，无依赖，并行执行） -->
    <query id="q1_trades" type="mysql" datasource="ds_mysql"
           pagination="cursor" order-by="id"
           page-size-param="pageSize" last-id-param="lastId"
           default-page-size="20" max-page-size="100">
        SELECT id, trade_no, customer_id, product_type, amount, status, channel, created_at
        FROM trades
        WHERE 1=1
        <if test="$requestData[''status''] != null">AND status = #{$requestData[''status'']}</if>
        <if test="$requestData[''productType''] != null">AND product_type = #{$requestData[''productType'']}</if>
        ORDER BY created_at DESC
    </query>

    <!-- 查询4: MongoDB查询操作日志（无依赖，与q1并行） -->
    <query id="q4_logs" type="mongo" datasource="ds_mongo">
        <mongo collection="trade_operation_logs">
            <filter>{"tradeId": "#{tradeId}"}</filter>
            <projection>{"operator":1, "action":1, "detail":1, "time":1}</projection>
            <sort>{"time": -1}</sort>
            <limit>20</limit>
        </mongo>
    </query>

    <!-- 查询2: HTTP查询风控信息（依赖q1，用q1单行结果的字段） -->
    <query id="q2_risk" type="http" depends="q1_trades">
        <http url="https://risk.internal.com/api/v1/trade/risk/#{$q1_trades[''trade_no'']}"
              method="GET"
              headers=''{"Authorization":"Bearer #{$requestData[''token'']}"}''
              responsePath="data" />
    </query>

    <!-- 查询3: Dubbo查询客户信息（依赖q1，用q1单行结果的字段） -->
    <query id="q3_customer" type="dubbo" datasource="dubbo_ds" depends="q1_trades">
        <dubbo service="com.fintechervision.customer.CustomerService"
               method="getCustomerDetail"
               version="1.0.0"
               timeout="3000">
            <param type="String" value="#{$q1_trades[''customer_id'']}" />
        </dubbo>
    </query>

    <!-- ====== 结果映射 ====== -->

    <resultMap id="tradeMap" query="q1_trades">
        <field name="tradeId" column="id" />
        <field name="tradeNo" column="trade_no" />
        <field name="customerId" column="customer_id" />
        <field name="productType" column="product_type" />
        <field name="amount" column="amount" function="fn:ROUND,2" />
        <field name="status" column="status" />
        <field name="channel" column="channel" />
        <field name="createdAt" column="created_at" function="fn:DATE_FORMAT,yyyy-MM-dd HH:mm:ss" />
    </resultMap>

    <resultMap id="riskMap" query="q2_risk">
        <field name="riskLevel" column="riskLevel" function="fn:IFNULL,UNKNOWN" />
        <field name="riskScore" column="riskScore" function="fn:TYPE_CONVERT,INTEGER" />
        <!-- IFF: 条件函数，第一个参数是字段值(布尔型)，当字段值为"true"时返回第二个参数，否则返回第三个参数 -->
        <field name="frozenText" column="frozen" function="fn:IFF,已冻结,未冻结" />
    </resultMap>

    <resultMap id="customerMap" query="q3_customer">
        <field name="customerId" column="customerId" />
        <field name="customerName" column="customerName" />
        <field name="certType" column="certType" />
        <field name="certNo" column="certNo" />
        <field name="level" column="level" function="fn:IFNULL,普通客户" />
    </resultMap>

    <resultMap id="logMap" query="q4_logs">
        <field name="operator" column="operator" />
        <field name="action" column="action" />
        <field name="detail" column="detail" />
        <field name="time" column="time" />
    </resultMap>

    <!-- ====== 响应组装 ====== -->
    <responseData>
        <field name="trade" mapTo="tradeMap" as="map" />
        <field name="riskInfo" mapTo="riskMap" as="map" />
        <field name="customerInfo" mapTo="customerMap" as="map" />
        <field name="operationLogs" mapTo="logMap" as="list" />
    </responseData>

</interface>', 1, 3, 'system');


-- ============================================================
-- 12. 测试数据源配置（对应模板中引用的 datasource name）
-- ============================================================

INSERT INTO datasource_config (ds_name, ds_type, jdbc_url, username, password, extra_config, status, created_by) VALUES
-- MySQL 主库（模板01-06, 09-11, 13, 15 使用）
('ds_main', 'MYSQL', 'jdbc:mysql://localhost:3306/dsp_test?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai', 'root', 'root', NULL, 1, 'system'),

-- MySQL 业务库（模板12, 16 使用）
('ds_mysql', 'MYSQL', 'jdbc:mysql://localhost:3306/dsp_biz?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai', 'root', 'root', NULL, 1, 'system'),

-- Doris 分析库（模板12 使用）
('ds_doris', 'DORIS', 'jdbc:mysql://localhost:9030/dsp_analytics?useUnicode=true&characterEncoding=utf8&useSSL=false', 'root', 'root', NULL, 1, 'system'),

-- PostgreSQL（模板12 使用）
('ds_pg', 'POSTGRESQL', 'jdbc:postgresql://localhost:5432/dsp_config_db', 'postgres', 'postgres', NULL, 1, 'system'),

-- MongoDB（模板08, 16 使用）
('mongo_main', 'MONGODB', NULL, NULL, NULL, '{"url":"mongodb://localhost:27017/dsp_log"}', 1, 'system'),

-- Dubbo 数据源（模板07, 14, 16 使用）
('dubbo_ds', 'DUBBO', NULL, NULL, NULL, '{"registry":"nacos://localhost:8848"}', 1, 'system');

-- ============================================================
-- 13. 接口基础信息初始数据（对应模板的 interface_info 记录）
-- ============================================================

INSERT INTO interface_info (transno, name, system_name, status, description, current_version, created_by, updated_by) VALUES
('USER_GET_BY_ID',         '根据ID查询用户',       '示例系统', 3, '根据用户ID查询用户详细信息',                          1, 'system', 'system'),
('USER_LIST_QUERY',        '用户列表查询',         '示例系统', 3, '支持多条件筛选的用户列表查询',                        1, 'system', 'system'),
('ORDER_LIST_CURSOR',      '订单游标分页查询',      '示例系统', 3, '基于游标的订单列表分页查询',                          1, 'system', 'system'),
('REPORT_LIST_OPTIMIZED',  '报表优化分页查询',      '示例系统', 3, '使用子查询优化的深分页报表查询',                      1, 'system', 'system'),
('WEATHER_QUERY',          '天气查询',             '示例系统', 3, '调用外部天气API查询城市天气信息',                      1, 'system', 'system'),
('RISK_CHECK',             '风控检查',             '示例系统', 3, '调用风控系统POST接口进行风险评估',                     1, 'system', 'system'),
('CUSTOMER_PROFILE',       '客户画像查询',         '示例系统', 3, '通过Dubbo调用客户服务获取客户画像信息',                1, 'system', 'system'),
('USER_LOG_QUERY',         '用户操作日志查询',      '示例系统', 3, '从MongoDB查询用户操作日志',                           1, 'system', 'system'),
('DASHBOARD_OVERVIEW',     '仪表盘概览',           '示例系统', 3, '并行查询多个数据源聚合仪表盘数据',                     1, 'system', 'system'),
('USER_DETAIL_AGGREGATE',  '用户详情聚合',         '示例系统', 3, '先查用户基本信息，再根据结果查询关联数据',              1, 'system', 'system'),
('DATA_TRANSFORM_DEMO',    '数据转换演示',         '示例系统', 3, '演示所有内置函数的结果映射转换',                       1, 'system', 'system'),
('CROSS_DB_REPORT',        '跨库报表查询',         '示例系统', 3, '同时查询MySQL业务库和Doris分析库，聚合生成报表',       1, 'system', 'system'),
('USER_WITH_EXT_INFO',     '用户信息聚合查询',      '示例系统', 3, '先查本地用户数据，再调外部接口补充扩展信息',           1, 'system', 'system'),
('LOAN_RISK_ASSESS',       '贷款风险评估',         '示例系统', 3, '查库获取用户贷款信息，再调Dubbo风控服务评估风险',      1, 'system', 'system'),
('DICT_QUERY',             '字典查询',             '示例系统', 3, '查询数据字典，直接返回原始结果',                       1, 'system', 'system'),
('TRADE_DETAIL_AGGREGATE', '交易详情聚合查询',      '示例系统', 3, '综合查询交易详情，聚合多数据源信息',                   1, 'system', 'system');
