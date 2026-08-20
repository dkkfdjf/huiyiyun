-- 慧医云 本地知识库 AI 问答(RAG):文档与切块的文本/元数据表(Flyway V9)。
-- 向量本身不存 MySQL,而存 VectorStore(默认进程内;可换 Redis Stack/Milvus/Qdrant/pgvector)。
-- MySQL 只存文本 + 元信息(可回显、可重建索引)。详见根目录《本地知识库AI问答-设计.md》。

CREATE TABLE `kb_document` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `title`         VARCHAR(255) NOT NULL,
  `source_type`   VARCHAR(32)  NOT NULL DEFAULT 'manual' COMMENT 'manual/file/url',
  `source_ref`    VARCHAR(255) DEFAULT NULL,
  `content_hash`  CHAR(64)     DEFAULT NULL COMMENT 'SHA-256,去重用',
  `chunk_count`   INT          NOT NULL DEFAULT 0,
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  `created_by`    BIGINT       DEFAULT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_doc_hash` (`content_hash`),
  KEY `idx_kb_doc_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';

CREATE TABLE `kb_chunk` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `doc_id`      BIGINT       NOT NULL,
  `ordinal`     INT          NOT NULL COMMENT '块序号,从 0;(doc_id,ordinal) 与向量库 id 对齐',
  `text`        MEDIUMTEXT   NOT NULL,
  `token_count` INT          DEFAULT NULL COMMENT '近似长度(字符数)',
  `metadata`    TEXT         DEFAULT NULL COMMENT '回显用 JSON 文本:{title,sourceType,...}',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_chunk_doc` (`doc_id`, `ordinal`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库切块(向量存外部 VectorStore)';

-- 知识库总开关(默认开;管理员可在 system_config 改)。
INSERT INTO `system_config`(`config_key`,`config_value`,`description`) VALUES
  ('kb.enabled','true','本地知识库 AI 问答总开关:true 启用 / false 停用');
