package com.huiyi.modules.system;

/**
 * 登录滑块验证模式(系统配置 captcha.mode 的取值)。
 *
 * <ul>
 *   <li>ENABLED 启用(默认):前端滑块 + 后端阿里云二次校验(计费点)。</li>
 *   <li>PASS    关闭放行:不校验滑块,登录照常通过 —— 止损但保持可用,0 阿里云调用。</li>
 *   <li>LOCK    关闭锁定:任何账号都无法登录 —— 纯冻结止损,0 阿里云调用。</li>
 * </ul>
 * fromCode 解析失败时回退 ENABLED —— 不因脏数据/直接改库意外关掉验证或锁死全员登录。
 */
public enum CaptchaMode {
    ENABLED("enabled"),
    PASS("pass"),
    LOCK("lock");

    private final String code;
    CaptchaMode(String code) { this.code = code; }
    public String getCode() { return code; }

    public static CaptchaMode fromCode(String s) {
        if (s == null) return ENABLED;
        for (CaptchaMode m : values()) {
            if (m.code.equalsIgnoreCase(s.trim())) return m;
        }
        return ENABLED;
    }
}
