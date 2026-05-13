-- interface_info: system_id → system_code
ALTER TABLE interface_info ADD COLUMN system_code VARCHAR(64) DEFAULT NULL COMMENT '所属系统编码' AFTER system_name;
ALTER TABLE interface_info DROP COLUMN system_id;

-- interface_template: system_id → system_code
ALTER TABLE interface_template ADD COLUMN system_code VARCHAR(64) DEFAULT NULL COMMENT '所属系统编码' AFTER system_name;
ALTER TABLE interface_template DROP COLUMN system_id;
