package com.huiyi.modules.city;

import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.modules.system.entity.City;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 城市基础数据。列表全角色可读(机构/网点表单下拉);增删改仅管理员。
 */
@Tag(name = "城市")
@RestController
@RequestMapping("/api/v1/cities")
@RequiredArgsConstructor
public class CityController {

    private final CityService cityService;

    @Operation(summary = "城市列表(下拉用)")
    @GetMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY, RoleConstants.GUEST})
    public R<List<City>> list() {
        return R.ok(cityService.list());
    }

    @Operation(summary = "城市覆盖看板(每城市网点/机构/药企/销售额)")
    @GetMapping("/coverage")
    @RequiresRole({RoleConstants.ADMIN})
    public R<List<CityCoverageVO>> coverage() {
        return R.ok(cityService.coverage());
    }

    @OperationLog(module = "城市", operation = "新增")
    @Operation(summary = "新增城市")
    @PostMapping
    @RequiresRole({RoleConstants.ADMIN})
    public R<Long> create(@Valid @RequestBody CitySaveDTO dto) {
        return R.ok(cityService.save(dto));
    }

    @OperationLog(module = "城市", operation = "编辑")
    @Operation(summary = "修改城市")
    @PutMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN})
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody CitySaveDTO dto) {
        cityService.update(id, dto);
        return R.ok();
    }

    @OperationLog(module = "城市", operation = "删除")
    @Operation(summary = "删除城市(被机构/网点引用时拒绝)")
    @DeleteMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN})
    public R<Void> delete(@PathVariable Long id) {
        cityService.delete(id);
        return R.ok();
    }
}
