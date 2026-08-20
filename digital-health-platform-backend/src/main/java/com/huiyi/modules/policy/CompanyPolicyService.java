package com.huiyi.modules.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.ResultCode;
import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.notification.NotificationService;
import com.huiyi.modules.system.entity.CompanyPolicy;
import com.huiyi.modules.system.entity.PharmaCompany;
import com.huiyi.modules.system.mapper.CompanyPolicyMapper;
import com.huiyi.modules.system.mapper.PharmaCompanyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 医药公司政策(药企公告)管理。
 * 隔离:公司仅本公司(读+写,自动归属登录公司);管理员/机构/医师全量只读(公告向下游触达)。
 * 写操作归属校验失败 → NO_DATA_PERMISSION。
 */
@Service
@RequiredArgsConstructor
public class CompanyPolicyService {

    private final CompanyPolicyMapper policyMapper;
    private final NotificationService notificationService;
    private final PharmaCompanyMapper companyMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 转换为 VO */
    private CompanyPolicyVO toVO(CompanyPolicy p, String companyName) {
        CompanyPolicyVO vo = new CompanyPolicyVO();
        vo.setId(p.getId());
        vo.setCompanyId(p.getCompanyId());
        vo.setCompanyName(companyName);
        vo.setTitle(p.getTitle());
        vo.setContent(p.getContent());
        vo.setPolicyType(p.getPolicyType());
        vo.setEffectiveDate(p.getEffectiveDate());
        vo.setExpireDate(p.getExpireDate());
        vo.setCreateTime(p.getCreateTime() != null ? p.getCreateTime().format(DATE_FORMATTER) : null);
        vo.setUpdateTime(p.getUpdateTime() != null ? p.getUpdateTime().format(DATE_FORMATTER) : null);
        return vo;
    }

    /** 分页:公司仅本公司;其余角色全量(companyId 可选过滤)。返回含企业名称的 VO */
    public PageResult<CompanyPolicyVO> page(String title, Integer policyType, Long companyId, int pageNum, int pageSize) {
        CurrentUser u = SecurityContextHolder.get();
        LambdaQueryWrapper<CompanyPolicy> w = new LambdaQueryWrapper<>();
        if (u.getRole() == RoleConstants.COMPANY) {
            w.eq(CompanyPolicy::getCompanyId, u.getCompanyId());
        } else if (companyId != null) {
            w.eq(CompanyPolicy::getCompanyId, companyId);
        }
        if (title != null && !title.isBlank()) w.like(CompanyPolicy::getTitle, title);
        if (policyType != null) w.eq(CompanyPolicy::getPolicyType, policyType);
        w.orderByDesc(CompanyPolicy::getCreateTime);
        Page<CompanyPolicy> p = policyMapper.selectPage(new Page<>(pageNum, pageSize), w);

        // 查询关联的企业名称（过滤掉 null 的 companyId）
        List<Long> companyIds = p.getRecords().stream()
            .map(CompanyPolicy::getCompanyId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        Map<Long, String> companyNames = companyIds.isEmpty() ? Map.of() :
            companyMapper.selectBatchIds(companyIds).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));

        List<CompanyPolicyVO> vos = p.getRecords().stream()
            .map(policy -> {
                String name = policy.getCompanyId() != null
                    ? companyNames.getOrDefault(policy.getCompanyId(), "未知企业")
                    : "系统管理员";
                return toVO(policy, name);
            })
            .collect(Collectors.toList());

        return PageResult.of(vos, p.getTotal(), pageNum, pageSize);
    }

    /** 最新 N 条(仪表盘"最新政策"公告用;全角色可读)。返回含企业名称的 VO */
    public List<CompanyPolicyVO> latest(int n) {
        int limit = Math.max(1, Math.min(n, 50));
        List<CompanyPolicy> policies = policyMapper.selectList(new LambdaQueryWrapper<CompanyPolicy>()
                .orderByDesc(CompanyPolicy::getCreateTime)
                .last("LIMIT " + limit));

        // 查询关联的企业名称（过滤掉 null 的 companyId）
        List<Long> companyIds = policies.stream()
            .map(CompanyPolicy::getCompanyId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        Map<Long, String> companyNames = companyIds.isEmpty() ? Map.of() :
            companyMapper.selectBatchIds(companyIds).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));

        return policies.stream()
            .map(policy -> {
                String name = policy.getCompanyId() != null
                    ? companyNames.getOrDefault(policy.getCompanyId(), "未知企业")
                    : "系统管理员";
                return toVO(policy, name);
            })
            .collect(Collectors.toList());
    }

    /** 发布:归属当前登录药企(仅药企可发布;companyId 取登录态,忽略入参)。 */
    public Long save(CompanyPolicySaveDTO dto) {
        CurrentUser u = SecurityContextHolder.get();
        CompanyPolicy p = new CompanyPolicy();
        p.setTitle(dto.getTitle().trim());
        p.setContent(dto.getContent());
        p.setPolicyType(dto.getPolicyType());
        p.setEffectiveDate(dto.getEffectiveDate());
        p.setExpireDate(dto.getExpireDate());
        p.setCompanyId(u.getCompanyId());
        policyMapper.insert(p);
        // 触达下游医疗机构:广播药企公告通知(药企类目,点击跳"药企公告"页)。通知最佳努力,失败不阻断发布。
        PharmaCompany co = u.getCompanyId() == null ? null : companyMapper.selectById(u.getCompanyId());
        String coName = (co != null && co.getName() != null && !co.getName().isBlank()) ? co.getName() : "药企";
        notificationService.notifyAllInstitutions(NotificationService.CAT_COMPANY,
                coName + " · 公告:" + p.getTitle(),
                p.getContent() == null ? "" : p.getContent(),
                NotificationService.REF_POLICY, p.getId());
        return p.getId();
    }

    /** 修改:归属校验(仅本公司);仅改可编辑字段,companyId 不可改。 */
    public void update(Long id, CompanyPolicySaveDTO dto) {
        CurrentUser u = SecurityContextHolder.get();
        CompanyPolicy p = requireOwned(id, u);
        p.setTitle(dto.getTitle().trim());
        p.setContent(dto.getContent());
        p.setPolicyType(dto.getPolicyType());
        p.setEffectiveDate(dto.getEffectiveDate());
        p.setExpireDate(dto.getExpireDate());
        policyMapper.updateById(p);
    }

    /** 删除(软删):归属校验。 */
    public void delete(Long id) {
        CurrentUser u = SecurityContextHolder.get();
        requireOwned(id, u);
        policyMapper.deleteById(id);
    }

    /** 取出并校验归属(公司仅本公司;管理员不限)。 */
    private CompanyPolicy requireOwned(Long id, CurrentUser u) {
        CompanyPolicy p = policyMapper.selectById(id);
        if (p == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (u.getRole() == RoleConstants.COMPANY
                && (p.getCompanyId() == null || !p.getCompanyId().equals(u.getCompanyId()))) {
            throw new BusinessException(ResultCode.NO_DATA_PERMISSION);
        }
        return p;
    }
}
