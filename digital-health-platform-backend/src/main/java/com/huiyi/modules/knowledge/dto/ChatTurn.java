package com.huiyi.modules.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一轮对话(多轮记忆用):role=user/assistant,content=该轮文本。
 *
 * 前端 KbAssistant 发问时把「最近 5 轮」历史带上,后端拼进 LLM 的 messages,使追问(如"它有什么副作用")
 * 能结合上文消解指代,而非每问孤立。仅作上下文传递,不落库(kb_chat_log 已逐条记录每次问答)。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatTurn {
    private String role;      // user / assistant
    private String content;
}
