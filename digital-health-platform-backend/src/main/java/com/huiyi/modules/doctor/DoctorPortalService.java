package com.huiyi.modules.doctor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huiyi.common.result.PageResult;
import com.huiyi.modules.system.entity.Drug;
import com.huiyi.modules.system.mapper.DrugMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 医师工作台只读门户:为临床反馈选药提供上架药品检索。
 * 复用 system 域 DrugMapper(同 InstitutionPortal 跨域读取既有模式),不改药品模块。
 */
@Service
@RequiredArgsConstructor
public class DoctorPortalService {

    private final DrugMapper drugMapper;

    /** 上架药品(status=1)检索,名称模糊;供医师提交反馈时从已有药品中选择。 */
    public PageResult<Drug> pageDrugs(String name, int pageNum, int pageSize) {
        LambdaQueryWrapper<Drug> w = new LambdaQueryWrapper<Drug>()
                .eq(Drug::getStatus, 1)
                .orderByDesc(Drug::getCreateTime);
        if (name != null && !name.isBlank()) w.like(Drug::getName, name.trim());
        Page<Drug> p = drugMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }
}
