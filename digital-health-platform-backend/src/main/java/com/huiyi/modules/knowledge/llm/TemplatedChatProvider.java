package com.huiyi.modules.knowledge.llm;

import com.huiyi.modules.knowledge.dto.ChatTurn;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认作答实现:不调任何大模型,只把检索到的命中片段拼成"证据"返回。
 *
 * 存在的目的:在**未接真实 LLM** 时也能端到端验证 RAG 链路——能看到检索确实命中了相关片段、
 * 召回顺序是否合理。空检索时给明确提示(优于硬编一句假回答,防误导)。
 *
 * 选型靠 huiyi.kb.chat.impl:templated(本默认,matchIfMissing)/ siliconflow(DeepSeek-V3)。业务层零改动。
 * 多轮记忆(history)在本 stub 下不参与拼接(无模型可用),仅满足接口签名;接 LLM 的 SiliconFlow 实现才真正消费。
 */
@Component
@ConditionalOnProperty(prefix = "huiyi.kb.chat", name = "impl", havingValue = "templated", matchIfMissing = true)
public class TemplatedChatProvider implements ChatProvider {

    @Override
    public String answer(String query, List<String> passages, List<ChatTurn> history) {
        if (passages == null || passages.isEmpty()) {
            return "知识库中未检索到与该问题相关的内容。请先导入相关文档,或换一种问法再试。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("（未接入大模型,以下为按相关度排序的检索片段;接入 LLM 后,此处将基于这些片段生成自然语言回答）\n\n");
        sb.append("问题:").append(query).append("\n\n");
        for (int i = 0; i < passages.size(); i++) {
            sb.append("【片段").append(i + 1).append("】").append(passages.get(i)).append("\n\n");
        }
        return sb.toString().trim();
    }

    @Override
    public String name() {
        return "templated-no-llm";
    }

    /** 双能力回退的 stub:未接 LLM 无法提供通用知识,给出明确提示而非假回答。 */
    @Override
    public String answerGeneral(String query, List<ChatTurn> history) {
        return "(未接入大模型,无法提供通用知识作答;接入 LLM 后,本地知识库未命中时将自动回退到模型通用知识。) 问题:" + query;
    }
}
