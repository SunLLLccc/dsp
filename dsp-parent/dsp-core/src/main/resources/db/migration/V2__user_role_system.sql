-- ============================================================
-- V2 用户角色体系 - 数据库迁移脚本
-- ============================================================

-- -----------------------------------------------------------
-- 11. 部门表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_dept (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(100)    NOT NULL COMMENT '部门名称',
    parent_id       BIGINT          NOT NULL DEFAULT 0 COMMENT '上级部门ID，0为顶级',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '排序',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='部门表';

-- -----------------------------------------------------------
-- 12. 角色表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_code       VARCHAR(50)     NOT NULL COMMENT '角色编码',
    role_name       VARCHAR(50)     NOT NULL COMMENT '角色名称',
    description     VARCHAR(200)    DEFAULT NULL COMMENT '说明',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB COMMENT='角色表';

-- -----------------------------------------------------------
-- 13. 用户表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    username        VARCHAR(50)     NOT NULL COMMENT '登录名',
    password        VARCHAR(100)    NOT NULL COMMENT '密码（BCrypt加密）',
    real_name       VARCHAR(50)     DEFAULT NULL COMMENT '真实姓名',
    dept_id         BIGINT          DEFAULT NULL COMMENT '所属部门ID',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB COMMENT='用户表';

-- -----------------------------------------------------------
-- 14. 用户角色关联表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_role (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id         BIGINT          NOT NULL COMMENT '用户ID',
    role_id         BIGINT          NOT NULL COMMENT '角色ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB COMMENT='用户角色关联表';

-- -----------------------------------------------------------
-- 初始数据
-- -----------------------------------------------------------

-- 默认部门
INSERT INTO sys_dept (id, name, parent_id, sort_order) VALUES (1, '总部门', 0, 0);

-- 初始角色
INSERT INTO sys_role (id, role_code, role_name, description) VALUES
(1, 'ADMIN',        '系统管理员', '拥有全部权限'),
(2, 'DEPT_MANAGER', '部门经理',   '审批权限，管理本部门用户'),
(3, 'USER',         '普通用户',   '可创建和编辑接口'),
(4, 'VIEWER',       '只读用户',   '只能查看，不能修改'),
(5, 'IMPORTER',     '导入用户',   '可导入配置');

-- 默认管理员（密码 admin123，BCrypt加密）
INSERT INTO sys_user (id, username, password, real_name, dept_id, status) VALUES
(1, 'admin', '$2b$10$QOx458ZaaHJtsqLr7qZdA.e2caAjWU3THYpHESshit1ws1bc/tWYm', '管理员', 1, 1);

-- admin 绑定 ADMIN 角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
