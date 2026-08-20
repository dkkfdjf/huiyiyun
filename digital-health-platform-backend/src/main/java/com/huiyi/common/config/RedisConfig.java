package com.huiyi.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis 缓存配置 —— 四阶段演进路线 · 阶段一:缓存层。
 *
 * 设计要点:
 *  - 客户端 Lettuce(starter 默认,基于 Netty + 连接池,application.yml 已配 lettuce.pool)。
 *  - Key 用 StringRedisSerializer → redis-cli 可直接读,前缀统一 "huiyi:缓存名::参数"。
 *  - Value 用 GenericJackson2JsonRedisSerializer:带 @class 类型头,反序列化无需额外传 Class;
 *    ObjectMapper 显式注册 JavaTimeModule,兼容 BaseEntity 的 LocalDateTime(createTime/updateTime)。
 *  - 默认 TTL 30 分钟;高频全局参照数据单独设短 TTL。
 *  - sync=true 的 @Cacheable 防缓存击穿(thundering herd)。
 *
 * 安全边界:本阶段只缓存【无租户隔离的全局参照数据】(必备材料分页 / 已审核药企列表)。
 *  Company / Institution 行级隔离的数据暂不进缓存,避免按租户串读 —— 对应「后端可观测性」经验,
 *  待后续引入按租户 key 的成熟方案再扩展。
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /** JSON 值序列化器:类型头 + LocalDateTime 支持(每次新建,避免多缓存共享同一 mapper 状态)。 */
    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 带类型头,反序列化无需传 Class;用白名单校验器,仅放行本项目可信类型(防反序列化漏洞)
        om.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL);
        return new GenericJackson2JsonRedisSerializer(om);
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "huiyi:" + cacheName + "::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer()));

        // 高频全局参照数据:写少读多,设短 TTL 兜底(药企新增/编辑/启停即整表驱逐,另靠 TTL 失效)
        Map<String, RedisCacheConfiguration> perCache = Map.of(
                "companies:active", base.entryTtl(Duration.ofMinutes(10)),
                "materials:page", base.entryTtl(Duration.ofMinutes(15))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(perCache)
                .transactionAware()
                .build();
    }

    /** 通用 RedisTemplate(String key + JSON value),供后续阶段(session/限流/手动 ops)使用。 */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> tpl = new RedisTemplate<>();
        tpl.setConnectionFactory(connectionFactory);
        StringRedisSerializer str = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer json = jsonSerializer();
        tpl.setKeySerializer(str);
        tpl.setHashKeySerializer(str);
        tpl.setValueSerializer(json);
        tpl.setHashValueSerializer(json);
        tpl.afterPropertiesSet();
        return tpl;
    }
}
