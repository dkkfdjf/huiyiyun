package com.huiyi.modules.log;

import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.system.entity.ErrorLog;
import com.huiyi.modules.system.mapper.ErrorLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

/**
 * 错误日志写入(设计表16 error_log):捕获未处理异常的上下文入库,供管理员排查。
 * 调用方:GlobalExceptionHandler 兜底分支。append-only,30 天清理见 LogCleanupService。
 * SecurityContextHolder 在 @ExceptionHandler 执行时尚未被 JwtAuthFilter 清空(filter 的 finally
 * 在 chain.doFilter 返回后才执行),故此处可读到当前用户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorLogService {

    private final ErrorLogMapper errorLogMapper;

    private static final int MSG_MAX = 1900;     // error_message VARCHAR(2000)
    private static final int PARAM_MAX = 2000;
    private static final int STACK_MAX = 4000;   // stack_trace TEXT,截断防爆库

    public void record(Throwable e) {
        try {
            ErrorLog l = new ErrorLog();
            l.setErrorTime(LocalDateTime.now());
            CurrentUser u = SecurityContextHolder.get();
            if (u != null) l.setUserId(u.getUserId());
            HttpServletRequest req = currentRequest();
            if (req != null) {
                l.setRequestUrl(truncate(req.getRequestURI(), 500));
                l.setRequestParam(truncate(req.getQueryString(), PARAM_MAX));
                l.setIp(clientIp(req));
            }
            l.setErrorType(truncate(e.getClass().getName(), 100));
            l.setErrorMessage(truncate(e.getMessage(), MSG_MAX));
            l.setStackTrace(truncate(stack(e), STACK_MAX));
            errorLogMapper.insert(l);
        } catch (Exception ex) {
            // 写日志失败不得影响异常处理主流程
            log.warn("写 error_log 失败: {}", ex.getMessage());
        }
    }

    private HttpServletRequest currentRequest() {
        Object attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attrs).getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest r) {
        String x = r.getHeader("X-Forwarded-For");
        return (x == null || x.isEmpty()) ? r.getRemoteAddr() : x.split(",")[0].trim();
    }

    private String stack(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
