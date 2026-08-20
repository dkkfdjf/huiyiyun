package com.huiyi.modules.knowledge.embed;

/**
 * 文本嵌入(把一段文本映射成固定维度的浮点向量"语义指纹")。
 *
 * 是 RAG 检索的基石——只有把"文档块"和"查询"映射到同一向量空间,才能用相似度召回相关片段。
 * 抽成接口是为了**让嵌入模型可插拔**:课设/开发用 {@code HashingEmbeddingProvider}(零依赖、词面相似);
 * 接真实语义检索时,实现一个 Bean(本地 Ollama bge-m3 / DashScope text-embedding-v2 / OpenAI)覆盖默认即可。
 *
 * 实现约束:① 线程安全;② 对相同文本返回相同向量(确定性);③ ingest 与 query 必须用同一 provider/同一维度。
 */
public interface EmbeddingProvider {

    /** 把文本编码成维度为 {@link #dimension()} 的向量。建议返回 L2 归一化向量,便于余弦=点积。 */
    float[] embed(String text);

    /** 向量维度。换 provider 时维度可能变,须清空向量库重建索引。 */
    int dimension();

    /** 模型标识,便于日志/监控区分"现在用的什么嵌入"。 */
    default String name() {
        return "unknown";
    }
}
