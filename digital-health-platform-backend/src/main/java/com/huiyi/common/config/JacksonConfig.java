package com.huiyi.common.config;

import com.fasterxml.jackson.databind.MapperFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 序列化配置。
 * <p>显式开启 {@link MapperFeature#DEFAULT_VIEW_INCLUSION}:激活 @JsonView 序列化时,无 @JsonView 注解的字段
 * 仍照常输出。这是游客脱敏({@code GuestViewResponseAdvice} 对游客响应激活 GuestView)的硬依赖——若此项关闭,
 * 游客的 R/PageResult 等无注解响应会被视图整体清空成 {},前端 unwrap 拿不到 code/token/data,
 * 表现为"登录成功却进不去"和"几乎所有页面请求失败"。
 * <p>{@code application.yml} 的 {@code spring.jackson.default-view-inclusion=true} 已设此项(Spring Boot 映射
 * 到本 MapperFeature);此处再以代码兜底,双保险,避免脱敏因配置变更而静默失效。
 * <p>注:该开关属 {@link MapperFeature}(非 SerializationFeature),经 builder.postConfigurer 直接在
 * ObjectMapper 上开启,API 稳定可靠。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer defaultViewInclusionCustomizer() {
        return builder -> builder.postConfigurer(mapper -> mapper.enable(MapperFeature.DEFAULT_VIEW_INCLUSION));
    }
}
