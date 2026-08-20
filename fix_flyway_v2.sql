-- 修复 Flyway V2 迁移校验失败问题
-- 执行此脚本后，重启 Spring Boot 应用即可

USE huiyiyun;

-- 1. 删除旧的 V2 迁移记录（seed_essential_materials）
DELETE FROM flyway_schema_history WHERE version = '2' AND installed_rank > 0;

-- 2. 如果旧的 essential_materials 表存在，也删除它（可选）
-- DROP TABLE IF EXISTS essential_material;

-- 3. 检查删除结果
SELECT * FROM flyway_schema_history WHERE version = '2';

-- 执行完此脚本后，Flyway 会在下次启动时自动执行新的 V2__add_notification.sql
