package com.huiyi.modules.knowledge;

import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.RoleConstants;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 知识库行级隔离:把当前登录用户映射成"可见 scope 集合",供向量检索预过滤,防药企/机构间串读。
 *
 * 返回 null = 不限(管理员全可见);否则检索只命中 scope ∈ 集合的向量。
 * 规则(用户定「按归属隔离」):
 * <ul>
 *   <li>ADMIN → null(全部)</li>
 *   <li>COMPANY → {GLOBAL, COMPANY:{自己companyId}}</li>
 *   <li>INSTITUTION / DOCTOR → {GLOBAL, INSTITUTION:{自己institutionId}}(医生属机构看本院科室;
 *       institutionId 未填则仅 GLOBAL,安全降级——少给不扩权)</li>
 * </ul>
 * 依据:CurrentUser 的 role/companyId/institutionId(经 JwtUtil 从 token 解析,见 common.security)。
 */
@Component
public class KbScopeResolver {

    public Set<String> visibleScopes(CurrentUser u) {
        if (u == null || u.getRole() == null || u.getRole() == RoleConstants.ADMIN) {
            return null;   // 管理员全可见(匿名本不应到达此处,JwtAuthFilter 已拦)
        }
        Set<String> s = new HashSet<>();
        s.add("GLOBAL");
        if (u.getRole() == RoleConstants.COMPANY && u.getCompanyId() != null) {
            s.add("COMPANY:" + u.getCompanyId());
        }
        if ((u.getRole() == RoleConstants.INSTITUTION || u.getRole() == RoleConstants.DOCTOR)
                && u.getInstitutionId() != null) {
            s.add("INSTITUTION:" + u.getInstitutionId());
        }
        return s;
    }
}
