package com.huiyi.modules.knowledge.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 提问返回:自然语言回答 + 命中的来源片段(可溯源,防幻觉)。 */
@Data
public class KbAnswerVO {
    private String answer;
    private List<Source> sources;
    private String mode;   // local=本地资料作答 / general=本地0命中,回退通用知识(前端据此显「通用知识」标识)
    private List<Action> actions;   // 可交互动作(LLM 自定跳转;后端按角色白名单校验,前端渲染可点按钮)

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Source {
        private Long docId;
        private String title;
        private Integer ordinal;
        private String snippet;   // 命中片段(截断)
        private Double score;     // 相似度
    }

    /** 可交互动作:把 AI 回答升级为可点跳转(如「查看药品目录」→ /company/drugs)。target 已过角色白名单。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Action {
        private String label;   // 按钮文字
        private String target;  // 前端路由(已按当前角色白名单校验)
    }
}
