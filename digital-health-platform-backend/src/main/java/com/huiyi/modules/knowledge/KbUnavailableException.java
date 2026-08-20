package com.huiyi.modules.knowledge;

/**
 * 知识库 LLM 作答服务不可用(网络断连 / HTTP 非 2xx / 响应解析失败 / 额度或 key 失效等)。
 * <p>作答失败属"服务暂时不可用"的<b>异常</b>,与"本地 0 命中 → 通用知识回退"的<b>正常兜底</b>是两回事。
 * 抛出此异常让 {@link RagService} 走 error 分支(清晰提示重试),而非把异常文本
 * (如「SiliconFlow 调用异常:Connection reset」)当成答案、并错挂在「通用知识·非本平台数据」标签下展示。
 */
public class KbUnavailableException extends RuntimeException {

    public KbUnavailableException(String message) {
        super(message);
    }

    public KbUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
