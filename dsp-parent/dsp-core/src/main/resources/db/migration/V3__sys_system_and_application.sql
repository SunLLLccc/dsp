-- ============================================================
-- V3 系统管理 + 接口申请流程 - 数据库迁移脚本
-- ============================================================

-- -----------------------------------------------------------
-- 1. 系统表（部门下的业务系统）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_system (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(128)    NOT NULL COMMENT '系统名称',
    code            VARCHAR(64)     DEFAULT NULL COMMENT '系统编码',
    dept_id         BIGINT          NOT NULL COMMENT '所属部门ID',
    description     VARCHAR(256)    DEFAULT NULL COMMENT '说明',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB COMMENT='系统表';

-- -----------------------------------------------------------
-- 2. 接口表增加 system_id 字段
-- -----------------------------------------------------------
ALTER TABLE interface_info ADD COLUMN system_id BIGINT DEFAULT NULL COMMENT '所属系统ID' AFTER system_name;
ALTER TABLE interface_template ADD COLUMN system_id BIGINT DEFAULT NULL COMMENT '所属系统ID' AFTER system_name;

-- -----------------------------------------------------------
-- 3. 审批记录表扩展（支持接口申请流程）
-- -----------------------------------------------------------
ALTER TABLE approval_record ADD COLUMN type TINYINT NOT NULL DEFAULT 0 COMMENT '类型：0-版本审批 1-接口申请' AFTER transno;
ALTER TABLE approval_record ADD COLUMN applicant_dept_id BIGINT DEFAULT NULL COMMENT '申请方部门ID';
ALTER TABLE approval_record ADD COLUMN applicant_system_id BIGINT DEFAULT NULL COMMENT '申请方系统ID';
ALTER TABLE approval_record ADD COLUMN provider_system_id BIGINT DEFAULT NULL COMMENT '服务方系统ID';
ALTER TABLE approval_record ADD COLUMN requirement_no VARCHAR(128) DEFAULT NULL COMMENT '需求编号';
ALTER TABLE approval_record ADD COLUMN requirement_desc VARCHAR(512) DEFAULT NULL COMMENT '需求描述';
ALTER TABLE approval_record ADD COLUMN apply_reason VARCHAR(512) DEFAULT NULL COMMENT '申请原因';
ALTER TABLE approval_record ADD COLUMN downstream_info VARCHAR(512) DEFAULT NULL COMMENT '下游接口/页面';
ALTER TABLE approval_record ADD COLUMN current_step TINYINT DEFAULT 1 COMMENT '当前审批步骤：1-服务方部门经理 2-申请方部门经理';
ALTER TABLE approval_record ADD COLUMN approver2 VARCHAR(64) DEFAULT NULL COMMENT '第二步审批人';
ALTER TABLE approval_record ADD COLUMN approve_time2 DATETIME DEFAULT NULL COMMENT '第二步审批时间';
