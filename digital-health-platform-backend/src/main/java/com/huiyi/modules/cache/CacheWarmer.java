package com.huiyi.modules.cache;

import com.huiyi.modules.company.PharmaCompanyService;
import com.huiyi.modules.material.EssentialMaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 缓存预热:应用就绪后(ApplicationReadyEvent,在所有 ApplicationRunner 种子之后)主动调用
 * 热点 @Cacheable 方法,经代理写入 Redis,让任意首请求即缓存命中、不踩冷启动 DB。
 * Redis 未就绪/数据为空时仅告警、不阻断启动(首请求会懒加载)。
 * 开关:huiyi.cache.prewarm(默认 true)。
 *
 * 注:预热首填缓存时会经 CountingCacheManagerDecorator 计为 miss(缓存从空填入),污染起步命中率。
 *     故预热成功后调 cacheMetrics.reset() 归零,让监控页命中率从"应用就绪后"真实累计(缓存内容保留)。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "huiyi.cache.prewarm", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class CacheWarmer {

    private final PharmaCompanyService companyService;
    private final EssentialMaterialService materialService;
    private final CacheMetrics cacheMetrics;

    /** 材料页常按分类筛,预热各分类首页;新增分类首访问时再懒加载。 */
    private static final List<String> MATERIAL_CATEGORIES = List.of("报销类", "特殊病种类", "慢病类");

    @EventListener(ApplicationReadyEvent.class)
    public void warm() {
        try {
            // companies:active(无参 → 单键 SimpleKey,一次填满整缓存)
            companyService.listActive();
            // materials:page 默认首页 + 各分类首页(4 参复合键)
            materialService.page(null, null, 1, 10);
            for (String cat : MATERIAL_CATEGORIES) {
                materialService.page(null, cat, 1, 10);
            }
            // 预热首填会计为 miss(缓存从空填入),污染起步命中率;归零让监控从"就绪后"真实累计
            cacheMetrics.reset();
            log.info("cache prewarm done");
        } catch (Exception e) {
            // Redis 未就绪等:预热失败不影响启动,首请求会懒加载
            log.warn("cache prewarm skipped: {}", e.getMessage());
        }
    }
}
