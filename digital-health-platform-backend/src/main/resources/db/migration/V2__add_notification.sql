-- 慧医云 通知中心(Flyway V2)。
-- 站内通知:库存预警/反馈流转/药企审核/系统;ref_type + ref_id 指向相关业务对象。
-- 关联靠应用层 + 索引,无外键(对齐 V1 约定)。

CREATE TABLE `notification` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '接收者 user.id',
  `category` TINYINT NOT NULL COMMENT '0库存预警/1反馈流转/2药企审核/3系统',
  `title` VARCHAR(120) NOT NULL,
  `body` VARCHAR(500) DEFAULT NULL,
  `ref_type` VARCHAR(30) DEFAULT NULL COMMENT 'DEMAND/COMPANY/STOCK',
  `ref_id` BIGINT UNSIGNED DEFAULT NULL,
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '0未读/1已读',
  `create_by` VARCHAR(50) DEFAULT NULL,
  `update_by` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`,`is_read`),
  KEY `idx_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知';
