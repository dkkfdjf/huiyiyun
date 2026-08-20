package com.huiyi.modules.cache;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 应用内缓存命中计数(非 Redis 全局统计)。
 * <p>
 * 计数存于应用内存,因此:
 *  - 重启应用即归零(符合"重启统计"语义);
 *  - 管理员「清空缓存」时由 {@link CacheMonitorService#clearAll()} 调 {@link #reset()} 同步归零。
 * <p>
 * 用于向管理员展示"自应用启动 / 上次清空以来"的真实命中率,诚实反映本应用缓存效果,
 * 取代之前误读的 Redis INFO keyspace_hits/misses(后者是服务器全局累计、DEL 不归零)。
 */
@Component
public class CacheMetrics {

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final Instant startedAt = Instant.now();

    public void recordHit() {
        hits.incrementAndGet();
    }

    public void recordMiss() {
        misses.incrementAndGet();
    }

    public long getHits() {
        return hits.get();
    }

    public long getMisses() {
        return misses.get();
    }

    /** 应用启动 / 上次清空的时刻(UTC),供前端标注"自 HH:mm 统计"。 */
    public Instant getStartedAt() {
        return startedAt;
    }

    /** 清空缓存时一并重置命中率,让监控从清空点重新累计。 */
    public void reset() {
        hits.set(0);
        misses.set(0);
    }
}
