package com.huiyi.modules.system;

import com.huiyi.modules.system.vo.CaptchaVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 阿里云滑块验证码「真实调用次数」计数(管理员监控页估费用)。
 *
 * 计数点 = {@code AliyunCaptchaService.verify()} 收到非空 captchaVerifyParam(即将真正打阿里云接口)时
 * 调 {@link #increment()}。旧实现用 login_log 行数近似"每次登录≈1 次滑块",在三态门控后已失真:
 * pass/lock/凭据错误/账号锁定 都会写 login_log,但其中 0 次阿里云调用 → 严重高估费用。
 *
 * 用 Redis 计数:total 持久 key + 按日/月分桶 key(带过期,自动清理)。全部最佳努力——Redis 抖动只记 warn,
 * 绝不影响登录(verify 的计数失败被吞)。
 *
 * 注:Redis 重启会让 total 清零(课设费用估算可接受)。如需持久化,把 {@link #increment()} 换成写一行
 * captcha_call_log 表即可,计数点已集中在此,改动面小。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaMetrics {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MON = DateTimeFormatter.ofPattern("yyyyMM");

    private static final String K_TOTAL = "huiyi:captcha:calls:total";
    private static final String K_DAY = "huiyi:captcha:calls:day:";
    private static final String K_MON = "huiyi:captcha:calls:mon:";

    private final StringRedisTemplate redis;

    /** 一次真实阿里云验真调用:today / month / total 各 +1。最佳努力,不抛(登录不受影响)。 */
    public void increment() {
        LocalDate now = LocalDate.now();
        String dk = K_DAY + now.format(DAY);
        String mk = K_MON + now.format(MON);
        try {
            redis.opsForValue().increment(K_TOTAL);
            redis.opsForValue().increment(dk);
            redis.expire(dk, 35, TimeUnit.DAYS);     // 日桶留一个多月
            redis.opsForValue().increment(mk);
            redis.expire(mk, 400, TimeUnit.DAYS);    // 月桶留逾一年
        } catch (Exception e) {
            log.warn("captcha metrics increment skipped (login unaffected): {}", e.getMessage());
        }
    }

    /** 今日 / 本月 / 累计 调用快照。Redis 不可用返回 0,不抛。 */
    public CaptchaVO snapshot() {
        LocalDate now = LocalDate.now();
        CaptchaVO c = new CaptchaVO();
        c.setToday(readLong(K_DAY + now.format(DAY)));
        c.setMonth(readLong(K_MON + now.format(MON)));
        c.setTotal(readLong(K_TOTAL));
        return c;
    }

    private long readLong(String key) {
        try {
            String v = redis.opsForValue().get(key);
            return v == null ? 0 : Long.parseLong(v);
        } catch (Exception e) {
            log.warn("captcha metrics read 0, key={} : {}", key, e.getMessage());
            return 0;
        }
    }
}
