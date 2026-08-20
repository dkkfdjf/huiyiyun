package com.huiyi.modules.knowledge;

import com.huiyi.common.aspect.AuditDetail;
import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.R;
import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.knowledge.dto.KbAskDTO;
import com.huiyi.modules.knowledge.dto.KbIngestDTO;
import com.huiyi.modules.knowledge.model.KbChatLog;
import com.huiyi.modules.knowledge.model.KnowledgeDocument;
import com.huiyi.modules.knowledge.vo.KbAnswerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 本地知识库 AI 问答(RAG,管理员)。
 *
 * /api/v1/kb/** 受 JWT 保护;除 /ask(所有登录角色,按 scope 行级隔离)外,其余端点 @RequiresRole(ADMIN)。
 * 链路:导入(切块+嵌入+入向量库)→ 提问(向量检索 + LLM 作答,带可溯源来源)。
 * 三大可插拔件(向量库 / 嵌入模型 / LLM)见对应接口注释,默认为零依赖 stub。
 */
@Tag(name = "知识库(本地 RAG)")
@RestController
@RequestMapping("/api/v1/kb")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final RagService ragService;
    private final KnowledgeSeeder knowledgeSeeder;
    private final com.huiyi.modules.system.SystemConfigService systemConfigService;
    private final KbChatLogService chatLogService;

    @Operation(summary = "导入文本知识(管理员)")
    @OperationLog(module = "知识库", operation = "导入知识文档")
    @PostMapping("/ingest")
    @RequiresRole(RoleConstants.ADMIN)
    public R<Map<String, Object>> ingest(@RequestBody @Valid KbIngestDTO dto) {
        Long id = knowledgeService.ingest(dto, SecurityContextHolder.get().getUserId());
        return R.ok(Map.of("id", id));
    }

    @Operation(summary = "提问(RAG 检索 + 作答;所有登录角色,按 scope 行级隔离)")
    @OperationLog(module = "知识库", operation = "问答")
    @PostMapping("/ask")
    public R<KbAnswerVO> ask(@RequestBody @Valid KbAskDTO dto, HttpServletRequest req) {
        // 总开关关闭 → 拒绝(挡前端绕过 + 关停计费兜底);但管理员豁免,便于关停后仍可测试/维护。
        CurrentUser u = SecurityContextHolder.get();
        boolean admin = u != null && u.getRole() != null && u.getRole() == RoleConstants.ADMIN;
        if (!systemConfigService.isKbEnabled() && !admin) {
            throw new com.huiyi.common.result.BusinessException(
                    com.huiyi.common.result.ResultCode.FORBIDDEN.getCode(), "本地知识库问答已被管理员关闭");
        }
        int topK = dto.getTopK() == null ? 5 : dto.getTopK();

        // 无效提问硬拦截:在调嵌入 + LLM 之前直接返回,省 API key(防「故意浪费 key 的无效对话」)。
        // 仍落对话日志(flag_reason=无效提问,可叠加高频/重复),供管理员审计谁在刷。
        if (KbQueryGuard.isInvalid(dto.getQuery())) {
            KbAnswerVO rejected = new KbAnswerVO();
            rejected.setAnswer("该提问缺少有效内容,已跳过知识库检索。请描述具体的药品、政策、必备材料或业务问题。");
            rejected.setSources(new ArrayList<>());
            rejected.setMode("rejected");
            chatLogService.record(u, dto.getQuery(), rejected.getAnswer(), 0, clientIp(req));
            AuditDetail.set("无效提问(已拦截,未调 LLM):" + qBrief(dto.getQuery()));   // 进审计日志 request_param
            return R.ok(rejected);
        }

        // 游客高频硬拦:游客(GUEST)同 IP 10 分钟内提问过频 → 拒绝作答(省 embedding/LLM 计费)。
        // 已登录用户高频只打标记(体验优先);游客匿名、易被脚本滥用,故硬拦。仍落日志标"高频"供管理员审计。
        // isHighFrequency 按已落库记录计数(本次尚未记),故窗口阈值(>10)从第 11 次起拦。
        if (u != null && u.getRole() != null && u.getRole() == RoleConstants.GUEST
                && chatLogService.isHighFrequency(null, clientIp(req))) {
            KbAnswerVO rejected = new KbAnswerVO();
            rejected.setAnswer("您的提问过于频繁,请稍后再试。游客体验有提问频率限制,登录账号可享受完整知识库服务。");
            rejected.setSources(new ArrayList<>());
            rejected.setMode("rate_limited");
            chatLogService.record(u, dto.getQuery(), rejected.getAnswer(), 0, clientIp(req));
            AuditDetail.set("游客高频拦截(未调 LLM):" + qBrief(dto.getQuery()));   // 进审计日志 request_param
            return R.ok(rejected);
        }

        KbAnswerVO ans = ragService.ask(dto.getQuery(), dto.getHistory(), topK);
        // 落对话日志(管理员审计 + 异常/滥用检测);内部 try/catch,失败不影响作答
        int hits = ans.getSources() == null ? 0 : ans.getSources().size();
        chatLogService.record(u, dto.getQuery(), ans.getAnswer(), hits, clientIp(req));
        AuditDetail.set("提问:" + qBrief(dto.getQuery()) + " | 命中来源:" + hits);   // 进审计日志 request_param
        return R.ok(ans);
    }

    /** 审计详情用的提问摘要(截断,免 operation_log.request_param 过长)。 */
    private static String qBrief(String q) {
        if (q == null) return "";
        return q.length() <= 120 ? q : q.substring(0, 120) + "…";
    }

    @Operation(summary = "知识文档列表(管理员)")
    @GetMapping("/docs")
    @RequiresRole(RoleConstants.ADMIN)
    public R<List<KnowledgeDocument>> docs() {
        return R.ok(knowledgeService.listDocs());
    }

    @Operation(summary = "删除知识文档(连同其切块与向量,管理员)")
    @OperationLog(module = "知识库", operation = "删除知识文档")
    @DeleteMapping("/docs/{id}")
    @RequiresRole(RoleConstants.ADMIN)
    public R<Void> delete(@PathVariable Long id) {
        knowledgeService.deleteDoc(id);
        return R.ok();
    }

    @Operation(summary = "从业务数据重建知识库(管理员;清旧业务文档后逐表灌库,带行级 scope)")
    @OperationLog(module = "知识库", operation = "从业务数据重建")
    @PostMapping("/rebuild")
    @RequiresRole(RoleConstants.ADMIN)
    public R<Map<String, Object>> rebuild() {
        return R.ok(knowledgeSeeder.rebuild());
    }

    @Operation(summary = "对话日志分页(管理员审计;登录名 / 异常标记 / 时间区间)")
    @GetMapping("/chat-logs")
    @RequiresRole(RoleConstants.ADMIN)
    public R<PageResult<KbChatLog>> chatLogs(@RequestParam(required = false) String username,
                                             @RequestParam(required = false) Integer flagged,
                                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
                                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end,
                                             @RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(chatLogService.page(username, flagged, start, end, pageNum, pageSize));
    }

    /** 取客户端 IP(nginx 反代后取 X-Forwarded-For 首段;与 AuthController 同源)。游客无 user_id,对话日志按此 IP 计频/审计。 */
    private String clientIp(HttpServletRequest r) {
        String x = r.getHeader("X-Forwarded-For");
        return (x == null || x.isEmpty()) ? r.getRemoteAddr() : x.split(",")[0].trim();
    }
}
