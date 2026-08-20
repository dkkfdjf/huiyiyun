package com.huiyi.modules.auth;

import com.huiyi.modules.system.CaptchaMetrics;
import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.service.captcha20230305.AsyncClient;
import com.aliyun.sdk.service.captcha20230305.models.VerifyIntelligentCaptchaRequest;
import com.aliyun.sdk.service.captcha20230305.models.VerifyIntelligentCaptchaResponse;
import com.aliyun.sdk.service.captcha20230305.models.VerifyIntelligentCaptchaResponseBody;
import darabonba.core.client.ClientOverrideConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 阿里云验证码 2.0 服务端二次校验(官方 SDK:com.aliyun:alibabacloud-captcha20230305 V2 异步版)。
 *
 * 前端滑块通过后拿到 captchaVerifyParam,原样透传到这里,调阿里云 VerifyIntelligentCaptcha 验真。
 * 用官方 SDK 内置的 ACS3-HMAC-SHA256 签名参考实现 —— 之前自实现签名时 CredentialScope 等细节
 * 无法离线确证,签名被阿里云拒,校验恒走 fail-open,控制台「使用量」不计。换 SDK 后签名正确,
 * VerifyIntelligentCaptcha 真正命中,使用量随之累加。
 *
 * V2 SDK 为异步(返回 CompletableFuture),本处仅一次同步校验,故 fut.get(timeout) 阻塞取结果。
 * region 用于 ACS3 签名凭证域(本场景 cn-shanghai);endpointOverride 锁定主机,不依赖 serviceName 推导。
 *
 * 安全策略:验证服务异常/超时/HTTP 非 2xx 时按 fail-open 默认放行(阿里云官方建议,避免云抖动锁死登录),
 * 并记 warn 日志便于排查。课设严格调试可设 huiyi.aliyun.captcha.fail-open=false。
 * Client 凭据启动后不变,故惰性构建一次缓存(volatile + DCL)。
 */
@Slf4j
@Service
public class AliyunCaptchaService {

    @Value("${huiyi.aliyun.captcha.access-key-id}") private String accessKeyId;
    @Value("${huiyi.aliyun.captcha.access-key-secret}") private String accessKeySecret;
    @Value("${huiyi.aliyun.captcha.scene-id:}") private String sceneId;
    @Value("${huiyi.aliyun.captcha.region:cn-shanghai}") private String region;
    @Value("${huiyi.aliyun.captcha.endpoint:captcha.cn-shanghai.aliyuncs.com}") private String endpoint;
    @Value("${huiyi.aliyun.captcha.fail-open:true}") private boolean failOpen;

    private volatile AsyncClient client;

    /** 阿里云调用计数(费用估算):verify 真正打阿里云时 +1。字段注入——本类用 @Value 无构造器。 */
    @Autowired private CaptchaMetrics captchaMetrics;

    /**
     * 二次校验。param 为空 → false(没过滑块);非空调阿里云;异常 → failOpen 决定。
     */
    public boolean verify(String captchaVerifyParam) {
        if (captchaVerifyParam == null || captchaVerifyParam.isBlank()) return false;
        captchaMetrics.increment();   // 真实阿里云验真调用计数(费用估算);旧实现用 login_log 近似,三态门控后失真
        try {
            VerifyIntelligentCaptchaRequest req = VerifyIntelligentCaptchaRequest.builder()
                    .captchaVerifyParam(captchaVerifyParam)
                    .sceneId(sceneId)
                    .build();

            CompletableFuture<VerifyIntelligentCaptchaResponse> fut = client().verifyIntelligentCaptcha(req);
            VerifyIntelligentCaptchaResponse resp = fut.get(8, TimeUnit.SECONDS);   // 阻塞取结果,超时则抛→failOpen

            VerifyIntelligentCaptchaResponseBody body = resp.getBody();
            VerifyIntelligentCaptchaResponseBody.Result result = body == null ? null : body.getResult();
            boolean ok = result != null && Boolean.TRUE.equals(result.getVerifyResult());
            String code = result == null ? "" : result.getVerifyCode();
            log.info("aliyun captcha verify VerifyResult={} VerifyCode={} Success={}",
                    ok, code, body == null ? null : body.getSuccess());
            return ok;
        } catch (Exception e) {
            // fut.get 的超时/中断、SDK 抛的业务错(InvalidAccessKeyId/SignatureDoesNotMatch/...)都落这里
            log.warn("aliyun captcha verify error, fail-open={} : {}", failOpen, e.getMessage());
            return failOpen;
        }
    }

    /** 惰性构建官方 AsyncClient(凭据启动后不变);构造抛异常会被 verify() 的 catch 捕获 → failOpen。 */
    private AsyncClient client() {
        AsyncClient c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    StaticCredentialProvider provider = StaticCredentialProvider.create(
                            Credential.builder()
                                    .accessKeyId(accessKeyId)
                                    .accessKeySecret(accessKeySecret)
                                    .build());
                    ClientOverrideConfiguration override = ClientOverrideConfiguration.create()
                            .setEndpointOverride(endpoint)
                            .setConnectTimeout(Duration.ofSeconds(5))
                            .setResponseTimeout(Duration.ofSeconds(8));
                    c = AsyncClient.builder()
                            .region(region)
                            .credentialsProvider(provider)
                            .overrideConfiguration(override)
                            .build();
                    client = c;
                }
            }
        }
        return c;
    }
}
