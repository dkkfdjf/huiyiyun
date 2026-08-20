-- 纠正:模块「认证」(登录/登出)属常规行为,不应标敏感。
-- V6 回填曾按 module IN('用户','认证') 把登录/登出误标为敏感;切面已去掉「认证」敏感表,
-- 此迁移把历史认证行同步改回常规,保证日志筛选 敏感/常规 与新判定一致。sensitive 是 MySQL 保留字,须反引号。
UPDATE `operation_log` SET `sensitive` = 0 WHERE `module` = '认证';
