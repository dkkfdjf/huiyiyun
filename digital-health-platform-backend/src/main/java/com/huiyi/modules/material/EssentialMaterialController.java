package com.huiyi.modules.material;

import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.modules.system.entity.EssentialMaterial;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 必备材料。读(/page)开放全角色;写(POST/PUT/DELETE)仅管理员。
 */
@Tag(name = "必备材料")
@RestController
@RequestMapping("/api/v1/materials")
@RequiredArgsConstructor
public class EssentialMaterialController {

    private final EssentialMaterialService materialService;

    @Operation(summary = "材料分页(全角色可读)")
    @GetMapping("/page")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY, RoleConstants.INSTITUTION, RoleConstants.DOCTOR, RoleConstants.GUEST})
    public R<PageResult<EssentialMaterial>> page(@RequestParam(required = false) String name,
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(materialService.page(name, category, pageNum, pageSize));
    }

    @OperationLog(module = "材料", operation = "新增")
    @Operation(summary = "新增材料")
    @PostMapping
    @RequiresRole({RoleConstants.ADMIN})
    public R<Long> create(@Valid @RequestBody EssentialMaterialSaveDTO dto) {
        return R.ok(materialService.save(dto));
    }

    @OperationLog(module = "材料", operation = "编辑")
    @Operation(summary = "修改材料")
    @PutMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN})
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody EssentialMaterialSaveDTO dto) {
        materialService.update(id, dto);
        return R.ok();
    }

    @OperationLog(module = "材料", operation = "删除")
    @Operation(summary = "删除材料(软删)")
    @DeleteMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN})
    public R<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return R.ok();
    }
}
