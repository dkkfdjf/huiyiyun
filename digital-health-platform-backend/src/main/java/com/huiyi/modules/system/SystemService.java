package com.huiyi.modules.system;

import com.huiyi.modules.system.vo.CaptchaVO;
import com.huiyi.modules.system.vo.DbPoolVO;
import com.huiyi.modules.system.vo.HttpVO;
import com.huiyi.modules.system.vo.JvmVO;
import com.huiyi.modules.system.vo.RedisVO;
import com.huiyi.modules.system.vo.SystemMetricsVO;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;

/**
 * 系统运行指标采集(管理员监控页)。
 * - JVM:Runtime + ManagementFactory(堆内存/线程/运行时长/CPU/负载)
 * - DB:HikariDataSource.getHikariPoolMXBean()(活跃/空闲/总/等待/上限)
 * - Redis:StringRedisTemplate ping(连通性 + 往返延迟)
 * - HTTP:Micrometer http.server.requests(Actuator 自动采集):请求数/延迟/错误率
 */
@Service
@RequiredArgsConstructor
public class SystemService {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final CaptchaMetrics captchaMetrics;
    private final com.huiyi.modules.knowledge.store.VectorStore vectorStore;
    private final com.huiyi.modules.knowledge.embed.EmbeddingProvider embeddingProvider;
    private final com.huiyi.modules.knowledge.llm.ChatProvider chatProvider;

    public SystemMetricsVO metrics() {
        SystemMetricsVO v = new SystemMetricsVO();
        v.setJvm(jvm());
        v.setDb(dbPool());
        v.setRedis(redis());
        v.setHttp(http());
        v.setCaptcha(captcha());
        v.setKb(kb());
        return v;
    }

    private JvmVO jvm() {
        Runtime r = Runtime.getRuntime();
        long total = r.totalMemory();
        long free = r.freeMemory();
        ThreadMXBean tb = ManagementFactory.getThreadMXBean();
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        JvmVO j = new JvmVO();
        j.setHeapUsed(total - free);
        j.setHeapCommitted(total);
        j.setHeapMax(r.maxMemory());
        j.setThreads(tb.getThreadCount());
        j.setDaemonThreads(tb.getDaemonThreadCount());
        j.setUptimeMs(rb.getUptime());
        j.setCpuCores(os.getAvailableProcessors());
        j.setSystemLoad(os.getSystemLoadAverage());   // 1 分钟平均负载;Windows 通常返回 -1
        return j;
    }

    private DbPoolVO dbPool() {
        DbPoolVO d = new DbPoolVO();
        // Spring Boot 默认 HikariCP;连接池未预热时 getHikariPoolMXBean() 可能为 null,需判空
        if (dataSource instanceof HikariDataSource hs && hs.getHikariPoolMXBean() != null) {
            HikariPoolMXBean p = hs.getHikariPoolMXBean();
            d.setType("HikariCP");
            d.setActive(p.getActiveConnections());
            d.setIdle(p.getIdleConnections());
            d.setTotal(p.getTotalConnections());
            d.setWaiting(p.getThreadsAwaitingConnection());
            d.setMax(hs.getMaximumPoolSize());
        }
        return d;
    }

    private RedisVO redis() {
        RedisVO r = new RedisVO();
        long t0 = System.nanoTime();
        try (var conn = redisTemplate.getConnectionFactory().getConnection()) {
            r.setOnline("PONG".equalsIgnoreCase(conn.ping()));
        } catch (Exception e) {
            r.setOnline(false);
            r.setError(e.getMessage());
        }
        r.setLatencyMs((System.nanoTime() - t0) / 1_000_000);
        return r;
    }

    /**
     * HTTP 接口运行指标:取自 Micrometer 的 http.server.requests(Actuator 自动采集每个请求)。
     * 累计请求数 / 平均延迟 / 最大延迟 / 5xx(SERVER_ERROR)错误数与占比。刚启动无记录时返回全 0。
     */
    private HttpVO http() {
        HttpVO h = new HttpVO();
        Timer all = meterRegistry.find("http.server.requests").timer();
        if (all == null) return h;   // 尚无请求记录(刚启动)
        long total = all.count();
        h.setTotalRequests(total);
        h.setAvgLatencyMs(all.mean(TimeUnit.MILLISECONDS));
        h.setMaxLatencyMs(all.max(TimeUnit.MILLISECONDS));
        Timer errs = meterRegistry.find("http.server.requests").tag("outcome", "SERVER_ERROR").timer();
        long err = errs == null ? 0 : errs.count();
        h.setErrorCount(err);
        h.setErrorRate(total > 0 ? err * 100.0 / total : 0);
        return h;
    }

    /**
     * 阿里云智能验证(滑块)调用次数:取真实阿里云验真计数(AliyunCaptchaService.verify 处 increment),
     * 而非 login_log 行数——三态门控后二者已脱钩(pass/lock/凭据错误都写日志但 0 阿里云调用)。
     * 供管理员按真实调用估算阿里云验证码费用。返回 今日/本月/累计。
     */
    private CaptchaVO captcha() {
        // 真实阿里云验真调用计数(在 AliyunCaptchaService.verify() 处 increment),不再用 login_log 行数近似——
        // 三态门控后 login_log 行数已与阿里云调用次数脱钩(pass/lock/凭据错误都写日志但 0 阿里云调用,会严重高估费用)。
        return captchaMetrics.snapshot();
    }

    /** 本地知识库向量库占用:向量数取自内存索引(VectorStore.size),内存估算 = 数 × 维度 × 4 字节(float)。
     *  维度与模型随嵌入 provider 变(bge-m3=1024 / hashing=256),换 provider 须清库重建。 */
    private com.huiyi.modules.system.vo.KbVO kb() {
        com.huiyi.modules.system.vo.KbVO k = new com.huiyi.modules.system.vo.KbVO();
        int count = vectorStore.size();
        int dim = embeddingProvider.dimension();
        k.setVectorCount(count);
        k.setDimension(dim);
        k.setEstMemBytes((long) count * dim * 4L);
        k.setEmbeddingModel(embeddingProvider.name());
        k.setChatModel(chatProvider.name());
        return k;
    }
}
