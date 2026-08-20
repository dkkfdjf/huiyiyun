package com.huiyi.common.aspect;

/**
 * 向 {@link OperationLogAspect} 传递「本次操作的额外审计详情」(如知识库提问内容),
 * 由切面写入 operation_log.request_param 列,使该操作在审计日志中可读、可溯源。
 *
 * <p>用法:业务方法体内 {@link #set} 注入详情;切面在写完日志后 {@link #clear}。
 * 未 set 时切面行为不变(其他端点零侵入)。
 *
 * <p>线程本地:同线程内 set → 切面 finally 读后 clear,不跨请求泄漏(tomcat 线程池复用安全)。
 */
public final class AuditDetail {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private AuditDetail() {}

    /** 注入本次操作的审计详情(如「提问:阿莫西林用量 | 命中来源:3」)。null/空等价于不注入。 */
    public static void set(String detail) {
        HOLDER.set(detail);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
