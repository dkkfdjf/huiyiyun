package com.huiyi.modules.knowledge.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 纯内存向量库:进程内 ConcurrentHashMap + 暴力余弦(点积,向量须已 L2 归一化)。
 *
 * 行级隔离:scope 经 upsert 的 metadata("scope" 键)写入,search 按 allowedScopes 过滤(与 MysqlVectorStore 同语义)。
 *
 * 仅适合单机开发/测试——重启即丢、无法水平扩展、全量扫描 O(n)。
 * opt-in:设 huiyi.kb.vectorstore.impl=memory 才启用(测试/无 DB)。默认走 MysqlVectorStore。KnowledgeService 零改动。
 */
@Component
@ConditionalOnProperty(prefix = "huiyi.kb.vectorstore", name = "impl", havingValue = "memory")
public class InMemoryVectorStore implements VectorStore {

    private record Entry(float[] vector, Map<String, String> meta) {
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public void upsert(String id, float[] vector, Map<String, String> metadata) {
        store.put(id, new Entry(vector.clone(), metadata == null ? Map.of() : metadata));
    }

    @Override
    public List<VectorHit> search(float[] query, int topK, Set<String> allowedScopes) {
        if (query == null || query.length == 0) {
            return List.of();
        }
        List<VectorHit> all = new ArrayList<>(store.size());
        store.forEach((id, e) -> {
            if (allowedScopes != null) {                       // null = 不过滤(管理员全可见)
                String sc = e.meta() == null ? null : e.meta().get("scope");
                if (sc == null || !allowedScopes.contains(sc)) {
                    return;
                }
            }
            all.add(new VectorHit(id, cosine(query, e.vector())));
        });
        all.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));   // 降序
        return all.size() <= topK ? all : new ArrayList<>(all.subList(0, topK));
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }

    @Override
    public void clear() {
        store.clear();
    }

    @Override
    public int size() {
        return store.size();
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
