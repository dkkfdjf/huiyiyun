package com.huiyi.modules.doctor;

import com.huiyi.common.security.RoleConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.ResultCode;
import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.auth.entity.User;
import com.huiyi.modules.auth.mapper.UserMapper;
import com.huiyi.modules.system.entity.Department;
import com.huiyi.modules.system.entity.Doctor;
import com.huiyi.modules.system.entity.OperationLog;
import com.huiyi.modules.system.mapper.DepartmentMapper;
import com.huiyi.modules.system.mapper.DoctorMapper;
import com.huiyi.modules.system.mapper.MedicalInstitutionMapper;
import com.huiyi.modules.system.entity.MedicalInstitution;
import com.huiyi.modules.system.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 医师管理。管理员全局目录;医疗机构管理员仅本院(行级隔离)。
 * 医师档案与 role=3 账号 1:1 绑定:新增=建账号+建档,删除=软删档案+禁用账号。
 */
@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorMapper doctorMapper;
    private final MedicalInstitutionMapper institutionMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final OperationLogMapper operationLogMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 分页:支持姓名模糊、机构、科室、职称筛选;机构角色强制只看本院。 */
    public PageResult<Doctor> page(String name, Long institutionId, Long departmentId,
                                   String title, int pageNum, int pageSize) {
        CurrentUser u = SecurityContextHolder.get();
        if (u != null && u.getRole() == RoleConstants.INSTITUTION) {
            institutionId = u.getInstitutionId();   // 机构强制本院,忽略入参
        }
        LambdaQueryWrapper<Doctor> w = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) w.like(Doctor::getName, name.trim());
        if (institutionId != null) w.eq(Doctor::getInstitutionId, institutionId);
        if (departmentId != null) w.eq(Doctor::getDepartmentId, departmentId);
        if (title != null && !title.isBlank()) w.eq(Doctor::getTitle, title.trim());
        w.orderByDesc(Doctor::getCreateTime);
        Page<Doctor> p = doctorMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 新增:建 role=3 账号 + 医师档案(同一事务);登录名查重。机构角色强制归属本院。 */
    @Transactional
    public Long save(DoctorSaveDTO dto) {
        Long instId = resolveInstitution(dto.getInstitutionId());
        long dup = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername().trim()));
        if (dup > 0)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "登录名已存在");
        requireDepartmentOwned(dto.getDepartmentId(), instId);   // 防越院分配科室(设计 4.2)

        User u = new User();
        u.setUsername(dto.getUsername().trim());
        u.setPassword(encoder.encode(dto.getPassword()));
        u.setRealName(dto.getName());
        u.setRole(RoleConstants.DOCTOR);
        u.setStatus(1);
        u.setInstitutionId(instId);
        userMapper.insert(u);

        Doctor d = new Doctor();
        d.setUserId(u.getId());
        d.setName(dto.getName());
        d.setInstitutionId(instId);
        d.setDepartmentId(dto.getDepartmentId());
        d.setTitle(dto.getTitle());
        d.setPhone(dto.getPhone());
        d.setEmail(dto.getEmail());
        doctorMapper.insert(d);
        return d.getId();
    }

    /** 修改:档案字段 + 同步账号 realName/institutionId。机构角色仅本院且不可跨机构移动。 */
    @Transactional
    public void update(Long id, DoctorUpdateDTO dto) {
        Doctor d = requireExists(id);
        Long instId = resolveInstitutionForUpdate(d, dto.getInstitutionId());
        requireDepartmentOwned(dto.getDepartmentId(), instId);   // 防越院调配科室(设计 4.2)
        d.setName(dto.getName());
        d.setInstitutionId(instId);
        d.setDepartmentId(dto.getDepartmentId());
        d.setTitle(dto.getTitle());
        d.setPhone(dto.getPhone());
        d.setEmail(dto.getEmail());
        doctorMapper.updateById(d);

        User u = userMapper.selectById(d.getUserId());
        if (u != null) {
            u.setRealName(dto.getName());
            u.setInstitutionId(instId);
            userMapper.updateById(u);
        }
    }

    /**
     * 分配/调配科室(设计 §4.2 用例):把医师调到目标科室。
     * 防越院:机构角色仅本院医师;目标科室须存在且 institution_id == 医师机构。
     * 审计:写 operation_log(before=原科室ID, after=新科室ID)。
     */
    @Transactional
    public void assignDepartment(Long id, Long departmentId) {
        long start = System.currentTimeMillis();
        Doctor d = requireExists(id);
        requireOwnedByInstitution(d);
        Long before = d.getDepartmentId();               // 原科室(可能 null=未分配)
        requireDepartmentOwned(departmentId, d.getInstitutionId());   // 目标科室归属校验(复用 S2)
        d.setDepartmentId(departmentId);
        doctorMapper.updateById(d);
        writeOpLog("医师", "分配科室", "Doctor", String.valueOf(id),
                jsonDept(before), jsonDept(departmentId), System.currentTimeMillis() - start);
    }

    /** 软删档案 + 禁用账号(同一事务)。机构角色仅本院。 */
    @Transactional
    public void delete(Long id) {
        Doctor d = requireExists(id);
        requireOwnedByInstitution(d);
        doctorMapper.deleteById(id);
        User u = userMapper.selectById(d.getUserId());
        if (u != null) {
            u.setStatus(0);
            userMapper.updateById(u);
        }
    }

    /** 重置医师密码(账号管理范畴,独立于档案修改)。机构角色仅本院。 */
    public void resetPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 50)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "密码长度 6~50");
        Doctor d = requireExists(id);
        requireOwnedByInstitution(d);
        User u = userMapper.selectById(d.getUserId());
        if (u == null) throw new BusinessException(ResultCode.NOT_FOUND);
        u.setPassword(encoder.encode(newPassword));
        userMapper.updateById(u);
    }

    /* —————— 本医师自管(M5,医师)—————— */

    /** 本医师信息。 */
    public Doctor getMe(Long doctorId) {
        Doctor d = doctorMapper.selectById(doctorId);
        if (d == null) throw new BusinessException(ResultCode.NOT_FOUND);
        if (d.getInstitutionId() != null) {   // 带出归属机构名(医生端"我的信息/提交反馈"展示)
            MedicalInstitution inst = institutionMapper.selectById(d.getInstitutionId());
            d.setInstitutionName(inst == null ? null : inst.getName());
        }
        return d;
    }

    /** 本医师自助维护联系方式(电话/邮箱);不改姓名/科室/职称/归属。 */
    public void updateMe(Long doctorId, DoctorProfileDTO dto) {
        Doctor d = doctorMapper.selectById(doctorId);
        if (d == null) throw new BusinessException(ResultCode.NOT_FOUND);
        d.setPhone(dto.getPhone());
        d.setEmail(dto.getEmail());
        doctorMapper.updateById(d);
    }

    private Doctor requireExists(Long id) {
        Doctor d = doctorMapper.selectById(id);
        if (d == null) throw new BusinessException(ResultCode.NOT_FOUND);
        return d;
    }

    /** 机构角色 → 本院;否则用入参(管理员须指定)。 */
    private Long resolveInstitution(Long dtoInstitutionId) {
        CurrentUser u = SecurityContextHolder.get();
        if (u != null && u.getRole() == RoleConstants.INSTITUTION) return u.getInstitutionId();
        if (dtoInstitutionId == null)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "请选择所属机构");
        return dtoInstitutionId;
    }

    /** 修改时的归属解析:机构角色校验仅本院且保持原机构(不可跨机构移动);管理员用入参。 */
    private Long resolveInstitutionForUpdate(Doctor d, Long dtoInstitutionId) {
        CurrentUser u = SecurityContextHolder.get();
        if (u != null && u.getRole() == RoleConstants.INSTITUTION) {
            if (!Objects.equals(d.getInstitutionId(), u.getInstitutionId()))
                throw new BusinessException(ResultCode.NO_DATA_PERMISSION);
            return d.getInstitutionId();
        }
        if (dtoInstitutionId == null)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "请选择所属机构");
        return dtoInstitutionId;
    }

    /** 机构角色归属校验(管理员不限)。 */
    private void requireOwnedByInstitution(Doctor d) {
        CurrentUser u = SecurityContextHolder.get();
        if (u != null && u.getRole() == RoleConstants.INSTITUTION
                && !Objects.equals(d.getInstitutionId(), u.getInstitutionId())) {
            throw new BusinessException(ResultCode.NO_DATA_PERMISSION);
        }
    }

    /**
     * 目标科室归属校验(防越院调配,设计 4.2):科室必须存在且 institution_id 与医师归属机构一致。
     * departmentId 为 null 表示暂不分配,放行。软删科室经 @TableLogic 过滤后判为不存在。
     */
    private void requireDepartmentOwned(Long departmentId, Long institutionId) {
        if (departmentId == null) return;
        Department dept = departmentMapper.selectById(departmentId);
        if (dept == null)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "目标科室不存在");
        if (!Objects.equals(dept.getInstitutionId(), institutionId))
            throw new BusinessException(ResultCode.NO_DATA_PERMISSION);
    }

    /** 手写一条 operation_log(带 before/after)。assignDepartment 等需前后态审计的操作用。 */
    private void writeOpLog(String module, String operation, String targetType, String targetId,
                            String before, String after, long costMs) {
        CurrentUser u = SecurityContextHolder.get();
        OperationLog l = new OperationLog();
        if (u != null) {
            l.setUserId(u.getUserId());
            l.setUsername(u.getUsername());
        }
        l.setModule(module);
        l.setOperation(operation);
        l.setTargetType(targetType);
        l.setTargetId(targetId);
        l.setBeforeData(before);
        l.setAfterData(after);
        l.setCostTime(costMs);
        l.setOperationTime(LocalDateTime.now());
        operationLogMapper.insert(l);
    }

    private String jsonDept(Long departmentId) {
        return "{\"departmentId\":" + (departmentId == null ? "null" : departmentId) + "}";
    }
}
