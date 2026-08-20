package com.huiyi.modules.knowledge.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 检索中间结果:知识块 + 相似度得分。KnowledgeService 产出,RagService 消费后映射成对外 VO。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Retrieval {
    private KnowledgeChunk chunk;
    private double score;
}
