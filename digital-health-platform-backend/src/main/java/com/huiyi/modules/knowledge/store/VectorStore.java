package com.huiyi.modules.knowledge.store;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 向量库:存取向量并按相似度(余弦)召回 topK。
 *
 * 抽象出来的目的:**向量库选型可推迟 / 可插拔**。默认 {@code MysqlVectorStore}(MySQL 存档 + 内存检索,适配小机);
 * 设 huiyi.kb.vectorstore.impl=memory 退回纯内存 {@code InMemoryVectorStore}(测试 / 无 DB)。
 * 换专用向量库实现一个 Bean 覆盖即可,业务层(KnowledgeService)零改动。候选见根目录《本地知识库AI问答-设计.md》:
 * Redis Stack(RediSearch,复用现有 Redis)/ Milvus(专用、重)/ Qdrant(专用、轻)/ pgvector(需换 Postgres)。
 *
 * id 约定:与知识块一一对应,格式 "{docId}:{ordinal}",便于回表取块文本。
 */
public interface VectorStore {

    /** 写入或覆盖一条向量(id 相同即覆盖)。metadata 存回显用的轻量元信息(如 docId/ordinal)。 */
    void upsert(String id, float[] vector, Map<String, String> metadata);

    /**
     * 按相似度降序返回 topK 命中(score 越大越相似)。
     * allowedScopes 为 null 时不过滤(管理员全可见);非 null 时只保留 scope ∈ 该集合的向量——
     * 即行级隔离,防药企/机构间串读。scope 经 {@link #upsert} 的 metadata("scope" 键)写入。
     */
    List<VectorHit> search(float[] query, int topK, Set<String> allowedScopes);

    /** 删除一条向量(删文档时逐块清理,防残留)。 */
    void delete(String id);

    /** 清空全部(重建索引 / 测试用)。 */
    void clear();

    /** 当前内存中向量条数(供监控页展示"已加载向量数";不含持久化但未 warmUp 的)。 */
    int size();
}
