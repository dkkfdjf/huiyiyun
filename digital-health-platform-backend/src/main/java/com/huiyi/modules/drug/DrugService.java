package com.huiyi.modules.drug;

import com.huiyi.common.security.RoleConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.ResultCode;
import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.system.entity.Drug;
import com.huiyi.modules.system.mapper.DrugMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DrugService {

    private final DrugMapper drugMapper;

    /** 分页查询:管理员全局,公司按 companyId;支持名称模糊与上下架筛选。 */
    public PageResult<Drug> page(String name, Integer status, int pageNum, int pageSize) {
        CurrentUser u = SecurityContextHolder.get();
        LambdaQueryWrapper<Drug> w = new LambdaQueryWrapper<>();
        if (u.getRole() == RoleConstants.COMPANY) w.eq(Drug::getCompanyId, u.getCompanyId());
        if (name != null && !name.isBlank()) w.like(Drug::getName, name.trim());
        if (status != null) w.eq(Drug::getStatus, status);
        w.orderByDesc(Drug::getCreateTime);
        Page<Drug> p = drugMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 新增:批准文号本公司范围内查重。 */
    public Long save(DrugSaveDTO dto) {
        CurrentUser u = SecurityContextHolder.get();
        Long dup = drugMapper.selectCount(new LambdaQueryWrapper<Drug>()
                .eq(Drug::getApprovalNo, dto.getApprovalNo())
                .eq(u.getRole() != RoleConstants.ADMIN, Drug::getCompanyId, u.getCompanyId()));
        if (dup > 0) throw new BusinessException(ResultCode.APPROVAL_DUP);

        Drug d = new Drug();
        d.setName(dto.getName());
        d.setSpecification(dto.getSpecification());
        d.setDosageForm(dto.getDosageForm());
        d.setApprovalNo(dto.getApprovalNo());
        d.setUnit(dto.getUnit());
        d.setProducer(dto.getProducer());
        d.setCompanyId(u.getCompanyId());           // 管理员为 null(全局药品);公司为本公司
        d.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        drugMapper.insert(d);
        return d.getId();
    }

    /** 修改:归属校验 + 文号变更查重(排除自身)。 */
    public void update(Long id, DrugSaveDTO dto) {
        Drug d = requireOwned(id);
        if (dto.getApprovalNo() != null && !dto.getApprovalNo().equals(d.getApprovalNo())) {
            CurrentUser u = SecurityContextHolder.get();
            Long dup = drugMapper.selectCount(new LambdaQueryWrapper<Drug>()
                    .eq(Drug::getApprovalNo, dto.getApprovalNo())
                    .eq(u.getRole() != RoleConstants.ADMIN, Drug::getCompanyId, u.getCompanyId())
                    .ne(Drug::getId, id));
            if (dup > 0) throw new BusinessException(ResultCode.APPROVAL_DUP);
        }
        d.setName(dto.getName());
        d.setSpecification(dto.getSpecification());
        d.setDosageForm(dto.getDosageForm());
        d.setApprovalNo(dto.getApprovalNo());
        d.setUnit(dto.getUnit());
        d.setProducer(dto.getProducer());
        if (dto.getStatus() != null) d.setStatus(dto.getStatus());
        drugMapper.updateById(d);
    }

    /** 上下架:status 为空则翻转当前态。 */
    public void toggleStatus(Long id, Integer status) {
        Drug d = requireOwned(id);
        d.setStatus(status == null ? (d.getStatus() == null || d.getStatus() == 1 ? 0 : 1) : status);
        drugMapper.updateById(d);
    }

    /** 软删(@TableLogic)。 */
    public void delete(Long id) {
        requireOwned(id);
        drugMapper.deleteById(id);
    }

    /** 取出并校验归属:管理员放行,公司仅限本公司;不存在→404,越权→4004。 */
    private Drug requireOwned(Long id) {
        Drug d = drugMapper.selectById(id);
        if (d == null) throw new BusinessException(ResultCode.NOT_FOUND);
        CurrentUser u = SecurityContextHolder.get();
        if (u.getRole() != RoleConstants.ADMIN && !u.getCompanyId().equals(d.getCompanyId()))
            throw new BusinessException(ResultCode.NO_DATA_PERMISSION);
        return d;
    }
}
