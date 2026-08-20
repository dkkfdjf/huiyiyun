package com.huiyi.modules.knowledge.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B 方案向量库:向量以 JSON 存 MySQL(kb_vector),应用就绪后加载回内存做暴力余弦检索。
 *
 * 行级隔离:每条向量带 scope(可见范围),检索时按当前用户可见 scope 先过滤、再算余弦,防药企/机构间串读。
 * scope 来源:upsert 的 metadata("scope" 键,由 KnowledgeService 写入)→ 落 kb_vector.scope 列 → 启动加载回 idScope。
 *
 * 为什么这么设计(适配 2核2G 全第三方):
 * <ul>
 *   <li>检索仍在应用内存(暴力余弦、毫秒级、几 MB)——和 InMemoryVectorStore 一样省;</li>
 *   <li>但向量持久化在 MySQL(已在跑),重启不丢、且不用重新调 SiliconFlow 嵌入 API(省钱);</li>
 *   <li>零新进程、零新内存增量——区别只在"重启后向量还在"。</li>
 * </ul>
 *
 * 选型:huiyi.kb.vectorstore.impl 缺省=mysql(本类,matchIfMissing);设 memory 退回纯内存。KnowledgeService 零改动。
 * id 格式 "{docId}:{ordinal}"。注意 MySQL 是关系库、无原生向量检索——本类不靠它做向量数学,只当存档。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "huiyi.kb.vectorstore", name = "impl", havingValue = "mysql", matchIfMissing = true)
public class MysqlVectorStore implements VectorStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 内存索引:id → 归一化向量。启动从 kb_vector 加载,检索/写入都走它(MySQL 只管存档)。 */
    private final Map<String, float[]> mem = new ConcurrentHashMap<>();
    /** id → scope(可见范围),与 mem 并行;检索时按 allowedScopes 过滤用。 */
    private final Map<String, String> idScope = new ConcurrentHashMap<>();

    public MysqlVectorStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 应用就绪后(Flyway 已建 kb_vector)从 MySQL 全量加载回内存。表未就绪则安静地空启动。 */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, vector, scope FROM kb_vector");
            for (Map<String, Object> r : rows) {
                float[] v = decode((String) r.get("vector"));
                if (v != null && v.length > 0) {
                    String id = (String) r.get("id");
                    mem.put(id, v);
                    Object sc = r.get("scope");
                    idScope.put(id, sc == null ? "GLOBAL" : sc.toString());
                }
            }
            log.info("kb vector store warmed up from MySQL: {} vectors", mem.size());
        } catch (Exception e) {
            log.warn("kb vector warm-up failed (start empty): {}", e.getMessage());
        }
    }

    @Override
    public void upsert(String id, float[] vector, Map<String, String> metadata) {
        if (id == null || vector == null || vector.length == 0) {
            return;
        }
        String scope = metadata == null ? null : metadata.get("scope");
        if (scope == null || scope.isBlank()) {
            scope = "GLOBAL";
        }
        mem.put(id, vector.clone());
        idScope.put(id, scope);
        try {
            jdbc.update("INSERT INTO kb_vector(id, vector, scope) VALUES(?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE vector = VALUES(vector), scope = VALUES(scope)", id, encode(vector), scope);
        } catch (Exception e) {
            log.warn("kb vector upsert failed id={} : {}", id, e.getMessage());
        }
    }

    @Override
    public List<VectorHit> search(float[] query, int topK, Set<String> allowedScopes) {
        if (query == null || query.length == 0) {
            return List.of();
        }
        List<VectorHit> all = new ArrayList<>(mem.size());
        mem.forEach((id, v) -> {
            if (allowedScopes != null) {                       // null = 不过滤(管理员全可见)
                String sc = idScope.get(id);
                if (sc == null || !allowedScopes.contains(sc)) {
                    return;
                }
            }
            all.add(new VectorHit(id, cosine(query, v)));
        });
        all.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));   // 降序
        return all.size() <= topK ? all : new ArrayList<>(all.subList(0, topK));
    }

    @Override
    public void delete(String id) {
        mem.remove(id);
        idScope.remove(id);
        try {
            jdbc.update("DELETE FROM kb_vector WHERE id = ?", id);
        } catch (Exception e) {
            log.warn("kb vector delete failed id={} : {}", id, e.getMessage());
        }
    }

    @Override
    public void clear() {
        mem.clear();
        idScope.clear();
        try {
            jdbc.update("DELETE FROM kb_vector");
        } catch (Exception e) {
            log.warn("kb vector clear failed : {}", e.getMessage());
        }
    }

    @Override
    public int size() {
        return mem.size();
    }

    // ---- 向量 JSON 编解码:float[] <-> "[f0,f1,...]" ----
    private String encode(float[] v) {
        try {
            return mapper.writeValueAsString(v);
        } catch (Exception e) {
            return "[]";
        }
    }

    private float[] decode(String json) {
        try {
            return mapper.readValue(json, float[].class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 向量已 L2 归一化 → 点积即余弦;维度不一致返回 -1(视作完全不相关)。 */
    private static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            return -1;
        }
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return dot;
    }
}
