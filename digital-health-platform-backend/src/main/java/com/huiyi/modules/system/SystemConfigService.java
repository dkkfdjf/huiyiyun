package com.huiyi.modules.system;

import com.huiyi.modules.system.entity.SystemConfig;
import com.huiyi.modules.system.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 系统配置(KV)读写:通用全局开关,管理员可改。
 *
 * Redis 读穿缓存(key=huiyi:sys:config:&lt;config_key&gt;)+ 写时删除,
 * 避免登录热路径每次查库;Redis 抖动时降级直查 DB。
 * 属"无租户隔离全局参照数据",符合缓存阶段一范围。
 * 当前承载 captcha.mode;未来承载本地知识库 AI 问答等开关,直接复用 get/set。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private static final String CACHE_PREFIX = "huiyi:sys:config:";
    private static final String KEY_CAPTCHA_MODE = "captcha.mode";
    private static final String KEY_KB_ENABLED = "kb.enabled";
    private static final String KEY_GUEST_ENABLED = "guest.enabled";

    private final SystemConfigMapper mapper;
    private final StringRedisTemplate redis;

    /** 通用读:Redis → DB 读穿,缺省返回 def。 */
    public String get(String key, String def) {
        String ck = CACHE_PREFIX + key;
        try {
            String cached = redis.opsForValue().get(ck);
            if (cached != null) return cached;
        } catch (Exception e) {
            log.warn("sysconfig redis read fallback to DB, key={} : {}", key, e.getMessage());
        }
        SystemConfig c = mapper.selectById(key);
        String v = c != null ? c.getConfigValue() : def;
        if (c != null) {   // 仅当 DB 有该键才回填缓存(避免把"缺省值"当真值缓存)
            try { redis.opsForValue().set(ck, v); } catch (Exception ignore) { }
        }
        return v;
    }

    /** 通用写:upsert DB + 删缓存(下次读重建)。 */
    public void set(String key, String value, Long updatedBy) {
        SystemConfig c = mapper.selectById(key);
        if (c == null) {
            c = new SystemConfig();
            c.setConfigKey(key);
            c.setConfigValue(value);
            c.setUpdatedBy(updatedBy);
            mapper.insert(c);
        } else {
            c.setConfigValue(value);
            c.setUpdatedBy(updatedBy);
            mapper.updateById(c);
        }
        try { redis.delete(CACHE_PREFIX + key); } catch (Exception ignore) { }
    }

    /** 登录滑块验证模式(默认 ENABLED,脏数据回退启用)。 */
    public CaptchaMode getCaptchaMode() {
        return CaptchaMode.fromCode(get(KEY_CAPTCHA_MODE, CaptchaMode.ENABLED.getCode()));
    }

    public void setCaptchaMode(CaptchaMode mode, Long updatedBy) {
        set(KEY_CAPTCHA_MODE, mode.getCode(), updatedBy);
    }

    /** 本地知识库 AI 问答总开关(默认开)。关后 /ask 立即拒绝、前端精灵提示"已被管理员禁用",
     *  用于按需停掉嵌入/LLM 计费调用防超支(与 captcha.mode 同一套 KV + 读穿缓存)。 */
    public boolean isKbEnabled() {
        return Boolean.parseBoolean(get(KEY_KB_ENABLED, "true"));
    }

    public void setKbEnabled(boolean enabled, Long updatedBy) {
        set(KEY_KB_ENABLED, String.valueOf(enabled), updatedBy);
    }

    /** 游客只读体验入口总开关(默认关——opt-in,与 kb.enabled 默认开相反)。
     *  关后 /auth/guest 拒绝、登录页不渲染游客按钮;开则放行(受 captcha.mode 同样的门槛与 IP 限流约束)。 */
    public boolean isGuestEnabled() {
        return Boolean.parseBoolean(get(KEY_GUEST_ENABLED, "false"));
    }

    public void setGuestEnabled(boolean enabled, Long updatedBy) {
        set(KEY_GUEST_ENABLED, String.valueOf(enabled), updatedBy);
    }
}
