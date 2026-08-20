package com.huiyi.modules.dict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.modules.system.entity.SysDict;
import com.huiyi.modules.system.mapper.SysDictMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据字典(全局参照)。前端下拉框按 dictType 拉取选项,不再写死。
 * 字典为只读参照(由 DataInitializer 种子维护),全角色可读。
 * 已种子的类型:user_role / audit_status / drug_status / demand_status / demand_type / demand_urgency / policy_type。
 */
@Tag(name = "数据字典")
@RestController
@RequestMapping("/api/v1/dicts")
@RequiredArgsConstructor
public class DictController {

    private final SysDictMapper dictMapper;

    @Operation(summary = "按类型查字典项(下拉用)")
    @GetMapping("/{type}")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY, RoleConstants.INSTITUTION, RoleConstants.DOCTOR})
    public R<List<SysDict>> listByType(@PathVariable String type) {
        return R.ok(dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictType, type)
                .orderByAsc(SysDict::getSort)));
    }
}
