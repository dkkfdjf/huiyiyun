-- 慧医云 知识库对话日志增加提问来源 IP(Flyway V13)。
-- 游客(GUEST)无 user_id 行,既有的「按 user_id 计高频/重复」对其直接放行(无效)。
-- 落库时记录 client_ip:游客行按 IP 回退计频(KbChatLogService),已登录用户亦记,便于管理员追溯提问来源网络。
-- 索引 (client_ip, created_at):支撑游客 IP 窗口计数;高频/重复检测在 user_id IS NULL 时回退走此索引。
ALTER TABLE `kb_chat_log` ADD COLUMN `client_ip` VARCHAR(64) NULL COMMENT '提问来源 IP(游客按此计频/审计;X-Forwarded-For 首段)' AFTER `role`;
ALTER TABLE `kb_chat_log` ADD INDEX `idx_kb_chat_log_ip_time` (`client_ip`, `created_at`);
