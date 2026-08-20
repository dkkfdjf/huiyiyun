package com.huiyi.modules.knowledge.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切块:固定字符大小 + 相邻块重叠。
 *
 * 为什么切块:嵌入模型对长文本会"稀释"语义,整篇灌进去检索精度差;切成段落级小块,
 * 每块语义聚焦,召回更准。重叠是为了防止关键句被切在两块的边界上而两头都丢。
 *
 * 这里按"字符"近似(中文一字≈一字),够用于课设。生产可换按 token 切(需分词器),
 * 接口不变。空白合并成单空格,避免换行/缩进污染。
 */
@Component
public class TextChunker {

    private final int chunkSize;
    private final int overlap;

    public TextChunker(@Value("${huiyi.kb.chunk.size:500}") int chunkSize,
                       @Value("${huiyi.kb.chunk.overlap:60}") int overlap) {
        this.chunkSize = Math.max(1, chunkSize);
        // overlap 必须 < chunkSize,否则步长 ≤ 0 会死循环;这里夹紧
        this.overlap = Math.max(0, Math.min(overlap, this.chunkSize - 1));
    }

    public List<String> chunk(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String norm = text.replaceAll("\\s+", " ").trim();
        int len = norm.length();
        int step = chunkSize - overlap;
        for (int start = 0; start < len; start += step) {
            int end = Math.min(start + chunkSize, len);
            String piece = norm.substring(start, end).strip();
            if (!piece.isEmpty()) {
                out.add(piece);
            }
            if (end == len) {
                break;
            }
        }
        return out;
    }
}
