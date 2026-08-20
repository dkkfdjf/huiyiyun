-- 慧医云 初始建表(Flyway V1)。
-- 库由连接串 createDatabaseIfNotExist=true 自动建;本脚本只建 18 张表。
-- 注:无外键约束(关联靠应用层 + 索引维护),故无 FOREIGN KEY。

-- 1. user
CREATE TABLE `user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` VARCHAR(50) NOT NULL COMMENT '登录名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码(BCrypt)',
  `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
  `role` TINYINT NOT NULL COMMENT '角色:0管理员/1公司/2机构管理员/3医师',
  `company_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '公司绑定(行级隔离)',
  `institution_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '机构绑定(行级隔离)',
  `phone` VARCHAR(20) DEFAULT NULL,
  `email` VARCHAR(100) DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用/1正常(登录唯一依据)',
  `login_fail_count` INT NOT NULL DEFAULT 0,
  `locked_until` DATETIME DEFAULT NULL,
  `last_login_time` DATETIME DEFAULT NULL,
  `create_by` VARCHAR(50) DEFAULT NULL,
  `update_by` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username_deleted` (`username`,`deleted`),
  KEY `idx_role_status` (`role`,`status`),
  KEY `idx_company` (`company_id`),
  KEY `idx_institution` (`institution_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- 2. pharma_company
CREATE TABLE `pharma_company` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '公司名称',
  `credit_code` VARCHAR(50) DEFAULT NULL COMMENT '统一社会信用代码',
  `license_no` VARCHAR(50) DEFAULT NULL,
  `contact_person` VARCHAR(50) DEFAULT NULL,
  `contact_phone` VARCHAR(20) DEFAULT NULL,
  `address` VARCHAR(200) DEFAULT NULL,
  `audit_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待审核/1正常/2驳回/3停用',
  `audit_remark` VARCHAR(500) DEFAULT NULL,
  `audit_time` DATETIME DEFAULT NULL,
  `create_by` VARCHAR(50) DEFAULT NULL,
  `update_by` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_credit_deleted` (`credit_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医药公司';

-- 3. medical_institution
CREATE TABLE `medical_institution` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `address` VARCHAR(200) NOT NULL,
  `city_id` BIGINT UNSIGNED NOT NULL,
  `longitude` DECIMAL(10,6) NOT NULL COMMENT 'GCJ-02',
  `latitude` DECIMAL(10,6) NOT NULL,
  `contact_person` VARCHAR(50) DEFAULT NULL,
  `contact_phone` VARCHAR(20) DEFAULT NULL,
  `audit_status` TINYINT NOT NULL DEFAULT 1 COMMENT '0待审核/1正常/2驳回/3停用(管理员录入默认正常)',
  `audit_remark` VARCHAR(500) DEFAULT NULL,
  `audit_time` DATETIME DEFAULT NULL,
  `create_by` VARCHAR(50) DEFAULT NULL,
  `update_by` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_city` (`city_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医疗机构';

-- 4. department
CREATE TABLE `department` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `institution_id` BIGINT UNSIGNED NOT NULL,
  `name` VARCHAR(50) NOT NULL,
  `sort` INT NOT NULL DEFAULT 0,
  `create_by` VARCHAR(50) DEFAULT NULL,
  `update_by` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inst_name_deleted` (`institution_id`,`name`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科室';

-- 5. doctor
CREATE TABLE `doctor` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '关联账号(1:1)',
  `name` VARCHAR(50) NOT NULL,
  `institution_id` BIGINT UNSIGNED NOT NULL,
  `department_id` BIGINT UNSIGNED DEFAULT NULL,
  `title` VARCHAR(50) DEFAULT NULL,
  `phone` VARCHAR(20) DEFAULT NULL,
  `email` VARCHAR(100) DEFAULT NULL,
  `create_by` VARCHAR(50) DEFAULT NULL,
  `update_by` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_deleted` (`user_id`,`deleted`),
  KEY `idx_inst_dept` (`institution_id`,`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医师';

-- 6. city
CREATE TABLE `city` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL,
  `province` VARCHAR(50) NOT NULL,
  `region_code` VARCHAR(20) DEFAULT NULL,
  `create_by` VARCHAR(50) DEFAULT NULL,
  `update_by` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='城市';

-- 7. drug
CREATE TABLE `drug` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `specification` VARCHAR(100) DEFAULT NULL,
  `dosage_form` VARCHAR(50) DEFAULT NULL,
  `company_id` BIGINT UNSIGNED NOT NULL,
  `approval_no` VARCHAR(50) DEFAULT NULL,
  `unit` VARCHAR(20) DEFAULT NULL,
  `producer` VARCHAR(100) DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0下架/1上架',
  `create_by` VARCHAR(50) DEFAULT NULL,
  `update_by` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_deleted` (`approval_no`,`deleted`),
  KEY `idx_company` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药品';

-- 8. sales_location
CREATE TABLE `sales_location` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `address` VARCHAR(200) NOT NULL,
  `city_id` BIGINT UNSIGNED NOT NULL,
  `company_id` BIGINT UNSIGNED NOT NULL,
  `longitude` DECIMAL(10,6) NOT NULL,
  `latitude` DECIMAL(10,6) NOT NULL,
  `contact_person` VARCHAR(50) DEFAULT NULL,
  `contact_phone` VARCHAR(20) DEFAULT NULL,
  `create_by` VARCHAR(50) DEFAULT NULL,
  `update_by` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_company_city` (`company_id`,`city_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售地点';

-- 9. drug_stock
CREATE TABLE `drug_stock` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `drug_id` BIGINT UNSIGNED NOT NULL,
  `location_id` BIGINT UNSIGNED NOT NULL,
  `company_id` BIGINT UNSIGNED NOT NULL COMMENT '冗余,便于隔离',
  `stock_qty` INT NOT NULL DEFAULT 0,
  `price` DECIMAL(10,2) NOT NULL,
  `threshold` INT NOT NULL DEFAULT 0,
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `create_by` VARCHAR(50) DEFAULT NULL,
  `update_by` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_drug_loc_deleted` (`drug_id`,`location_id`,`deleted`),
  KEY `idx_company_drug` (`company_id`,`drug_id`),
  KEY `idx_location` (`location_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存';

-- 10. sales_record (append-only)
CREATE TABLE `sales_record` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `record_no` VARCHAR(32) NOT NULL,
  `location_id` BIGINT UNSIGNED NOT NULL,
  `drug_id` BIGINT UNSIGNED NOT NULL,
  `company_id` BIGINT UNSIGNED NOT NULL,
  `qty` INT NOT NULL,
  `price` DECIMAL(10,2) NOT NULL,
  `amount` DECIMAL(12,2) NOT NULL,
  `sale_time` DATETIME NOT NULL,
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_by` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_no` (`record_no`),
  KEY `idx_loc_time` (`location_id`,`sale_time`),
  KEY `idx_drug_time` (`drug_id`,`sale_time`),
  KEY `idx_company_time` (`company_id`,`sale_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售流水';

-- 11. replenishment_order (append-only)
CREATE TABLE `replenishment_order` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no` VARCHAR(32) NOT NULL,
  `location_id` BIGINT UNSIGNED NOT NULL,
  `drug_id` BIGINT UNSIGNED NOT NULL,
  `company_id` BIGINT UNSIGNED NOT NULL,
  `qty` INT NOT NULL,
  `in_time` DATETIME NOT NULL,
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_by` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_loc_time` (`location_id`,`in_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补货流水';

-- 12. company_policy
CREATE TABLE `company_policy` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `company_id` BIGINT UNSIGNED NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `content` TEXT NOT NULL,
  `policy_type` TINYINT NOT NULL DEFAULT 1 COMMENT '1医保/2药企/3价格',
  `effective_date` DATE NOT NULL,
  `expire_date` DATE DEFAULT NULL,
  `create_by` VARCHAR(50) DEFAULT NULL,
  `update_by` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_company_type` (`company_id`,`policy_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公司政策';

-- 13. drug_demand
CREATE TABLE `drug_demand` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `doctor_id` BIGINT UNSIGNED NOT NULL,
  `institution_id` BIGINT UNSIGNED NOT NULL,
  `drug_name` VARCHAR(100) NOT NULL COMMENT '必填,可手填',
  `drug_id` BIGINT UNSIGNED DEFAULT NULL,
  `company_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '空→未关联池',
  `demand_type` TINYINT NOT NULL COMMENT '1临床用药需求/2临床用量反馈',
  `qty` INT NOT NULL,
  `urgency` TINYINT NOT NULL COMMENT '1一般/2紧急',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理/1处理中/2已满足/3已驳回/4已撤回',
  `remark` VARCHAR(500) DEFAULT NULL,
  `reply` VARCHAR(500) DEFAULT NULL,
  `handler_id` BIGINT UNSIGNED DEFAULT NULL,
  `handle_time` DATETIME DEFAULT NULL,
  `create_by` VARCHAR(50) DEFAULT NULL,
  `update_by` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_status_company` (`status`,`company_id`),
  KEY `idx_doctor` (`doctor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='临床用药反馈';

-- 14. essential_material
CREATE TABLE `essential_material` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `category` VARCHAR(50) NOT NULL,
  `specification` VARCHAR(100) DEFAULT NULL,
  `unit` VARCHAR(20) DEFAULT NULL,
  `content` TEXT DEFAULT NULL,
  `create_by` VARCHAR(50) DEFAULT NULL,
  `update_by` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='必备材料';

-- 15. login_log (append-only, 保留180天)
CREATE TABLE `login_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED DEFAULT NULL,
  `username` VARCHAR(50) NOT NULL,
  `login_time` DATETIME NOT NULL,
  `ip` VARCHAR(50) DEFAULT NULL,
  `user_agent` VARCHAR(255) DEFAULT NULL,
  `login_result` TINYINT NOT NULL COMMENT '0失败/1成功',
  `fail_reason` VARCHAR(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

-- 16. error_log (append-only, 保留30天)
CREATE TABLE `error_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `error_time` DATETIME NOT NULL,
  `user_id` BIGINT UNSIGNED DEFAULT NULL,
  `request_url` VARCHAR(500) DEFAULT NULL,
  `request_param` TEXT DEFAULT NULL,
  `error_type` VARCHAR(100) DEFAULT NULL,
  `error_message` VARCHAR(2000) DEFAULT NULL,
  `stack_trace` TEXT DEFAULT NULL,
  `ip` VARCHAR(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_time` (`error_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错误日志';

-- 17. operation_log (append-only, 保留180天)
CREATE TABLE `operation_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED DEFAULT NULL,
  `username` VARCHAR(50) DEFAULT NULL,
  `module` VARCHAR(50) NOT NULL,
  `operation` VARCHAR(50) NOT NULL,
  `target_type` VARCHAR(50) DEFAULT NULL,
  `target_id` VARCHAR(50) DEFAULT NULL,
  `method` VARCHAR(10) DEFAULT NULL,
  `request_url` VARCHAR(500) DEFAULT NULL,
  `request_param` TEXT DEFAULT NULL,
  `before_data` TEXT DEFAULT NULL,
  `after_data` TEXT DEFAULT NULL,
  `cost_time` BIGINT DEFAULT NULL,
  `operation_time` DATETIME NOT NULL,
  `ip` VARCHAR(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_module_time` (`module`,`operation_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

-- 18. sys_dict
CREATE TABLE `sys_dict` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `dict_type` VARCHAR(50) NOT NULL,
  `dict_key` VARCHAR(50) NOT NULL,
  `dict_value` VARCHAR(100) NOT NULL,
  `sort` INT NOT NULL DEFAULT 0,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_key_deleted` (`dict_type`,`dict_key`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典';
