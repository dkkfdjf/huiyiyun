-- 操作日志增加 sensitive 标记:由 OperationLogAspect 非侵入式判定并写入
-- (命中敏感表「用户/认证」的写操作,或操作名含 删除/停用/重置/密码/权限/角色/解锁/锁定/驳回/审核 等关键词)
ALTER TABLE `operation_log` ADD COLUMN `sensitive` TINYINT NOT NULL DEFAULT 0 COMMENT '0普通/1敏感(切面自动判定)';
ALTER TABLE `operation_log` ADD INDEX `idx_sens_time` (`sensitive`, `operation_time`);
