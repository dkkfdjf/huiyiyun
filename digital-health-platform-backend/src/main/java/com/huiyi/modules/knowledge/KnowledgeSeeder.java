package com.huiyi.modules.knowledge;

import com.huiyi.common.entity.BaseEntity;
import com.huiyi.modules.knowledge.dto.KbIngestDTO;
import com.huiyi.modules.knowledge.model.KnowledgeDocument;
import com.huiyi.modules.system.entity.City;
import com.huiyi.modules.system.entity.CompanyPolicy;
import com.huiyi.modules.system.entity.Department;
import com.huiyi.modules.system.entity.Drug;
import com.huiyi.modules.system.entity.EssentialMaterial;
import com.huiyi.modules.system.entity.MedicalInstitution;
import com.huiyi.modules.system.entity.PharmaCompany;
import com.huiyi.modules.system.entity.SalesLocation;
import com.huiyi.modules.system.mapper.CityMapper;
import com.huiyi.modules.system.mapper.CompanyPolicyMapper;
import com.huiyi.modules.system.mapper.DepartmentMapper;
import com.huiyi.modules.system.mapper.DrugMapper;
import com.huiyi.modules.system.mapper.EssentialMaterialMapper;
import com.huiyi.modules.system.mapper.MedicalInstitutionMapper;
import com.huiyi.modules.system.mapper.PharmaCompanyMapper;
import com.huiyi.modules.system.mapper.SalesLocationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 从业务表自动建库:管理员一键把现有业务数据(药品/必备材料/政策公告/药企/机构/科室/网点)
 * 批量向量化灌进 RAG 知识库,并按数据自然归属打**行级 scope** 标签——免去手动导入。
 *
 * 复用 {@link KnowledgeService#ingest}(切块+嵌入+去重+落库),不重造 RAG 管线。
 *
 * scope 规则(用户定「按归属隔离」):
 * <ul>
 *   <li>必备材料 / 药品 / 医保政策(type=1) / 药企·机构主数据 → GLOBAL(全员)</li>
 *   <li>药企公告·价格政策(type=2/3) / 销售网点 → COMPANY:{companyId}(本药企+管理员)</li>
 *   <li>科室 → INSTITUTION:{institutionId}(本机构+管理员,医师同院)</li>
 * </ul>
 *
 * 重建语义:先清旧业务文档(保留 manual 手导),再逐表灌。ingest 按 text SHA-256 去重,
 * 故同表内内容完全相同的行只入一次;字段变化的记录因文本变会重新灌入,旧版已开头整体清掉,不残留。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSeeder {

    private final KnowledgeService knowledgeService;
    private final DrugMapper drugMapper;
    private final EssentialMaterialMapper essentialMaterialMapper;
    private final CompanyPolicyMapper companyPolicyMapper;
    private final PharmaCompanyMapper pharmaCompanyMapper;
    private final MedicalInstitutionMapper medicalInstitutionMapper;
    private final DepartmentMapper departmentMapper;
    private final SalesLocationMapper salesLocationMapper;
    private final CityMapper cityMapper;

    /** 业务来源标识(= KbIngestDTO.sourceType);rebuild 据此清旧、保留 manual 手导。 */
    private static final Set<String> BUSINESS_SOURCES = Set.of(
            "drug", "essential_material", "company_policy",
            "pharma_company", "medical_institution", "department", "sales_location");

    private static final Long SYSTEM_USER = 0L;   // 系统自动建库的 createdBy

    /** 全量重建:清旧业务文档 → 逐表灌库。返回统计 {removedOld, docs, byType}。 */
    public Map<String, Object> rebuild() {
        int removed = clearBusinessDocs();
        Map<String, Long> byType = new LinkedHashMap<>();
        int docs = 0;
        docs += tally("必备材料", byType, seedEssentialMaterial());
        docs += tally("药品", byType, seedDrug());
        docs += tally("政策公告", byType, seedCompanyPolicy());
        docs += tally("药企", byType, seedPharmaCompany());
        docs += tally("机构", byType, seedMedicalInstitution());
        docs += tally("科室", byType, seedDepartment());
        docs += tally("网点", byType, seedSalesLocation());

        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("removedOld", removed);
        stat.put("docs", docs);
        stat.put("byType", byType);
        log.info("kb rebuild done: removedOld={} docs={} byType={}", removed, docs, byType);
        return stat;
    }

    /** 清除所有"业务来源"文档(manual 手导保留)。返回清除数。 */
    private int clearBusinessDocs() {
        int n = 0;
        for (KnowledgeDocument d : knowledgeService.listDocs()) {
            if (BUSINESS_SOURCES.contains(d.getSourceType())) {
                knowledgeService.deleteDoc(d.getId());
                n++;
            }
        }
        return n;
    }

    // ---- 各业务表 → 知识文档(拼描述性文本 + 定 scope)----

    private int seedEssentialMaterial() {
        int n = 0;
        for (EssentialMaterial m : essentialMaterialMapper.selectList(null)) {
            if (isBlank(m.getContent())) continue;
            String text = fields("类别", m.getCategory(), "名称", m.getName()) + "\n" + m.getContent();
            ingest("essential_material", "必备材料-" + m.getName(), text, "GLOBAL", id(m));
            n++;
        }
        return n;
    }

    private int seedDrug() {
        int n = 0;
        for (Drug d : drugMapper.selectList(null)) {
            String text = fields("药品", d.getName(), "规格", d.getSpecification(), "剂型", d.getDosageForm(),
                    "生产企业", d.getProducer(), "批准文号", d.getApprovalNo());
            ingest("drug", "药品-" + d.getName(), text, "GLOBAL", id(d));
            n++;
        }
        return n;
    }

    private int seedCompanyPolicy() {
        int n = 0;
        LocalDate today = LocalDate.now();
        for (CompanyPolicy p : companyPolicyMapper.selectList(null)) {
            if (isBlank(p.getContent())) continue;
            if (p.getExpireDate() != null && p.getExpireDate().isBefore(today)) continue;   // 已失效不灌
            String label = policyLabel(p.getPolicyType());
            // type=1 医保政策=全员公共;其余=本药企私有;无 companyId 退化为公共(极少)
            String scope = (p.getPolicyType() != null && p.getPolicyType() == 1)
                    ? "GLOBAL"
                    : (p.getCompanyId() != null ? "COMPANY:" + p.getCompanyId() : "GLOBAL");
            String text = "【" + label + "】" + nullToEmpty(p.getTitle()) + "\n" + p.getContent();
            ingest("company_policy", label + "-" + nullToEmpty(p.getTitle()), text, scope, id(p));
            n++;
        }
        return n;
    }

    private int seedPharmaCompany() {
        int n = 0;
        for (PharmaCompany c : pharmaCompanyMapper.selectList(null)) {
            if (c.getAuditStatus() == null || c.getAuditStatus() != 1) continue;   // 仅正常态
            String text = fields("药企", c.getName(), "信用代码", c.getCreditCode(), "许可证号", c.getLicenseNo(), "地址", c.getAddress());
            ingest("pharma_company", "药企-" + c.getName(), text, "GLOBAL", id(c));
            n++;
        }
        return n;
    }

    private int seedMedicalInstitution() {
        int n = 0;
        for (MedicalInstitution i : medicalInstitutionMapper.selectList(null)) {
            if (i.getAuditStatus() == null || i.getAuditStatus() != 1) continue;
            String text = fields("医疗机构", i.getName(), "地址", i.getAddress());
            ingest("medical_institution", "机构-" + i.getName(), text, "GLOBAL", id(i));
            n++;
        }
        return n;
    }

    private int seedDepartment() {
        // 拼"机构名 - 科室":先建 institutionId→name 映射,避免 N+1。
        Map<Long, String> instNames = new HashMap<>();
        for (MedicalInstitution i : medicalInstitutionMapper.selectList(null)) {
            instNames.put(i.getId(), i.getName());
        }
        int n = 0;
        for (Department d : departmentMapper.selectList(null)) {
            String inst = instNames.getOrDefault(d.getInstitutionId(), "");
            String text = fields("机构", inst, "科室", d.getName());
            String scope = d.getInstitutionId() != null ? "INSTITUTION:" + d.getInstitutionId() : "GLOBAL";
            ingest("department", "科室-" + inst + "-" + nullToEmpty(d.getName()), text, scope, id(d));
            n++;
        }
        return n;
    }

    private int seedSalesLocation() {
        // 城市 cityId → "省+市"(如"湖南长沙"),让"在湖南/长沙去哪买药"这类按地域的提问能命中;
        // 公司 companyId → 药企名,补"所属药企"。两份数据量都小,一次性建 Map 避免 N+1。
        Map<Long, String> cityLabel = new HashMap<>();
        for (City c : cityMapper.selectList(null)) {
            cityLabel.put(c.getId(), join("", c.getProvince(), c.getName()));
        }
        Map<Long, String> companyNames = new HashMap<>();
        for (PharmaCompany c : pharmaCompanyMapper.selectList(null)) {
            companyNames.put(c.getId(), c.getName());
        }
        int n = 0;
        for (SalesLocation s : salesLocationMapper.selectList(null)) {
            String city = cityLabel.getOrDefault(s.getCityId(), "");
            String company = s.getCompanyId() == null ? "" : companyNames.getOrDefault(s.getCompanyId(), "");
            String coords = (s.getLongitude() != null && s.getLatitude() != null)
                    ? s.getLongitude().toPlainString() + "," + s.getLatitude().toPlainString() : "";
            // 标签式字段(非"·"无标签拼接):避免 LLM 把"销售网点:湘江店 · 周口店"这类字段分隔误读成两个网点
            String text = fields(
                    "销售网点", s.getName(),
                    "城市", city,
                    "所属药企", company,
                    "地址", s.getAddress(),
                    "经纬度", coords,
                    "联系人", s.getContactPerson(),
                    "电话", s.getContactPhone());
            String scope = s.getCompanyId() != null ? "COMPANY:" + s.getCompanyId() : "GLOBAL";
            ingest("sales_location", "网点-" + nullToEmpty(s.getName()), text, scope, id(s));
            n++;
        }
        return n;
    }

    // ---- 辅助 ----

    /** 单条入库:复用 KnowledgeService.ingest(切块+嵌入+去重+落库)。sourceRef=业务主键便于溯源。 */
    private void ingest(String sourceType, String title, String text, String scope, String sourceRef) {
        KbIngestDTO dto = new KbIngestDTO();
        dto.setTitle(title);
        dto.setText(text);
        dto.setSourceType(sourceType);
        dto.setSourceRef(sourceRef);
        dto.setScope(scope);
        knowledgeService.ingest(dto, SYSTEM_USER);
    }

    private static int tally(String key, Map<String, Long> byType, int n) {
        byType.put(key, (long) n);
        return n;
    }

    private static String policyLabel(Integer t) {
        if (t == null) return "政策";
        return switch (t) {
            case 1 -> "医保政策";
            case 2 -> "药企公告";
            case 3 -> "价格政策";
            default -> "政策";
        };
    }

    /** 用分隔符拼接非空片段。 */
    private static String join(String sep, String... parts) {
        StringJoiner sj = new StringJoiner(sep);
        for (String p : parts) {
            if (p != null && !p.isBlank()) sj.add(p);
        }
        return sj.toString();
    }

    /** 标签式拼字段("标签:值" 以 " | " 连,空值跳过):比无标签的 · 拼接更不易被 LLM 误读成多个实体(治"一条数据当两个答案")。 */
    private static String fields(String... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String v = kv[i + 1];
            if (v == null || v.isBlank()) continue;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(kv[i]).append(':').append(v.trim());
        }
        return sb.toString();
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static String id(BaseEntity e) { return e.getId() == null ? "" : String.valueOf(e.getId()); }
}
