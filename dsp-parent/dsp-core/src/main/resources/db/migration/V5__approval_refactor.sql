-- V5 审批模块重构 - 新建审批信息表、审批流程表、接口申请关系表

CREATE TABLE IF NOT EXISTS approval_info (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    approval_no       VARCHAR(64)  NOT NULL UNIQUE COMMENT '审批单号',
    type              TINYINT      NOT NULL COMMENT '类型：1-新增接口 2-修改接口 3-申请接口',
    title             VARCHAR(256) NOT NULL COMMENT '审批标题',
    status            TINYINT      DEFAULT 0 COMMENT '0-待审批 1-已通过 2-已驳回 3-已撤回',
    applicant         VARCHAR(64)  NOT NULL COMMENT '申请人用户名',
    applicant_name    VARCHAR(64)  COMMENT '申请人姓名',
    applicant_dept_id BIGINT       COMMENT '申请人部门ID',
    apply_time        DATETIME     NOT NULL COMMENT '申请时间',
    withdraw_time     DATETIME     COMMENT '撤回时间',
    transno           VARCHAR(64)  COMMENT '接口编码',
    version_no        INT          COMMENT '版本号',
    applicant_system_id BIGINT     COMMENT '申请方系统ID',
    provider_system_id  BIGINT     COMMENT '服务方系统ID',
    requirement_no    VARCHAR(128) COMMENT '需求编号',
    requirement_desc  VARCHAR(512) COMMENT '需求描述',
    apply_reason      VARCHAR(512) COMMENT '申请原因',
    downstream_info   VARCHAR(512) COMMENT '下游接口/页面',
    created_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_applicant (applicant),
    KEY idx_transno (transno),
    KEY idx_status_time (status, apply_time)
) ENGINE=InnoDB COMMENT='审批信息表';

CREATE TABLE IF NOT EXISTS approval_flow (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    approval_id     BIGINT       NOT NULL COMMENT '审批信息ID',
    step_no         TINYINT      NOT NULL COMMENT '步骤序号',
    step_name       VARCHAR(64)  NOT NULL COMMENT '步骤名称',
    status          TINYINT      DEFAULT 0 COMMENT '0-待审批 1-已通过 2-已驳回',
    dept_id         BIGINT       COMMENT '审批部门ID',
    approver        VARCHAR(64)  COMMENT '审批人用户名',
    approver_name   VARCHAR(64)  COMMENT '审批人姓名',
    approve_time    DATETIME     COMMENT '审批时间',
    reject_reason   VARCHAR(512) COMMENT '驳回原因',
    created_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_approval_id (approval_id),
    UNIQUE KEY uk_approval_step (approval_id, step_no)
) ENGINE=InnoDB COMMENT='审批流程表';

CREATE TABLE IF NOT EXISTS interface_relation (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    transno             VARCHAR(64)  NOT NULL COMMENT '接口编码',
    provider_system_id  BIGINT       NOT NULL COMMENT '服务方系统ID',
    applicant_system_id BIGINT       NOT NULL COMMENT '申请方系统ID',
    approval_id         BIGINT       COMMENT '关联审批ID',
    status              TINYINT      DEFAULT 1 COMMENT '1-生效 2-已下线',
    apply_time          DATETIME     COMMENT '申请时间',
    requirement_no      VARCHAR(128) COMMENT '需求编号',
    apply_reason        VARCHAR(512) COMMENT '申请原因',
    offline_time        DATETIME     COMMENT '下线时间',
    offline_reason      VARCHAR(256) COMMENT '下线原因',
    created_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_transno (transno),
    KEY idx_provider (provider_system_id),
    KEY idx_applicant (applicant_system_id)
) ENGINE=InnoDB COMMENT='接口申请关系表';
