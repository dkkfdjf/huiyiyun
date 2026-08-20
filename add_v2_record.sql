-- 手动插入 V2 迁移记录（因为表已存在且结构正确）
USE huiyiyun;

-- 插入 V2 迁移记录
INSERT INTO flyway_schema_history (
    installed_rank,
    version,
    description,
    type,
    script,
    checksum,
    installed_by,
    installed_on,
    execution_time,
    success
) VALUES (
    2,                              -- installed_rank
    '2',                            -- version
    'add notification',             -- description
    'SQL',                          -- type
    'V2__add_notification.sql',     -- script
    -910120650,                     -- checksum (本地解析的值)
    'root',                         -- installed_by
    NOW(),                          -- installed_on
    5,                              -- execution_time (模拟)
    1                               -- success (1=成功)
);

-- 验证结果
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
