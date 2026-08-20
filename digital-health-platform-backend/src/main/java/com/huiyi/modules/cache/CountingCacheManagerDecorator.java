package com.huiyi.modules.cache;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * 命中率计数:包装 Spring {@link CacheManager},把每个 {@link Cache} 的读操作(get)计入 {@link CacheMetrics}。
 * <p>
 * 为什么不用 Redis INFO keyspace_hits/misses:那是 Redis <b>服务器全局累计</b>统计
 * (含其他用途、DEL 缓存不归零、重启 Redis 才清),当作"本应用缓存命中率"展示会严重误导。
 * 这里在应用进程内按每次 get 返回值(null=未命中 / 非 null=命中)精确计数,语义清晰:
 *  - 重启应用自动归零;
 *  - 管理员「清空缓存」时由 {@link CacheMonitorService#clearAll()} 同步 reset。
 * <p>
 * 覆盖 @Cacheable 两种读路径:非 sync 走 {@link Cache#get(Object)},sync=true 走 {@link Cache#get(Object, Callable)}。
 */
@Component
public class CountingCacheManagerDecorator implements BeanPostProcessor {

    private final CacheMetrics metrics;

    public CountingCacheManagerDecorator(CacheMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 只包装一次:已包装的 CountingCacheManager 不再二次包
        if (bean instanceof CacheManager cm && !(bean instanceof CountingCacheManager)) {
            return new CountingCacheManager(cm, metrics);
        }
        return bean;
    }

    /** CacheManager 装饰器:getCache 返回的 Cache 再包一层会计数的 CountingCache。 */
    static final class CountingCacheManager implements CacheManager {
        private final CacheManager delegate;
        private final CacheMetrics metrics;

        CountingCacheManager(CacheManager delegate, CacheMetrics metrics) {
            this.delegate = delegate;
            this.metrics = metrics;
        }

        @Override
        public Cache getCache(String name) {
            Cache c = delegate.getCache(name);
            return c == null ? null : new CountingCache(c, metrics);
        }

        @Override
        public Collection<String> getCacheNames() {
            return delegate.getCacheNames();
        }
    }

    /** Cache 装饰器:仅在读路径(get)统计命中/未命中,其余写操作全委托,不改变缓存语义。 */
    @SuppressWarnings("unchecked")
    static final class CountingCache implements Cache {
        private final Cache delegate;
        private final CacheMetrics metrics;

        CountingCache(Cache delegate, CacheMetrics metrics) {
            this.delegate = delegate;
            this.metrics = metrics;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Object getNativeCache() {
            return delegate.getNativeCache();
        }

        /** 非 sync 的 @Cacheable 读:返回值非 null=命中,null=未命中(随后由 Spring put)。 */
        @Override
        public ValueWrapper get(Object key) {
            ValueWrapper w = delegate.get(key);
            if (w != null) {
                metrics.recordHit();
            } else {
                metrics.recordMiss();
            }
            return w;
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            T v = delegate.get(key, type);
            if (v != null) {
                metrics.recordHit();
            } else {
                metrics.recordMiss();
            }
            return v;
        }

        /** sync=true 的 @Cacheable 读:先 peek 判定命中/未命中,未命中再委托真实加载(sync 防击穿语义保持)。 */
        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            ValueWrapper w = delegate.get(key);
            if (w != null) {
                metrics.recordHit();
                return (T) w.get();
            }
            metrics.recordMiss();
            return delegate.get(key, valueLoader);
        }

        @Override
        public void put(Object key, Object value) {
            delegate.put(key, value);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            return delegate.putIfAbsent(key, value);
        }

        @Override
        public void evict(Object key) {
            delegate.evict(key);
        }

        @Override
        public void clear() {
            delegate.clear();
        }
    }
}
