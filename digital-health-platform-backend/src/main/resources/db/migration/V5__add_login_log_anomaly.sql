-- 登录日志增加异常 IP 标记:成功登录时,若该用户有登录历史但首次从此 IP 登录 → 标记异常(疑似异地/盗号)
ALTER TABLE `login_log` ADD COLUMN `anomaly` TINYINT NOT NULL DEFAULT 0 COMMENT '0正常/1异常IP(老用户首次从该IP登录)';
ALTER TABLE `login_log` ADD COLUMN `anomaly_reason` VARCHAR(100) DEFAULT NULL COMMENT '异常原因';
ALTER TABLE `login_log` ADD INDEX `idx_anomaly_time` (`anomaly`, `login_time`);
