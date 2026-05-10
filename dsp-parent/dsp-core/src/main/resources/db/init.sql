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
