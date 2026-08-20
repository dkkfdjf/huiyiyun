package com.huiyi.modules.knowledge.embed;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认嵌入实现:特征哈希(hashing trick)。
 *
 * 把文本按"中文单字 + 英文/数字 token"切分,每个 token 哈希到固定维度向量的某一维上累加词频,
 * 再 L2 归一化——得到的是"词面"指纹,余弦相似度反映的是**用词重合度,不是语义**。
 * 零依赖、确定性、能跑,但绝不能当真实语义检索用(同义词、改写完全抓不到)。
 *
 * 它存在只是为了让知识库框架在**未接真实嵌入模型**时也能端到端跑通(导入→检索→命中)。
 * 选型靠 huiyi.kb.embedding.impl:hashing(本默认,matchIfMissing)/ siliconflow(bge-m3 语义)。业务层零改动。
 */
@Component
@ConditionalOnProperty(prefix = "huiyi.kb.embedding", name = "impl", havingValue = "hashing", matchIfMissing = true)
public class HashingEmbeddingProvider implements EmbeddingProvider {

    /** CJK 取单字;英文/数字连成一段。统一小写,做最朴素的归一。 */
    private static final Pattern TOKEN = Pattern.compile("[\\p{IsHan}]|[a-z0-9]+");

    private final int dim;

    public HashingEmbeddingProvider(@Value("${huiyi.kb.embedding.dimension:256}") int dim) {
        this.dim = Math.max(1, dim);
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[dim];
        if (text == null || text.isBlank()) {
            return v;
        }
        Matcher m = TOKEN.matcher(text.toLowerCase());
        while (m.find()) {
            int h = stableHash(m.group());
            v[Math.floorMod(h, dim)] += 1.0f;
        }
        // L2 归一化:使余弦相似度退化为点积(InMemoryVectorStore 依赖此约定)
        double norm = 0;
        for (float f : v) {
            norm += f * f;
        }
        if (norm > 0) {
            double s = Math.sqrt(norm);
            for (int i = 0; i < dim; i++) {
                v[i] /= s;
            }
        }
        return v;
    }

    @Override
    public int dimension() {
        return dim;
    }

    @Override
    public String name() {
        return "hashing-" + dim;
    }

    /** 确定性字符串哈希(不用 Math.random,保证同文本同向量)。 */
    private static int stableHash(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
        }
        return h;
    }
}
