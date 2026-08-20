package com.huiyi.common.aspect;

import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.system.mapper.OperationLogMapper;
// 注意:本类同包有注解 com.huiyi.common.aspect.OperationLog(@annotation 绑定用),
// 实体 com.huiyi.modules.system.entity.OperationLog 与之同名,故此处不 import 实体,
// 改在 logAsync 内用全限定名引用实体,避免名字冲突。
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogMapper operationLogMapper;

    @Around("@annotation(ol)")
    public Object around(ProceedingJoinPoint pjp, OperationLog ol) throws Throwable {
        long start = System.currentTimeMillis();
        // 进入时快照当前用户:登出等方法会在方法体内 clear 上下文,事后取不到 → 登出日志丢 username。
        // 登录则相反(进入时无用户、体内才 set),故 writeLog 用"快照优先,为空则事后取"兼顾两者。
        CurrentUser snapshot = SecurityContextHolder.get();
        try {
            return pjp.proceed();
        } finally {
            // 同步写审计日志:@Async 经同类 this 调用会绕过 Spring 代理(自调用不生效),
            // 且即便真正异步,子线程也拿不到 ThreadLocal 的登录上下文。审计 insert 仅一次、
            // 耗时极短(毫秒级),同步写既可靠又保证 username/userId 不丢,优于虚假的 @Async。
            try {
                writeLog(ol, System.currentTimeMillis() - start, snapshot, pjp.getArgs());
            } catch (Exception e) {
                log.warn("write operation_log failed: {}", e.getMessage());
            } finally {
                AuditDetail.clear();   // 业务经 AuditDetail.set 注入的详情用完即清,防线程复用泄漏
            }
        }
    }

    private void writeLog(OperationLog ol, long costMs, CurrentUser snapshot, Object[] args) {
        com.huiyi.modules.system.entity.OperationLog l = new com.huiyi.modules.system.entity.OperationLog();
        // 快照优先(登出:体内已 clear);为空则事后取(登录:体内才 set)。两者都能正确落到 username/userId。
        CurrentUser u = snapshot != null ? snapshot : SecurityContextHolder.get();
        if (u != null) {
            l.setUserId(u.getUserId());
            l.setUsername(u.getUsername());
        } else {
            // 登录失败等场景:进入时无用户(login 在 JWT 白名单,过滤器不 set)、体内又抛异常没 set
            // (密码错/锁定/验证码失败/限流),快照与事后取都为空。从方法入参里捞 username(如 LoginDTO.getUsername()),
            // 保证"失败的登录尝试"也能记到是谁——补齐 operation_log「登录」行此前恒空的 username。
            String name = pickUsername(args);
            if (name != null) l.setUsername(name);
        }
        l.setModule(ol.module());
        l.setOperation(ol.operation());
        l.setCostTime(costMs);
        l.setOperationTime(LocalDateTime.now());
        // 通用:记请求方法 + URL + 来源 IP(此前 method/request_url 恒空,补上后审计更完整)
        HttpServletRequest req = currentRequest();
        if (req != null) {
            l.setMethod(req.getMethod());
            l.setRequestUrl(truncate(req.getRequestURI(), 255));
            l.setIp(clientIp(req));
        }
        l.setSensitive(isSensitive(ol.module(), ol.operation()) ? 1 : 0);   // 非侵入式自动判定敏感
        // 额外审计详情(如知识库提问内容):业务经 AuditDetail.set 注入 → 写 request_param,使该行在审计日志可读
        String detail = AuditDetail.get();
        if (detail != null && !detail.isBlank()) {
            l.setRequestParam(truncate(detail, 1000));
        }
        operationLogMapper.insert(l);
    }

    /** 从方法入参里提取 username:扫描带 getUsername() 的参数(如 LoginDTO),用于登录失败时仍能记录"谁"。
     *  仅在上下文无用户时调用(极少数路径,基本只有失败的登录),反射开销可忽略;入参无 getUsername() 则跳过。 */
    private static String pickUsername(Object[] args) {
        if (args == null) return null;
        for (Object a : args) {
            if (a == null) continue;
            try {
                Object v = a.getClass().getMethod("getUsername").invoke(a);
                if (v instanceof String s && !s.isBlank()) return s.trim();
            } catch (Exception ignore) { /* 该入参无 getUsername(),跳过 */ }
        }
        return null;
    }

    /** 敏感判定(非侵入式:仅依据注解自带的 module/operation,不改任何业务代码)。
     *  命中敏感表「用户」的写操作,或操作名含 删除/停用/重置/密码/权限 等关键词 → 敏感。 */
    // 模块「认证」(登录/登出)属常规行为,不入敏感表;敏感认证类操作(改密/解锁等)由 SENSITIVE_OPS 兜住
    private static final java.util.Set<String> SENSITIVE_TABLES = java.util.Set.of("用户");
    private static final String[] SENSITIVE_OPS = {"删除", "停用", "禁用", "重置", "密码", "权限", "角色", "解锁", "锁定", "驳回", "审核"};
    private static boolean isSensitive(String module, String operation) {
        if (module != null && SENSITIVE_TABLES.contains(module)) return true;   // 敏感表的写操作
        if (operation == null) return false;
        for (String k : SENSITIVE_OPS) if (operation.contains(k)) return true;
        return false;
    }

    /** 取当前 HTTP 请求(无请求上下文如异步线程则返回 null)。 */
    private HttpServletRequest currentRequest() {
        try {
            Object attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes)) return null;
            return ((ServletRequestAttributes) attrs).getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    /** 取客户端 IP(穿透代理 X-Forwarded-For 取首段)。 */
    private String clientIp(HttpServletRequest r) {
        if (r == null) return null;
        String x = r.getHeader("X-Forwarded-For");
        return (x == null || x.isEmpty()) ? r.getRemoteAddr() : x.split(",")[0].trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
