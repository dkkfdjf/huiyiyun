package com.huiyi.modules.company;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.ResultCode;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.modules.auth.entity.User;
import com.huiyi.modules.auth.mapper.UserMapper;
import com.huiyi.modules.system.entity.CompanyPolicy;
import com.huiyi.modules.system.entity.Drug;
import com.huiyi.modules.system.entity.DrugDemand;
import com.huiyi.modules.system.entity.PharmaCompany;
import com.huiyi.modules.system.entity.SalesLocation;
import com.huiyi.modules.system.mapper.CompanyPolicyMapper;
import com.huiyi.modules.system.mapper.DrugDemandMapper;
import com.huiyi.modules.system.mapper.DrugMapper;
import com.huiyi.modules.system.mapper.PharmaCompanyMapper;
import com.huiyi.modules.system.mapper.SalesLocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 药企管理(全局目录数据,无租户隔离)。
 * - listActive():正常态药企,给反馈指派/政策归属等下拉用,走缓存(空结果不缓存)。
 * - 管理 CRUD + 启停:管理员维护公司目录、启停账号(状态:1正常/3停用;管理员录入默认正常)。
 * - 本公司自管(M1):公司用户查看/维护本公司联系方式,查发展概览。
 *   任意写都整表驱逐 companies:active 缓存,保证下拉不读旧值。
 */
@Service
@RequiredArgsConstructor
public class PharmaCompanyService {

    private final PharmaCompanyMapper companyMapper;
    private final DrugMapper drugMapper;
    private final SalesLocationMapper locationMapper;
    private final UserMapper userMapper;
    private final CompanyPolicyMapper policyMapper;
    private final DrugDemandMapper demandMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 正常态药企(下拉用);全局只读,缓存。空结果不缓存(本方法无 sync,unless 生效)。 */
    @Cacheable(value = "companies:active",
            unless = "#result == null || #result.isEmpty()")
    public List<PharmaCompany> listActive() {
        return companyMapper.selectList(new LambdaQueryWrapper<PharmaCompany>()
                .eq(PharmaCompany::getAuditStatus, 1)
                .orderByAsc(PharmaCompany::getId));
    }

    /** 分页:支持名称模糊 + 状态(正常/停用)筛选。 */
    public PageResult<PharmaCompany> page(String name, Integer auditStatus, int pageNum, int pageSize) {
        LambdaQueryWrapper<PharmaCompany> w = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) w.like(PharmaCompany::getName, name.trim());
        if (auditStatus != null) w.eq(PharmaCompany::getAuditStatus, auditStatus);
        w.orderByDesc(PharmaCompany::getCreateTime);
        Page<PharmaCompany> p = companyMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 详情(编辑回显用)。 */
    public PharmaCompany detail(Long id) {
        return requireExists(id);
    }

    /**
     * 新增:一个事务内 建 公司 + 登录账号(role=1)并互链(对齐 DoctorService)。
     * 联系人取账号姓名,消除"联系人飘着、与 user 表脱节"。信用代码 + 登录名均唯一。
     */
    @Transactional
    @CacheEvict(value = "companies:active", allEntries = true)
    public Long save(PharmaCompanySaveDTO dto) {
        ensureCreditUnique(dto.getCreditCode(), null);
        String username = requireAccount(dto);

        PharmaCompany c = new PharmaCompany();
        applyFields(c, dto);
        c.setContactPerson(dto.getRealName().trim());   // 联系人=账号姓名
        c.setAuditStatus(1);   // 管理员录入默认正常
        c.setAuditTime(LocalDateTime.now());
        c.setAuditRemark("管理员录入");
        companyMapper.insert(c);

        User u = new User();
        u.setUsername(username);
        u.setPassword(encoder.encode(dto.getPassword()));
        u.setRealName(dto.getRealName().trim());
        u.setRole(RoleConstants.COMPANY);
        u.setStatus(1);
        u.setCompanyId(c.getId());
        userMapper.insert(u);
        return c.getId();
    }

    /** 修改:仅改可编辑字段,不动审核态。 */
    @CacheEvict(value = "companies:active", allEntries = true)
    public void update(Long id, PharmaCompanySaveDTO dto) {
        PharmaCompany c = requireExists(id);
        ensureCreditUnique(dto.getCreditCode(), id);
        applyFields(c, dto);
        companyMapper.updateById(c);
    }

    /**
     * 启停:在 正常(1)/停用(3) 间翻转(status 为空按当前态取反;显式传 1/3 则直接设置)。
     * 停用同步禁用其登录账号(兑现"停用后无法登录");正常则启用。
     */
    @CacheEvict(value = "companies:active", allEntries = true)
    public void toggleStatus(Long id, Integer status) {
        PharmaCompany c = requireExists(id);
        int target = status == null
                ? (c.getAuditStatus() == null || c.getAuditStatus() == 1 ? 3 : 1)
                : status;
        c.setAuditStatus(target);
        companyMapper.updateById(c);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getCompanyId, id).eq(User::getRole, RoleConstants.COMPANY)
                .set(User::getStatus, target == 1 ? 1 : 0));
    }

    /**
     * 软删:存在药品/网点时阻止(防孤儿业务数据)。
     * 其登录账号改为解绑+禁用(对齐 DoctorService.delete),不再因账号阻塞删除。
     */
    @CacheEvict(value = "companies:active", allEntries = true)
    public void delete(Long id) {
        requireExists(id);
        long refDrug = drugMapper.selectCount(new LambdaQueryWrapper<Drug>().eq(Drug::getCompanyId, id));
        long refLoc = locationMapper.selectCount(new LambdaQueryWrapper<SalesLocation>().eq(SalesLocation::getCompanyId, id));
        if (refDrug + refLoc > 0)
            throw new BusinessException(ResultCode.REFERENCE_CONFLICT.getCode(), "该药企下存在药品/网点,无法删除");
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getCompanyId, id).eq(User::getRole, RoleConstants.COMPANY)
                .set(User::getStatus, 0).set(User::getCompanyId, null));
        companyMapper.deleteById(id);
    }

    /* —————— 本公司自管(M1,公司用户)—————— */

    /** 本公司信息。 */
    public PharmaCompany getMe(Long companyId) {
        PharmaCompany c = companyMapper.selectById(companyId);
        if (c == null) throw new BusinessException(ResultCode.NOT_FOUND);
        return c;
    }

    /** 本公司自助维护:仅改联系方式/地址/许可证号(名称/信用代码/审核态由管理员管)。 */
    @CacheEvict(value = "companies:active", allEntries = true)
    public void updateMe(Long companyId, CompanyProfileDTO dto) {
        PharmaCompany c = companyMapper.selectById(companyId);
        if (c == null) throw new BusinessException(ResultCode.NOT_FOUND);
        c.setContactPerson(dto.getContactPerson());
        c.setContactPhone(dto.getContactPhone());
        c.setAddress(dto.getAddress());
        c.setLicenseNo(dto.getLicenseNo());
        companyMapper.updateById(c);
    }

    /** 发展概览:药品数 / 网点数 / 政策数 / 待处理反馈数。 */
    public Map<String, Object> development(Long companyId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("drugCount", drugMapper.selectCount(new LambdaQueryWrapper<Drug>().eq(Drug::getCompanyId, companyId)));
        m.put("locationCount", locationMapper.selectCount(new LambdaQueryWrapper<SalesLocation>().eq(SalesLocation::getCompanyId, companyId)));
        m.put("policyCount", policyMapper.selectCount(new LambdaQueryWrapper<CompanyPolicy>().eq(CompanyPolicy::getCompanyId, companyId)));
        m.put("pendingDemandCount", demandMapper.selectCount(new LambdaQueryWrapper<DrugDemand>()
                .eq(DrugDemand::getCompanyId, companyId).eq(DrugDemand::getStatus, 0)));
        return m;
    }

    /* —————— 私有工具 —————— */

    /** 校验新增时的登录账号入参(用户名/密码/姓名),登录名查重,返回 trim 后用户名。 */
    private String requireAccount(PharmaCompanySaveDTO dto) {
        String username = dto.getUsername();
        String password = dto.getPassword();
        String realName = dto.getRealName();
        if (username == null || username.isBlank() || password == null || password.isBlank()
                || realName == null || realName.isBlank())
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "新增药企需填写登录账号(用户名/密码/姓名)");
        username = username.trim();
        long dup = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (dup > 0)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "登录名已存在");
        return username;
    }

    private void applyFields(PharmaCompany c, PharmaCompanySaveDTO dto) {
        c.setName(dto.getName().trim());
        c.setCreditCode(dto.getCreditCode().trim());
        c.setLicenseNo(dto.getLicenseNo());
        c.setContactPerson(dto.getContactPerson());
        c.setContactPhone(dto.getContactPhone());
        c.setAddress(dto.getAddress());
    }

    private void ensureCreditUnique(String creditCode, Long excludeId) {
        long cnt = companyMapper.selectCount(new LambdaQueryWrapper<PharmaCompany>()
                .eq(PharmaCompany::getCreditCode, creditCode)
                .ne(excludeId != null, PharmaCompany::getId, excludeId));
        if (cnt > 0)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "统一社会信用代码已存在");
    }

    private PharmaCompany requireExists(Long id) {
        PharmaCompany c = companyMapper.selectById(id);
        if (c == null) throw new BusinessException(ResultCode.NOT_FOUND);
        return c;
    }
}
