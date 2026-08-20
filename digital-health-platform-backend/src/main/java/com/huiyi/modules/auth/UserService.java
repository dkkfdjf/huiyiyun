package com.huiyi.modules.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.ResultCode;
import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.auth.entity.User;
import com.huiyi.modules.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 账号管理(管理员统一建号/改号)。User.password 标了 @JsonProperty(WRITE_ONLY),
 * 故分页返回永不带密码哈希。内置三道护栏:
 *  1) 登录名查重(新增时);
 *  2) 角色绑定一致性(药企→公司,机构/医师→机构,管理员→无绑定);
 *  3) 不可停用/降级/删除自己,且系统至少保留一个启用的管理员(防锁死)。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 分页:登录名模糊、角色、状态筛选;按创建时间倒序。 */
    public PageResult<User> page(String username, Integer role, Integer status,
                                 int pageNum, int pageSize) {
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) w.like(User::getUsername, username.trim());
        if (role != null) w.eq(User::getRole, role);
        if (status != null) w.eq(User::getStatus, status);
        w.orderByDesc(User::getCreateTime);
        Page<User> p = userMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 新增:登录名查重 + 角色绑定校验;管理员角色清空公司/机构绑定。 */
    @Transactional
    public Long save(UserSaveDTO dto) {
        validateBinding(dto.getRole(), dto.getCompanyId(), dto.getInstitutionId());
        long dup = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername().trim()));
        if (dup > 0)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "登录名已存在");

        User u = new User();
        u.setUsername(dto.getUsername().trim());
        u.setPassword(encoder.encode(dto.getPassword()));
        u.setRealName(dto.getRealName());
        u.setRole(dto.getRole());
        u.setStatus(1);
        u.setLoginFailCount(0);
        applyBinding(u, dto.getRole(), dto.getCompanyId(), dto.getInstitutionId());
        u.setPhone(dto.getPhone());
        u.setEmail(dto.getEmail());
        userMapper.insert(u);
        return u.getId();
    }

    /** 修改:登录名/密码不动;调整角色或停用走保底管理员与禁自操作校验。 */
    @Transactional
    public void update(Long id, UserUpdateDTO dto) {
        User u = requireExists(id);
        validateBinding(dto.getRole(), dto.getCompanyId(), dto.getInstitutionId());

        boolean losingAdmin = losingAdmin(u, dto.getRole(),
                dto.getStatus() == null ? u.getStatus() : dto.getStatus());
        if (losingAdmin) {
            ensureNotSelf(id);
            ensureNotLastAdmin(u);
        }

        u.setRealName(dto.getRealName());
        u.setRole(dto.getRole());
        applyBinding(u, dto.getRole(), dto.getCompanyId(), dto.getInstitutionId());
        if (dto.getStatus() != null) u.setStatus(dto.getStatus());
        u.setPhone(dto.getPhone());
        u.setEmail(dto.getEmail());
        userMapper.updateById(u);
    }

    /** 重置密码(6~50):管理员统一改密入口,可重置他人或自己。 */
    public void resetPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 50)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "密码长度 6~50");
        User u = requireExists(id);
        u.setPassword(encoder.encode(newPassword));
        // 重置密码同时清锁定,避免"改完密仍被锁"
        u.setLoginFailCount(0);
        u.setLockedUntil(null);
        userMapper.updateById(u);
    }

    /** 自助改密:校验旧密码,通过后设新密码(6~50)并清锁定。 */
    public void changeMyPassword(Long userId, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 50)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "新密码长度 6~50");
        User u = requireExists(userId);
        if (oldPassword == null || !encoder.matches(oldPassword, u.getPassword()))
            // 旧密码错误同属凭据错误,用 4007(非 401):避免前端把它当"会话过期"弹"登录已过期"。
            throw new BusinessException(ResultCode.BAD_CREDENTIALS.getCode(), "旧密码错误");
        u.setPassword(encoder.encode(newPassword));
        u.setLoginFailCount(0);
        u.setLockedUntil(null);
        userMapper.updateById(u);
    }

    /** 启停:status 为空则在 正常/禁用 间翻转;停用走保底管理员与禁自操作校验。 */
    @Transactional
    public void toggleStatus(Long id, Integer status) {
        User u = requireExists(id);
        int target = status != null ? status : (u.getStatus() != null && u.getStatus() == 1 ? 0 : 1);
        if (target == 0) {
            ensureNotSelf(id);
            // 仅当被停用账号本身是"启用管理员"时才校验保底管理员(与 delete 一致);
            // 否则停用药企/机构/医师也会误触"至少保留一个管理员账号"。
            if (u.getStatus() != null && u.getStatus() == 1
                    && u.getRole() != null && u.getRole() == RoleConstants.ADMIN) {
                ensureNotLastAdmin(u);
            }
        }
        u.setStatus(target);
        userMapper.updateById(u);
    }

    /** 解锁:清失败计数与锁定时间(管理员手动解锁被 5 次失败锁定的账号)。 */
    public void unlock(Long id) {
        User u = requireExists(id);
        u.setLoginFailCount(0);
        u.setLockedUntil(null);
        userMapper.updateById(u);
    }

    /** 软删:不可删自己;删启用管理员需保证至少还剩一个。 */
    @Transactional
    public void delete(Long id) {
        User u = requireExists(id);
        ensureNotSelf(id);
        if (u.getStatus() != null && u.getStatus() == 1
                && u.getRole() != null && u.getRole() == RoleConstants.ADMIN) {
            ensureNotLastAdmin(u);
        }
        userMapper.deleteById(id);
    }

    /* —— 内部工具 —— */

    private User requireExists(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BusinessException(ResultCode.NOT_FOUND);
        return u;
    }

    /** 角色绑定一致性校验;管理员角色无视传入的公司/机构。 */
    private void validateBinding(Integer role, Long companyId, Long institutionId) {
        if (role == null)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "请选择角色");
        if (role == RoleConstants.COMPANY && companyId == null)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "药企账号需绑定公司");
        if ((role == RoleConstants.INSTITUTION || role == RoleConstants.DOCTOR) && institutionId == null)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "机构账号需绑定机构");
    }

    /** 按角色落地绑定字段:管理员清空公司/机构。 */
    private void applyBinding(User u, Integer role, Long companyId, Long institutionId) {
        if (role == RoleConstants.ADMIN) {
            u.setCompanyId(null);
            u.setInstitutionId(null);
        } else if (role == RoleConstants.COMPANY) {
            u.setCompanyId(companyId);
            u.setInstitutionId(null);
        } else {
            u.setCompanyId(null);
            u.setInstitutionId(institutionId);
        }
    }

    /** 本次操作是否会让 target 从"启用管理员"池中消失(改角色/停用)。 */
    private boolean losingAdmin(User target, Integer newRole, Integer newStatus) {
        boolean wasEnabledAdmin = target.getRole() != null && target.getRole() == RoleConstants.ADMIN
                && target.getStatus() != null && target.getStatus() == 1;
        if (!wasEnabledAdmin) return false;
        return !Objects.equals(newRole, RoleConstants.ADMIN)
                || newStatus == null || newStatus != 1;
    }

    private void ensureNotSelf(Long targetId) {
        CurrentUser u = SecurityContextHolder.get();
        if (u != null && u.getUserId() != null && u.getUserId().equals(targetId))
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "不能操作当前登录账号");
    }

    /** 系统至少保留一个启用的管理员,避免把自己锁在门外。 */
    private void ensureNotLastAdmin(User target) {
        long cnt = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, RoleConstants.ADMIN)
                .eq(User::getStatus, 1));
        if (cnt <= 1)
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "至少保留一个启用的管理员账号");
    }
}
