-- 慧医云 知识库行级隔离(Flyway V11)—— 给文档与向量加 scope(可见范围)列。
-- scope 取值:GLOBAL(全员)/ COMPANY:{id}(本药企+管理员) / INSTITUTION:{id}(本机构+管理员)。
-- 检索时先按当前用户可见 scope 过滤、再算余弦,防药企/机构间串读。
-- 旧数据默认 GLOBAL(安全:历史手导资料视为公共)。

ALTER TABLE `kb_document` ADD COLUMN `scope` VARCHAR(48) NOT NULL DEFAULT 'GLOBAL'
  COMMENT '可见范围 GLOBAL / COMPANY:id / INSTITUTION:id';
ALTER TABLE `kb_vector` ADD COLUMN `scope` VARCHAR(48) NOT NULL DEFAULT 'GLOBAL'
  COMMENT '同 kb_document.scope;检索过滤用';
ALTER TABLE `kb_vector` ADD INDEX `idx_kb_vector_scope` (`scope`);
