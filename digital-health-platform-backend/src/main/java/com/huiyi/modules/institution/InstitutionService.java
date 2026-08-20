package com.huiyi.modules.institution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.ResultCode;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.modules.auth.entity.User;
import com.huiyi.modules.auth.mapper.UserMapper;
import com.huiyi.modules.system.entity.Department;
import com.huiyi.modules.system.entity.Doctor;
import com.huiyi.modules.system.entity.MedicalInstitution;
import com.huiyi.modules.system.mapper.DepartmentMapper;
import com.huiyi.modules.system.mapper.DoctorMapper;
import com.huiyi.modules.system.mapper.MedicalInstitutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 医疗机构管理(管理员全局目录数据,无行级隔离)。
 */
@Service
@RequiredArgsConstructor
public class InstitutionService {

    private final MedicalInstitutionMapper instMapper;
    private final DepartmentMapper deptMapper;
    private final DoctorMapper doctorMapper;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 全部机构(下拉用),按名称升序。 */
    public List<MedicalInstitution> list() {
        return instMapper.selectList(new LambdaQueryWrapper<MedicalInstitution>()
                .orderByAsc(MedicalInstitution::getName));
    }

    /** 分页:支持名称模糊、城市、审核态筛选。 */
    public PageResult<MedicalInstitution> page(String name, Long cityId, Integer auditStatus,
                                                int pageNum, int pageSize) {
        LambdaQueryWrapper<MedicalInstitution> w = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) w.like(MedicalInstitution::getName, name.trim());
        if (cityId != null) w.eq(MedicalInstitution::getCityId, cityId);
        if (auditStatus != null) w.eq(MedicalInstitution::getAuditStatus, auditStatus);
        w.orderByDesc(MedicalInstitution::getCreateTime);
        Page<MedicalInstitution> p = instMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /**
     * 新增:一个事务内 建 机构 + 登录账号(role=2)并互链(对齐 DoctorService)。
     * 联系人取账号姓名。登录名查重。
     */
    @Transactional
    public Long save(InstitutionSaveDTO dto) {
        String username = dto.getUsername();
        String password = dto.getPassword();
        String realName = dto.getRealName();
        if (username == null || username.isBlank() || password == null || password.isBlank()
                || realName == null || realName.isBlank())
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "新增机构需填写登录账号(用户名/密码/姓名)");
        username = username.trim();
        long dup = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (dup > 0)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "登录名已存在");

        MedicalInstitution m = new MedicalInstitution();
        m.setName(dto.getName());
        m.setAddress(dto.getAddress());
        m.setCityId(dto.getCityId());
        m.setLongitude(dto.getLongitude());
        m.setLatitude(dto.getLatitude());
        m.setContactPerson(realName.trim());   // 联系人=账号姓名
        m.setContactPhone(dto.getContactPhone());
        m.setAuditStatus(1);   // 管理员录入默认正常
        instMapper.insert(m);

        User u = new User();
        u.setUsername(username);
        u.setPassword(encoder.encode(password));
        u.setRealName(realName.trim());
        u.setRole(RoleConstants.INSTITUTION);
        u.setStatus(1);
        u.setInstitutionId(m.getId());
        userMapper.insert(u);
        return m.getId();
    }

    /** 修改:仅改可编辑字段,不动审核态。 */
    public void update(Long id, InstitutionSaveDTO dto) {
        MedicalInstitution m = requireExists(id);
        m.setName(dto.getName());
        m.setAddress(dto.getAddress());
        m.setCityId(dto.getCityId());
        m.setLongitude(dto.getLongitude());
        m.setLatitude(dto.getLatitude());
        m.setContactPerson(dto.getContactPerson());
        m.setContactPhone(dto.getContactPhone());
        instMapper.updateById(m);
    }

    /** 启停:status 为空则在 正常(1)/停用(3) 间翻转;同步本院管理员账号可否登录。 */
    public void toggleStatus(Long id, Integer status) {
        MedicalInstitution m = requireExists(id);
        int target = status == null
                ? (m.getAuditStatus() == null || m.getAuditStatus() == 1 ? 3 : 1)
                : status;
        m.setAuditStatus(target);
        instMapper.updateById(m);
        // 正常(1)→启用账号,停用(3)→禁用账号(只影响机构管理员,不动医师)
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getInstitutionId, id).eq(User::getRole, RoleConstants.INSTITUTION)
                .set(User::getStatus, target == 1 ? 1 : 0));
    }

    /** 软删:存在科室或医师时阻止;其机构管理员账号解绑+禁用(对齐 DoctorService.delete)。 */
    public void delete(Long id) {
        requireExists(id);
        long childDept = deptMapper.selectCount(new LambdaQueryWrapper<Department>()
                .eq(Department::getInstitutionId, id));
        long childDoc = doctorMapper.selectCount(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getInstitutionId, id));
        if (childDept + childDoc > 0)
            throw new BusinessException(ResultCode.REFERENCE_CONFLICT.getCode(), "该机构下存在科室或医师,无法删除");
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getInstitutionId, id).eq(User::getRole, RoleConstants.INSTITUTION)
                .set(User::getStatus, 0).set(User::getInstitutionId, null));
        instMapper.deleteById(id);
    }

    private MedicalInstitution requireExists(Long id) {
        MedicalInstitution m = instMapper.selectById(id);
        if (m == null) throw new BusinessException(ResultCode.NOT_FOUND);
        return m;
    }
}
