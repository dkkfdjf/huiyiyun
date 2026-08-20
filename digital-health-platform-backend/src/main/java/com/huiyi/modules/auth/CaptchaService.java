package com.huiyi.modules.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 图形验证码(设计 §6.3 / §9.3):生成 4 位码 → 画 PNG → 存 Redis(key + TTL);校验一次性(读后即删)。
 * dev 环境(非 prod profile)支持万能码 huiyi.captcha.universal-code 放行,prod profile 下万能码自动失效。
 * 注:本服务只提供能力;登录是否强制校验由 LoginService 决定(当前策略:请求带验证码才校验,
 *    不带则沿用前端滑块作人机校验,见 S3 方案)。
 */
@Slf4j
@Service
public class CaptchaService {

    private static final String KEY_PREFIX = "huiyi:captcha:";
    private static final long TTL_SECONDS = 300;               // 5 分钟
    private static final int WIDTH = 120, HEIGHT = 40, LEN = 4;
    private static final char[] CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();   // 去掉易混 0/O/1/I
    private static final Color[] COLORS = {
            new Color(67, 56, 202), new Color(5, 150, 105), new Color(217, 119, 6),
            new Color(220, 38, 38), new Color(111, 40, 167)
    };

    private final StringRedisTemplate redis;
    private final String universalCode;
    private final boolean universalEnabled;                     // 非 prod 才启用

    public CaptchaService(StringRedisTemplate redis,
                          @Value("${huiyi.captcha.universal-code:}") String universalCode,
                          Environment env) {
        this.redis = redis;
        this.universalCode = universalCode == null ? "" : universalCode.trim();
        this.universalEnabled = !env.acceptsProfiles(Profiles.of("prod"));
    }

    /** 生成验证码,返回 key + base64 图片。 */
    public CaptchaVO generate() {
        String bare = UUID.randomUUID().toString().replace("-", "");
        String code = randomCode();
        redis.opsForValue().set(KEY_PREFIX + bare, code, Duration.ofSeconds(TTL_SECONDS));
        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaKey(bare);
        vo.setImg("data:image/png;base64," + renderBase64(code));
        return vo;
    }

    /**
     * 校验:dev 万能码(prod 禁用)命中即放行;否则按 key 取 Redis 码比对,读后即删(一次性)。
     * submitted 为空返回 false(调用方据此决定「未带验证码则跳过」)。
     */
    public boolean verify(String captchaKey, String submitted) {
        if (submitted == null || submitted.isBlank()) return false;
        String s = submitted.trim();
        if (universalEnabled && !universalCode.isEmpty() && universalCode.equalsIgnoreCase(s)) return true;
        if (captchaKey == null || captchaKey.isBlank()) return false;
        String fullKey = KEY_PREFIX + captchaKey.trim();
        String stored = redis.opsForValue().get(fullKey);
        redis.delete(fullKey);   // 一次性:无论对错都作废
        return stored != null && stored.equalsIgnoreCase(s);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(LEN);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < LEN; i++) sb.append(CHARS[r.nextInt(CHARS.length)]);
        return sb.toString();
    }

    /** 画 PNG → base64。纯 JDK,无新依赖。 */
    private String renderBase64(String code) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < 6; i++) {   // 干扰线
                g.setColor(new Color(r.nextInt(180, 240), r.nextInt(180, 240), r.nextInt(180, 240)));
                g.drawLine(r.nextInt(WIDTH), r.nextInt(HEIGHT), r.nextInt(WIDTH), r.nextInt(HEIGHT));
            }
            g.setFont(new Font("Arial", Font.BOLD, 26));
            for (int i = 0; i < code.length(); i++) {
                g.setColor(COLORS[r.nextInt(COLORS.length)]);
                g.drawString(String.valueOf(code.charAt(i)), 10 + i * 26, 30);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            log.warn("captcha render failed", e);
            return "";
        } finally {
            g.dispose();
        }
    }
}
