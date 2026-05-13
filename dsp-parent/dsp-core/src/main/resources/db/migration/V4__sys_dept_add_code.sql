-- 部门表增加部门编码唯一键
ALTER TABLE sys_dept ADD COLUMN code VARCHAR(64) COMMENT '部门编码' AFTER name;
ALTER TABLE sys_dept ADD UNIQUE KEY uk_code (code);
