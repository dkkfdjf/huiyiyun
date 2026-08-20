package com.huiyi.modules.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.dashboard.mapper.DashboardMapper;
import com.huiyi.modules.dashboard.vo.ScaleVO;
import com.huiyi.modules.knowledge.dto.ChatTurn;
import com.huiyi.modules.knowledge.llm.ChatProvider;
import com.huiyi.modules.knowledge.model.Retrieval;
import com.huiyi.modules.knowledge.vo.KbAnswerVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 检索增强生成(RAG)的 G:把检索命中的片段喂给 LLM 作答,并连同可溯源的来源一起返回。
 *
 * 返回来源片段(不止给答案)是有意为之——让答案可溯源、可核对,抑制"一本正经地胡说"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final KnowledgeService knowledgeService;
    private final ChatProvider chatProvider;
    private final KbScopeResolver scopeResolver;
    private final ObjectMapper objectMapper;
    private final DashboardMapper dashboardMapper;

    /** 计数/规模类问题关键词:命中则把平台实时规模注入资料,避免用文档里陈旧的旧计数(如"5个网点")作答。 */
    private static final Pattern COUNT_Q = Pattern.compile("多少|几个|几[家位名人种间]|数量|总数|共计|共有|一共|统计|规模|数目");

    public KbAnswerVO ask(String query, List<ChatTurn> history, int topK) {
        CurrentUser u = SecurityContextHolder.get();
        Integer role = u == null ? null : u.getRole();
        Set<String> scopes = scopeResolver.visibleScopes(u);
        List<Retrieval> hits = knowledgeService.search(query, topK, scopes);

        // 把「可跳转目的地目录」追加进给 LLM 的提问(让 LLM 自定动作、但只能从合法路由选);
        // 日志记的是原始 query(controller 侧 dto.getQuery()),不被这段指令污染。
        String qForLlm = query + KbActionParser.promptCatalog(role);

        // 计数/规模类问题:拉取平台实时规模(公开数据,见 /public/stats)作为权威依据注入。
        // 知识库只从"已导入文档"作答,文档里的计数会陈旧(如仍写"5个网点"),导致"有多少个网点"答出与真实不符的数字。
        // 命中计数关键词时,把实时规模塞进资料前置位,标"以此为准",优先于旧文档计数。
        ScaleVO scale = (query != null && COUNT_Q.matcher(query).find()) ? dashboardMapper.scaleStats() : null;
        String liveScale = null;
        if (scale != null) {
            liveScale = "【平台实时规模·系统注入·最新真实数据,计数以此为准(优先于其他资料里的旧数字),作答时无需为本条标注来源序号】"
                    + "销售网点 " + scale.getLocationCount() + " 家、合作医疗机构 " + scale.getInstitutionCount()
                    + " 家、在册医师 " + scale.getDoctorCount() + " 人、在管药品 " + scale.getDrugCount() + " 种。";
        }

        // 多轮历史瘦身:只取最近 3 轮、每条截断 300 字,防止历史膨胀拖慢 DeepSeek-V3 多轮作答(第 2 问起的"等很久")
        List<ChatTurn> hist = trimHistory(history);

        KbAnswerVO vo = new KbAnswerVO();
        boolean hasLocal = !hits.isEmpty();

        try {
            if (!hasLocal && liveScale == null) {
                // 双能力:本地 0 命中、且非计数问题 → 回退到模型通用知识(mode=general,前端显「通用知识·非本平台数据」标识)。
                vo.setAnswer(chatProvider.answerGeneral(qForLlm, hist));
                vo.setSources(new ArrayList<>());
                vo.setMode("general");
                log.info("kb ask q='{}' hits=0 -> general-knowledge fallback chat={}", abbrev(query, 40), chatProvider.name());
            } else {
                // 有本地命中,或计数问题(注入实时规模):严格按资料作答——仅依据资料、无依据说明、标来源。
                List<String> passages = new ArrayList<>();
                if (liveScale != null) passages.add(liveScale);   // 实时规模前置为资料[1],优先于旧文档
                for (Retrieval r : hits) {
                    passages.add(r.getChunk().getText());
                }
                vo.setAnswer(chatProvider.answer(qForLlm, passages, hist));
                List<KbAnswerVO.Source> sources = new ArrayList<>(hits.size());
                for (Retrieval r : hits) {
                    KbAnswerVO.Source s = new KbAnswerVO.Source();
                    s.setDocId(r.getChunk().getDocId());
                    s.setTitle(parseTitle(r.getChunk().getMetadata()));
                    s.setOrdinal(r.getChunk().getOrdinal());
                    s.setSnippet(truncate(r.getChunk().getText(), 120));
                    s.setScore(r.getScore());
                    sources.add(s);
                }
                vo.setSources(sources);
                vo.setMode("local");
                log.info("kb ask q='{}' hits={} liveScale={} chat={}", abbrev(query, 40), hits.size(), liveScale != null, chatProvider.name());
            }
        } catch (KbUnavailableException e) {
            // 作答服务不可用(网络断连 / HTTP 非 2xx / key 失效等)是异常,不是答案——
            // 不得把异常文本(如「Connection reset」)当成答案,更不能错挂在「通用知识·非本平台数据」标签下。
            // 清晰标 error + 友好提示重试,跳过拒答检测/动作解析(异常答案不参与)。
            log.warn("kb ask provider unavailable: {}", e.getMessage());
            vo.setAnswer("知识库服务暂时不可用,请稍后重试。");
            vo.setSources(new ArrayList<>());
            vo.setMode("error");
            return vo;
        }

        // 统一收口:若 LLM 判越界拒答(prompt 定死的拒绝话术),清掉松匹配来源、标 out_of_scope。
        // 避免"礼貌拒答却列 5 条来源"的矛盾显示——被清来源本就与答案无关;controller 侧日志据此记「越界提问」。
        if (KbRefusalDetector.isRefusal(vo.getAnswer())) {
            vo.setSources(new ArrayList<>());
            vo.setMode("out_of_scope");
        }

        // 解析 LLM 在回答末尾追加的 [[ACTION|...]] 标记 → 结构化动作(按角色白名单校验)+ 剔除正文标记
        KbActionParser.apply(vo, role, query);
        return vo;
    }

    @SuppressWarnings("unchecked")
    private String parseTitle(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return "";
        }
        try {
            Object t = objectMapper.readValue(metadataJson, Map.class).get("title");
            return t == null ? "" : t.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 多轮历史瘦身:取最近 3 轮、每条内容截断 300 字,减小喂给 LLM 的 prompt(防多轮作答越问越慢)。 */
    private static List<ChatTurn> trimHistory(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) return List.of();
        int from = Math.max(0, history.size() - 3);
        List<ChatTurn> out = new ArrayList<>(history.size() - from);
        for (int i = from; i < history.size(); i++) {
            ChatTurn t = history.get(i);
            if (t == null || t.getRole() == null || t.getContent() == null) continue;
            String c = t.getContent();
            if (c.length() > 300) c = c.substring(0, 300);
            ChatTurn nt = new ChatTurn();
            nt.setRole(t.getRole());
            nt.setContent(c);
            out.add(nt);
        }
        return out;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String abbrev(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
