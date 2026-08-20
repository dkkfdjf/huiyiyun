package com.huiyi.modules.material;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.ResultCode;
import com.huiyi.modules.system.entity.EssentialMaterial;
import com.huiyi.modules.system.mapper.EssentialMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 必备材料管理。管理员统一维护(增删改查),全角色只读查阅。
 * 不与医药公司/药品关联,故无行级数据隔离。
 */
@Service
@RequiredArgsConstructor
public class EssentialMaterialService {

    private final EssentialMaterialMapper materialMapper;

    /** 分页(全角色可读):按名称模糊 + 类别精确。 */
    @Cacheable(value = "materials:page", sync = true)   // 高频读·全角色;管理员写时整表驱逐(见下)
    public PageResult<EssentialMaterial> page(String name, String category, int pageNum, int pageSize) {
        LambdaQueryWrapper<EssentialMaterial> w = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) w.like(EssentialMaterial::getName, name);
        if (category != null && !category.isBlank()) w.eq(EssentialMaterial::getCategory, category);
        w.orderByDesc(EssentialMaterial::getUpdateTime);
        Page<EssentialMaterial> p = materialMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    @CacheEvict(value = "materials:page", allEntries = true)   // 任意写都打乱排序,整表驱逐最稳
    public Long save(EssentialMaterialSaveDTO dto) {
        EssentialMaterial m = new EssentialMaterial();
        applyFields(m, dto);
        materialMapper.insert(m);
        return m.getId();
    }

    @CacheEvict(value = "materials:page", allEntries = true)
    public void update(Long id, EssentialMaterialSaveDTO dto) {
        EssentialMaterial m = materialMapper.selectById(id);
        if (m == null) throw new BusinessException(ResultCode.NOT_FOUND);
        applyFields(m, dto);
        materialMapper.updateById(m);
    }

    @CacheEvict(value = "materials:page", allEntries = true)
    public void delete(Long id) {
        if (materialMapper.selectById(id) == null) throw new BusinessException(ResultCode.NOT_FOUND);
        materialMapper.deleteById(id);
    }

    private void applyFields(EssentialMaterial m, EssentialMaterialSaveDTO dto) {
        m.setName(dto.getName().trim());
        m.setCategory(dto.getCategory().trim());
        m.setSpecification(dto.getSpecification());
        m.setUnit(dto.getUnit());
        m.setContent(dto.getContent());
    }
}
