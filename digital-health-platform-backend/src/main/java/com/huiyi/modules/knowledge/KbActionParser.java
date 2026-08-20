package com.huiyi.modules.knowledge;

import com.huiyi.common.security.RoleConstants;
import com.huiyi.modules.knowledge.vo.KbAnswerVO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 LLM 回答里的「可交互动作」标记解析成结构化动作,供前端渲染成可点跳转。
 *
 * <p>约定:LLM 若认为回答涉及某个用户可能想打开的实体(药品/网点/机构/政策…),
 * 在回答<b>末尾</b>追加一行或多行 {@code [[ACTION|按钮文字|路由]]}。本类负责:
 * <ol>
 *   <li>{@link #promptCatalog(Integer)} —— 拼出当前角色的「可跳转目的地目录」喂给 LLM,
 *       让它只能从合法路由里选(目录即白名单来源);</li>
 *   <li>{@link #apply(KbAnswerVO, Integer)} —— 正则提取标记、从正文剔除,并按角色白名单
 *       校验 target,只保留合法动作(防 LLM 臆造路径 / 越权跳转)。</li>
 * </ol>
 *
 * <p>目录按角色分(admin/company/institution/doctor/guest 各一套,与前端 router 路径逐项对齐);
 * RagService.ask 取当前用户真实 role 传给 promptCatalog/apply,各角色只拿到本角色目录的动作(非管理员不会拿到 /admin/* )。
 */
public final class KbActionParser {

    private KbActionParser() {}

    // [[ACTION|label|/admin/locations]] —— label 不含 | 与 ];target 不含 ]
    private static final Pattern ACTION = Pattern.compile("\\[\\[ACTION\\|([^|\\]]+)\\|([^\\]]+)\\]\\]");

    /** 各角色可跳转目的地(目录):label → 路由。values() 即该角色的 target 白名单。 */
    private static final Map<Integer, Map<String, String>> CATALOG = new LinkedHashMap<>();
    static {
        Map<String, String> admin = new LinkedHashMap<>();
        admin.put("销售网点", "/admin/locations");
        admin.put("医疗机构", "/admin/institutions");
        admin.put("药企", "/admin/companies");
        admin.put("药品库存", "/admin/stocks");
        admin.put("必备材料", "/admin/materials");
        admin.put("政策公告", "/admin/policies");
        admin.put("临床反馈", "/admin/demands");
        admin.put("知识库管理", "/admin/kb");
        CATALOG.put(RoleConstants.ADMIN, admin);

        Map<String, String> company = new LinkedHashMap<>();
        company.put("药品目录", "/company/drugs");
        company.put("药品库存", "/company/stocks");
        company.put("进销存台账", "/company/inventory");
        company.put("销售网点", "/company/locations");
        company.put("临床反馈", "/company/demands");
        company.put("政策公告", "/company/policies");
        CATALOG.put(RoleConstants.COMPANY, company);

        Map<String, String> inst = new LinkedHashMap<>();
        inst.put("本院科室", "/institution/departments");
        inst.put("本院医师", "/institution/doctors");
        inst.put("临床反馈", "/institution/demands");
        inst.put("药品", "/institution/drugs");
        inst.put("政策公告", "/institution/policies");
        inst.put("必备材料", "/institution/materials");
        CATALOG.put(RoleConstants.INSTITUTION, inst);

        Map<String, String> doc = new LinkedHashMap<>();
        doc.put("临床反馈", "/doctor/demands");
        doc.put("政策公告", "/doctor/policies");
        doc.put("必备材料", "/doctor/materials");
        CATALOG.put(RoleConstants.DOCTOR, doc);

        // 游客只读目录(与前端 /guest 路由树逐项对齐):可体验的浏览目的地,不含任何写/敏感页(库存/营业额/反馈/知识库管理)。
        Map<String, String> guest = new LinkedHashMap<>();
        guest.put("药企", "/guest/companies");
        guest.put("医疗机构", "/guest/institutions");
        guest.put("科室", "/guest/departments");
        guest.put("医师", "/guest/doctors");
        guest.put("销售网点", "/guest/locations");
        guest.put("必备材料", "/guest/materials");
        guest.put("政策公告", "/guest/policies");
        CATALOG.put(RoleConstants.GUEST, guest);
    }

    /** 路由 → 主题词(出现在召回第 1 条来源标题里才算"回答确实讲了这个主题")。用于 apply() 主题过滤。 */
    private static final Map<String, String> ROUTE_TOPIC = Map.ofEntries(
            Map.entry("/admin/locations", "网点"),
            Map.entry("/admin/institutions", "机构"),
            Map.entry("/admin/companies", "药企"),
            Map.entry("/admin/stocks", "库存"),
            Map.entry("/admin/materials", "材料"),
            Map.entry("/admin/policies", "政策"),
            Map.entry("/admin/demands", "反馈"),
            Map.entry("/admin/kb", "知识库"),
            Map.entry("/company/drugs", "药品"),
            Map.entry("/company/stocks", "库存"),
            Map.entry("/company/inventory", "台账"),
            Map.entry("/company/locations", "网点"),
            Map.entry("/company/demands", "反馈"),
            Map.entry("/company/policies", "政策"),
            Map.entry("/institution/departments", "科室"),
            Map.entry("/institution/doctors", "医师"),
            Map.entry("/institution/demands", "反馈"),
            Map.entry("/institution/drugs", "药品"),
            Map.entry("/institution/policies", "政策"),
            Map.entry("/institution/materials", "材料"),
            Map.entry("/doctor/demands", "反馈"),
            Map.entry("/doctor/policies", "政策"),
            Map.entry("/doctor/materials", "材料"),
            Map.entry("/guest/companies", "药企"),
            Map.entry("/guest/institutions", "机构"),
            Map.entry("/guest/departments", "科室"),
            Map.entry("/guest/doctors", "医师"),
            Map.entry("/guest/locations", "网点"),
            Map.entry("/guest/materials", "材料"),
            Map.entry("/guest/policies", "政策"));

    /** 拼成 prompt 片段:告诉 LLM 可追加哪些动作(只能从合法路由选)。无目录的角色返回空串(不启用动作)。 */
    public static String promptCatalog(Integer role) {
        Map<String, String> cat = role == null ? null : CATALOG.get(role);
        if (cat == null || cat.isEmpty()) return "";
        StringBuilder sb = new StringBuilder()
                .append("\n\n若用户接下来可能想直接打开某个页面,可在回答【末尾】另起一行追加动作标记,")
                .append("格式严格为 [[ACTION|按钮文字|路由]]。必须遵守:\n")
                .append("  · 路由只能从下面「→」左侧逐字选一个,不得编造或改写;\n")
                .append("  · 按钮文字必须用对应「→」右侧的名称,不得自创(例如不能把「临床反馈」写成「查看企业」);\n")
                .append("  · 仅当回答主体确实属于下列页面之一时才追加;主体不在下表(如药企/公司/销售/库存/营业额等)时,")
                .append("【不要追加任何标记】。\n")
                .append("可选目的地:\n");
        cat.forEach((label, route) -> sb.append("  ").append(route).append(" → ").append(label).append('\n'));
        sb.append("不相关时不要追加任何标记。");
        return sb.toString();
    }

    /** 解析回答:剔除动作标记回写正文,并把合法动作(按角色白名单 + 主题相关)写入 vo.actions。
     *  四道闸:① target 必须是本角色合法路由(防臆造/越权);② label 用目录标准名覆盖 LLM 自拟文字(防"查看企业"张冠李戴);
     *  ③ 动作主题词必须出现在【召回第 1 条来源标题】里——它代表回答真正讲的对象,不符即丢弃(防讲药企却冒出"临床反馈"按钮);
     *  ④ 无数据兜底引导:LLM 判「暂无该数据」(库存/营业额等动态数据本不在 KB)时不会追加标记、且往往无贴题来源,
     *     上面提取拿不到按钮——这时按【问题 query】里的主题词挂 1 个跳转,把用户引到能查到该数据的业务页(库存→药品库存页)。 */
    public static void apply(KbAnswerVO vo, Integer role, String query) {
        String answer = vo.getAnswer() == null ? "" : vo.getAnswer();
        Map<String, String> cat = role == null ? null : CATALOG.get(role);
        String cleanAnswer = ACTION.matcher(answer).replaceAll("");

        // 主题锚点 = 最相关来源(召回第 1 条)的标题;无来源(通用兜底/拒绝)则不带任何动作。
        String topTitle = (vo.getSources() != null && !vo.getSources().isEmpty())
                ? vo.getSources().get(0).getTitle() : "";
        if (topTitle == null) topTitle = "";

        List<KbAnswerVO.Action> actions = new java.util.ArrayList<>();
        Matcher m = ACTION.matcher(answer);
        while (m.find()) {
            String target = m.group(2).trim();
            // 闸①②:反查目录取标准 label;target 不在本角色目录则丢弃。
            String canonicalLabel = null;
            if (cat != null) {
                for (Map.Entry<String, String> e : cat.entrySet()) {
                    if (target.equals(e.getValue())) { canonicalLabel = e.getKey(); break; }
                }
            }
            if (canonicalLabel == null) continue;
            // 闸③:主题词须出现在【最相关来源标题】或【用户问题 query】里,否则是 LLM 硬塞的无关跳转,丢弃。
            // 放宽(原仅看 topTitle):若用户问的就是"网点"而命中条标题恰好不含"网点"二字,原判会误杀这个合理跳转;
            //      现并认可"query 本身带该主题词"——既挡硬塞无关按钮,又提升跳转触发率。
            String topic = ROUTE_TOPIC.get(target);
            if (topic != null && !topTitle.contains(topic) && (query == null || !query.contains(topic))) continue;
            KbAnswerVO.Action a = new KbAnswerVO.Action();
            a.setLabel(canonicalLabel);
            a.setTarget(target);
            actions.add(a);
        }
        // 闸④:无数据兜底引导(见方法 javadoc)。用原始 query——不是拼了目录的 qForLlm(后者含路由名会误命中主题词)。
        if (actions.isEmpty() && isNoData(answer) && cat != null && query != null) {
            for (Map.Entry<String, String> e : cat.entrySet()) {
                String topic = ROUTE_TOPIC.get(e.getValue());
                if (topic != null && query.contains(topic)) {
                    KbAnswerVO.Action a = new KbAnswerVO.Action();
                    a.setLabel(e.getKey());
                    a.setTarget(e.getValue());
                    actions.add(a);
                    break;   // 只挂 1 个最相关的,免得一串按钮
                }
            }
        }
        vo.setAnswer(cleanAnswer.trim());
        vo.setActions(actions);
    }

    /** 回答是否为"暂无该数据"(正经问题但 KB 无此动态数据)。措辞含「暂无」+「数据」;
     *  拒答句是"不在…服务范围"、不含此搭配,故不与越界混淆。 */
    private static boolean isNoData(String answer) {
        return answer != null && answer.contains("暂无") && answer.contains("数据");
    }
}
