package com.huiyi.modules.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huiyi.modules.knowledge.dto.KbIngestDTO;
import com.huiyi.modules.knowledge.embed.EmbeddingProvider;
import com.huiyi.modules.knowledge.ingest.TextChunker;
import com.huiyi.modules.knowledge.mapper.KnowledgeChunkMapper;
import com.huiyi.modules.knowledge.mapper.KnowledgeDocumentMapper;
import com.huiyi.modules.knowledge.model.KnowledgeChunk;
import com.huiyi.modules.knowledge.model.KnowledgeDocument;
import com.huiyi.modules.knowledge.model.Retrieval;
import com.huiyi.modules.knowledge.store.VectorHit;
import com.huiyi.modules.knowledge.store.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库编排:导入(切块→嵌入→入向量库 + 落库)、检索(嵌入查询→向量召回→回表取文本)、删除。
 *
 * 刻意把"向量放哪""用什么模型嵌入"委托给可插拔件(EmbeddingProvider / VectorStore),
 * 本类只管业务编排——这样换向量库 / 换嵌入模型都不用动这里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeDocumentMapper docMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final EmbeddingProvider embedding;
    private final VectorStore vectorStore;
    private final TextChunker chunker;
    private final ObjectMapper objectMapper;

    /** 相关度阈值:向量召回余弦得分低于此值视为不相关并丢弃(防假命中冒充命中、挤掉通用知识回退)。经 huiyi.kb.retrieval.min-score 可调。 */
    @Value("${huiyi.kb.retrieval.min-score:0.40}")
    private double minScore;

    /** 导入一篇文档:切块 → 每块嵌入 → 写向量库 + 落库。返回文档 id(重复内容返回已有 id,不重复建索引)。 */
    @Transactional
    public Long ingest(KbIngestDTO dto, Long userId) {
        String text = dto.getText();
        String hash = sha256(text);

        KnowledgeDocument dup = docMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getContentHash, hash).last("limit 1"));
        if (dup != null) {
            log.info("kb ingest skipped(duplicate) doc={} hash={}", dup.getId(), hash);
            return dup.getId();
        }

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTitle(dto.getTitle());
        doc.setSourceType(dto.getSourceType() == null ? "manual" : dto.getSourceType());
        doc.setSourceRef(dto.getSourceRef());
        doc.setContentHash(hash);
        doc.setStatus("ACTIVE");
        doc.setScope(resolveScope(dto.getScope()));
        doc.setCreatedBy(userId);
        doc.setCreatedAt(LocalDateTime.now());
        docMapper.insert(doc);

        List<String> pieces = chunker.chunk(text);
        String metaJson = buildMeta(doc);
        for (int i = 0; i < pieces.size(); i++) {
            String piece = pieces.get(i);
            KnowledgeChunk row = new KnowledgeChunk();
            row.setDocId(doc.getId());
            row.setOrdinal(i);
            row.setText(piece);
            row.setTokenCount(piece.length());
            row.setMetadata(metaJson);
            row.setCreatedAt(LocalDateTime.now());
            chunkMapper.insert(row);

            float[] vec = embedding.embed(piece);
            vectorStore.upsert(vectorKey(doc.getId(), i), vec,
                    Map.of("docId", String.valueOf(doc.getId()),
                            "ordinal", String.valueOf(i),
                            "scope", doc.getScope()));
        }
        doc.setChunkCount(pieces.size());
        docMapper.updateById(doc);
        log.info("kb ingest doc={} chunks={} emb={}", doc.getId(), pieces.size(), embedding.name());
        return doc.getId();
    }

    /** 检索:嵌入查询 → 向量库 topK(按 allowedScopes 行级过滤)→ minScore 过滤弱命中 → 回表取块文本 + 得分。按相关度降序。 */
    public List<Retrieval> search(String query, int topK, Set<String> allowedScopes) {
        float[] q = embedding.embed(query);
        List<VectorHit> hits = vectorStore.search(q, topK, allowedScopes);
        List<Retrieval> out = new ArrayList<>(hits.size());
        for (VectorHit h : hits) {
            if (h.getScore() < minScore) continue;   // 低于阈值=不相关,丢弃(避免假命中挤掉通用知识回退)
            String[] parts = h.getId().split(":");
            if (parts.length != 2) {
                continue;
            }
            long docId = Long.parseLong(parts[0]);
            int ordinal = Integer.parseInt(parts[1]);
            KnowledgeChunk ck = chunkMapper.selectOne(new LambdaQueryWrapper<KnowledgeChunk>()
                    .eq(KnowledgeChunk::getDocId, docId)
                    .eq(KnowledgeChunk::getOrdinal, ordinal)
                    .last("limit 1"));
            if (ck != null) {
                out.add(new Retrieval(ck, h.getScore()));
            }
        }
        // 诊断:打印候选 topK 的分数(过滤前,降序)+ 过 minScore 保留数,便于据此精调 huiyi.kb.retrieval.min-score。
        // "非0即满topK"通常即门槛太松:候选分数普遍刚过线(如 0.32~0.39)→ 全保留;据此把门槛提到能筛掉弱命中的值。
        if (log.isInfoEnabled() && !hits.isEmpty()) {
            // 打印「分数@docId」:分数看区分度,docId 看是否同一篇文档的多个 chunk 在霸占名额(决定要不要做同文档去重)
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hits.size(); i++) {
                if (i > 0) sb.append(',');
                VectorHit hh = hits.get(i);
                String id = hh.getId();
                int c = id == null ? -1 : id.indexOf(':');   // id 形如 "{docId}:{ordinal}"
                sb.append(String.format("%.3f@%s", hh.getScore(), c > 0 ? id.substring(0, c) : id));
            }
            String qs = query == null ? "" : (query.length() > 30 ? query.substring(0, 30) : query);
            log.info("kb retrieve q='{}' minScore={} candTopK={} scores=[{}] kept={}",
                    qs, minScore, hits.size(), sb, out.size());
        }
        return out;
    }

    public List<KnowledgeDocument> listDocs() {
        return docMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .orderByDesc(KnowledgeDocument::getCreatedAt));
    }

    /** 删除文档:逐块清向量 → 删块行 → 删文档行。幂等(不存在的 id 不报错)。 */
    @Transactional
    public void deleteDoc(Long docId) {
        List<KnowledgeChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocId, docId));
        for (KnowledgeChunk c : chunks) {
            vectorStore.delete(vectorKey(docId, c.getOrdinal()));
        }
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocId, docId));
        docMapper.deleteById(docId);
        log.info("kb delete doc={} (chunks={}, vectors cleared)", docId, chunks.size());
    }

    private static String vectorKey(Long docId, int ordinal) {
        return docId + ":" + ordinal;
    }

    /** 规整 scope:空值退化为 GLOBAL(公共,默认可见)。 */
    private static String resolveScope(String scope) {
        return (scope == null || scope.isBlank()) ? "GLOBAL" : scope;
    }

    private String buildMeta(KnowledgeDocument d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", d.getTitle());
        m.put("sourceType", d.getSourceType());
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(b.length * 2);
            for (byte x : b) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";   // 极端情况退化:空 hash → 不去重(不影响导入)
        }
    }
}
