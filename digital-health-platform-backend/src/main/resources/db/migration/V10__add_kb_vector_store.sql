-- 慧医云 本地知识库 向量存档表(Flyway V10)—— B 方案:向量以 JSON 存 MySQL,启动加载回内存做余弦检索。
-- 与 kb_chunk 解耦:kb_vector 用 VectorStore 的 id("{doc_id}:{ordinal}")做主键,向量以 JSON 数组存盘。
-- 为什么存 MySQL:复用已在跑的库,重启不丢向量、且不用重新调嵌入 API(省 SiliconFlow 额度);检索仍在内存(几 MB),零新进程。适配 2核2G。
-- 切回纯内存:设 huiyi.kb.vectorstore.impl=memory(本表闲置,数据不丢)。

CREATE TABLE `kb_vector` (
  `id`         VARCHAR(128) NOT NULL COMMENT '向量 id:{doc_id}:{ordinal},与 kb_chunk (doc_id,ordinal) 对齐',
  `vector`     LONGTEXT     NOT NULL COMMENT 'JSON 数组 [f0,f1,...](bge-m3 1024 维,已 L2 归一化)',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库向量存档(B 方案:MySQL 存盘 + 内存检索)';
