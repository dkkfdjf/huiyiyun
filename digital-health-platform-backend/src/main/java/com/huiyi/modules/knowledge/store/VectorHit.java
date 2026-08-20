package com.huiyi.modules.knowledge.store;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 向量检索命中:id(对应知识块 "{docId}:{ordinal}") + 余弦得分(越大越相似)。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorHit {
    private String id;
    private double score;
}
