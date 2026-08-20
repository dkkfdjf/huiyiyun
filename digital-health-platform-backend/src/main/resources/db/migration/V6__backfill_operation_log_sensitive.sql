-- 回填历史操作日志的 sensitive 标记(按切面同款规则),让既有日志也能按 敏感/常规 区分、可见。
-- 新日志由 OperationLogAspect 自动判定;此迁移只补历史。sensitive 是 MySQL 保留字,须反引号。
UPDATE `operation_log` SET `sensitive` = 1
WHERE `module` IN ('用户','认证')
   OR `operation` LIKE '%删除%' OR `operation` LIKE '%停用%' OR `operation` LIKE '%禁用%'
   OR `operation` LIKE '%重置%' OR `operation` LIKE '%密码%' OR `operation` LIKE '%权限%'
   OR `operation` LIKE '%角色%' OR `operation` LIKE '%解锁%' OR `operation` LIKE '%锁定%'
   OR `operation` LIKE '%驳回%' OR `operation` LIKE '%审核%';
