-- 慧医云 知识库对话日志(Flyway V12)—— 记录每次 /ask 的提问/回答/命中数,
-- 供管理员审计 + 异常提问检测。
--
-- 异常标记规则(用户定「0命中 + 高频」):
--   0命中  —— 本次作答命中的来源片段数 = 0(知识库答不上:盲区 / 越权提问 / 无关问题);
--   高频   —— 同一用户 10 分钟内提问 > 10 次(刷量 / 滥用 / 疑似爬取)。
-- 命中任一即 flagged=1,flag_reason 记原因。日志写入失败不影响作答(调用方 try/catch)。
--
-- 索引:user_id+created_at(高频窗口计数)、flagged(筛异常)、created_at(时间倒序分页)。

CREATE TABLE `kb_chat_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NULL          COMMENT '提问用户 id(理论均登录,留空兜底)',
    `username`    VARCHAR(64)  NULL          COMMENT '提问用户登录名',
    `role`        TINYINT      NULL          COMMENT '提问用户角色 0管理员/1药企/2机构/3医师',
    `query_text`  VARCHAR(500) NOT NULL      COMMENT '用户提问(超长截断)',
    `answer_text` TEXT         NULL          COMMENT '知识库作答(超长截断)',
    `hit_count`   INT          NOT NULL DEFAULT 0 COMMENT '命中的来源片段数(0=知识库答不上)',
    `flagged`     TINYINT(1)   NOT NULL DEFAULT 0  COMMENT '异常标记 0/1',
    `flag_reason` VARCHAR(64)  NULL          COMMENT '异常原因:0命中 / 高频 / 0命中+高频',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提问时间',
    PRIMARY KEY (`id`),
    KEY `idx_kb_chat_log_user_time` (`user_id`, `created_at`),
    KEY `idx_kb_chat_log_flagged` (`flagged`),
    KEY `idx_kb_chat_log_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库对话日志(管理员审计 + 异常提问检测)';
