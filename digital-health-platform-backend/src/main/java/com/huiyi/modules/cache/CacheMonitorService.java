package com.huiyi.modules.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis 缓存实时状态查询(只读)。供前端顶栏监控指示灯使用。
 * 权限在 Controller 层 @RequiresRole(ADMIN):Redis 运维指标只对管理员开放,不暴露给业务角色。
 * 本方法本身不进缓存(否则就监控不了缓存了)。
 */
@Service
@RequiredArgsConstructor
public class CacheMonitorService {

    private final RedisConnectionFactory connectionFactory;
    private final CacheMetrics cacheMetrics;

    /** 键清单封顶条数:防前端 v-for 全量渲染拉长页面,也避免 KEYS 命中极多时回包过大。 */
    private static final int MAX_KEY_ENTRIES = 200;

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        try (RedisConnection conn = connectionFactory.getConnection()) {
            // ping 失败(Redis 没开)会抛异常 → 进 catch 置 offline
            boolean online = "PONG".equalsIgnoreCase(conn.ping());
            m.put("online", online);
            if (!online) {
                return m;
            }
            m.put("dbsize", conn.dbSize());
            // 命中率取应用内存计数(CacheMetrics,经 CountingCacheManagerDecorator 在 Cache.get 读路径累计),
            // 而非 Redis INFO keyspace_hits/misses —— 后者是服务器全局累计(含其他用途、DEL 不归零),会误导。
            // 应用计数:重启归零;管理员「清空缓存」时由 clearAll 调 cacheMetrics.reset() 同步归零。
            long h = cacheMetrics.getHits(), miss = cacheMetrics.getMisses();
            long sum = h + miss;
            m.put("hits", h);
            m.put("misses", miss);
            m.put("hitRate", sum == 0 ? 0 : Math.round(h * 100.0 / sum));
            m.put("since", cacheMetrics.getStartedAt().toString());

            List<Map<String, Object>> keys = new ArrayList<>();
            Set<byte[]> raw = conn.keys("huiyi:*".getBytes(StandardCharsets.UTF_8));
            if (raw != null) {
                // 只取 key 名,不查 TTL(展示键清单无需额外往返)。
                for (byte[] kb : raw) {
                    Map<String, Object> k = new LinkedHashMap<>();
                    k.put("key", new String(kb, StandardCharsets.UTF_8));
                    keys.add(k);
                }
            }
            keys.sort(Comparator.comparing(k -> String.valueOf(k.get("key"))));
            // 键清单封顶:dbsize 才是精确总数(含非 huiyi: 前缀);keyTotal 仅本次匹配到的数量,供前端"共 N 条,仅显示前 200"提示。
            int keyTotal = keys.size();
            if (keyTotal > MAX_KEY_ENTRIES) {
                keys = new ArrayList<>(keys.subList(0, MAX_KEY_ENTRIES));
            }
            m.put("keys", keys);
            m.put("keyTotal", keyTotal);
        } catch (Exception e) {
            m.clear();
            m.put("online", false);
        }
        return m;
    }

    /**
     * 清空全部业务缓存(前缀 huiyi:*)。仅管理员可调(Controller 层 @RequiresRole 鉴权)。
     * 用途:Flyway 种子 / 手动改库后,缓存不会随 DB 自动失效,管理员一键驱逐,下次访问回查数据库。
     * 同时重置应用内命中率计数(CacheMetrics),使监控从清空点重新累计 —— 回应"清空后统计不归零"的诉求。
     */
    public void clearAll() {
        cacheMetrics.reset();
        try (RedisConnection conn = connectionFactory.getConnection()) {
            Set<byte[]> keys = conn.keys("huiyi:*".getBytes(StandardCharsets.UTF_8));
            if (keys != null && !keys.isEmpty()) {
                conn.del(keys.toArray(new byte[0][]));
            }
        }
    }
}
