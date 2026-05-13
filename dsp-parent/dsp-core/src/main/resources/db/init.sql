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

INSERT INTO interface_template (transno, system_name, interface_name, xml_content, version_no, status, created_by) VALUES
('USER_GET_BY_ID', '示例系统', '根据ID查询用户', '<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!--\n  模板01: 简单单表SQL查询\n  场景: 最基础的查询，单数据源、单SQL、无动态条件、无分页、简单结果映射\n  适用: 根据ID查详情、字典查询等简单场景\n\n  要点:\n    - datasource: 引用管理后台已配置的数据源（通过数据源管理页面配置）\n    - resultMap: 数据库字段→输出字段映射，支持 function 函数转换\n    - responseData: resultMap 属性指定默认结果映射，field name 为输出键名\n-->\n<interface transno=\"USER_GET_BY_ID\" name=\"根据ID查询用户\" description=\"根据用户ID查询用户详细信息\">\n\n    <!-- 请求参数定义 -->\n    <requestData>\n        <param name=\"userId\" type=\"String\" required=\"true\" description=\"用户ID\" />\n    </requestData>\n\n    <!-- 引用已配置的数据源（通过管理后台数据源管理页面添加，dsName 为唯一键） -->\n    <datasource name=\"ds_main\" />\n\n    <!-- SQL查询: 使用 #{$requestData['paramName']} 引用请求参数 -->\n    <query id=\"q1\" type=\"mysql\" datasource=\"ds_main\">\n        SELECT id, user_name, email, phone, status, created_at, updated_at\n        FROM users\n        WHERE id = #{$requestData['userId']}\n    </query>\n\n    <!-- 结果映射: column=数据库字段, name=输出字段, function=fn:函数名,参数 -->\n    <resultMap id=\"userMap\" query=\"q1\">\n        <field name=\"userId\" column=\"id\" />\n        <field name=\"userName\" column=\"user_name\" />\n        <field name=\"email\" column=\"email\" />\n        <field name=\"phone\" column=\"phone\" />\n        <field name=\"status\" column=\"status\" />\n        <field name=\"createdAt\" column=\"created_at\" function=\"fn:DATE_FORMAT,yyyy-MM-dd HH:mm:ss\" />\n        <field name=\"updatedAt\" column=\"updated_at\" function=\"fn:DATE_FORMAT,yyyy-MM-dd HH:mm:ss\" />\n    </resultMap>\n\n    <!-- 响应组装: resultMap 指定默认结果映射, field name 作为输出键 -->\n    <responseData resultMap=\"userMap\">\n        <field name=\"user\" as=\"map\" />\n    </responseData>\n\n</interface>', 1, 3, 'system');


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
