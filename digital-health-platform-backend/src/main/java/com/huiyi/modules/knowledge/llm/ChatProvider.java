package com.huiyi.modules.knowledge.llm;

import com.huiyi.modules.knowledge.dto.ChatTurn;

import java.util.List;

/**
 * 大模型作答:把"用户问题 + 检索到的知识片段"组织成自然语言回答(RAG 的 G)。
 *
 * 抽成接口让 LLM 可插拔:框架默认 {@code TemplatedChatProvider}(不调任何模型,只把命中片段拼成"证据",
 * 用于在未接 LLM 时验证检索链路);接真实模型时实现一个 Bean(本地 Ollama qwen / DashScope / OpenAI 兼容接口)覆盖即可。
 */
public interface ChatProvider {

    /**
     * 基于检索到的知识片段作答。
     *
     * @param query     用户当前问题
     * @param passages  命中的知识片段文本(已按相关度降序)
     * @param history   最近若干轮对话(多轮记忆,使追问能结合上文消解指代;可空)
     * @return 自然语言回答
     */
    String answer(String query, List<String> passages, List<ChatTurn> history);

    /**
     * 本地知识库 0 命中时的回退作答(双能力):用模型自身通用知识回答,不依赖本地资料。
     * 调用方(RagService)负责在结果上标注「通用知识,非本平台数据」。
     *
     * @param query   用户当前问题
     * @param history 最近若干轮对话(多轮记忆;可空)
     * @return 基于通用知识的回答
     */
    String answerGeneral(String query, List<ChatTurn> history);

    default String name() {
        return "unknown";
    }
}
