package com.huiyi.modules.department;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.ResultCode;
import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.system.entity.Department;
import com.huiyi.modules.system.entity.Doctor;
import com.huiyi.modules.system.mapper.DepartmentMapper;
import com.huiyi.modules.system.mapper.DoctorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 科室管理。科室隶属于机构,同一机构下科室名唯一。
 * 管理员维护全局目录;医疗机构管理员仅本院(行级隔离:新增强制归属本院、改删校验归属)。
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentMapper deptMapper;
    private final DoctorMapper doctorMapper;

    /** 列表:机构角色强制本院;否则按 institutionId(为空返回全部),按 sort、id 升序。附带各科室医师数。 */
    public List<DepartmentVO> list(Long institutionId) {
        CurrentUser u = SecurityContextHolder.get();
        Long scope = (u != null && u.getRole() == RoleConstants.INSTITUTION) ? u.getInstitutionId() : institutionId;
        LambdaQueryWrapper<Department> w = new LambdaQueryWrapper<>();
        if (scope != null) w.eq(Department::getInstitutionId, scope);
        w.orderByAsc(Department::getSort).orderByAsc(Department::getId);
        List<Department> depts = deptMapper.selectList(w);
        if (depts.isEmpty()) return List.of();

        // 同一 scope(机构)域内,一次拉出在职医师按 departmentId 计数;与列表同域,计数不会跨机构串读。
        Map<Long, Long> cntByDept = doctorMapper.selectList(new LambdaQueryWrapper<Doctor>()
                        .eq(scope != null, Doctor::getInstitutionId, scope)
                        .isNotNull(Doctor::getDepartmentId))
                .stream()
                .filter(d -> d.getDepartmentId() != null)
                .collect(Collectors.groupingBy(Doctor::getDepartmentId, Collectors.counting()));

        return depts.stream().map(d -> {
            DepartmentVO vo = new DepartmentVO();
            BeanUtils.copyProperties(d, vo);
            vo.setDoctorCount(cntByDept.getOrDefault(d.getId(), 0L));
            return vo;
        }).toList();
    }

    /** 新增:同一机构下科室名查重。机构角色强制归属本院(忽略入参)。 */
    public Long save(DepartmentSaveDTO dto) {
        Long instId = resolveInstitution(dto.getInstitutionId());
        long dup = deptMapper.selectCount(new LambdaQueryWrapper<Department>()
                .eq(Department::getInstitutionId, instId)
                .eq(Department::getName, dto.getName().trim()));
        if (dup > 0)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "该机构下已存在同名科室");

        Department d = new Department();
        d.setInstitutionId(instId);
        d.setName(dto.getName().trim());
        d.setSort(dto.getSort() == null ? 0 : dto.getSort());
        deptMapper.insert(d);
        return d.getId();
    }

    /** 修改:归属机构固定不变(防止跨机构移动);同名查重排除自身。机构角色仅本院。 */
    public void update(Long id, DepartmentSaveDTO dto) {
        Department d = requireExists(id);
        requireOwnedByInstitution(d);
        long dup = deptMapper.selectCount(new LambdaQueryWrapper<Department>()
                .eq(Department::getInstitutionId, d.getInstitutionId())
                .eq(Department::getName, dto.getName().trim())
                .ne(Department::getId, id));
        if (dup > 0)
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "该机构下已存在同名科室");

        d.setName(dto.getName().trim());
        if (dto.getSort() != null) d.setSort(dto.getSort());
        deptMapper.updateById(d);
    }

    /** 软删:存在医师时阻止(防孤儿数据)。机构角色仅本院。 */
    public void delete(Long id) {
        Department d = requireExists(id);
        requireOwnedByInstitution(d);
        long child = doctorMapper.selectCount(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getDepartmentId, id));
        if (child > 0)
            throw new BusinessException(ResultCode.REFERENCE_CONFLICT.getCode(), "该科室下存在医师,无法删除");
        deptMapper.deleteById(id);
    }

    private Department requireExists(Long id) {
        Department d = deptMapper.selectById(id);
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

    /** 机构角色归属校验(管理员不限):跨院操作直接拒绝。 */
    private void requireOwnedByInstitution(Department d) {
        CurrentUser u = SecurityContextHolder.get();
        if (u != null && u.getRole() == RoleConstants.INSTITUTION
                && !Objects.equals(d.getInstitutionId(), u.getInstitutionId())) {
            throw new BusinessException(ResultCode.NO_DATA_PERMISSION);
        }
    }
}
