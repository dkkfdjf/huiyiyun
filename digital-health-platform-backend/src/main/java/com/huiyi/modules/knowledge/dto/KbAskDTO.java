package com.huiyi.modules.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/** 知识库提问请求。topK 控制召回片段数,默认 5;history 为最近若干轮对话(多轮记忆,可空)。 */
@Data
public class KbAskDTO {
    @NotBlank(message = "问题不能为空")
    private String query;

    private Integer topK = 5;

    /** 最近 5 轮对话历史(前端 KbAssistant 传入,使追问能结合上文;首次提问为空)。 */
    private List<ChatTurn> history;
}
