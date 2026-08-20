package com.huiyi.modules.system.vo;

import lombok.Data;

/** 本地知识库运行指标(管理员监控页):已加载向量数 / 维度 / 估算占用内存 / 嵌入模型。 */
@Data
public class KbVO {
    private int vectorCount;        // 内存中向量条数(VectorStore.size())
    private int dimension;          // 嵌入维度(EmbeddingProvider.dimension();换 provider 会变,须清库重建)
    private long estMemBytes;       // 估算占用 = vectorCount × dimension × 4(float)
    private String embeddingModel;  // 嵌入模型标识(文本→向量,用于检索;bge-m3 / hashing 等)
    private String chatModel;       // 作答模型标识(LLM 生成回答;DeepSeek-V3 / Qwen3 等)
}
