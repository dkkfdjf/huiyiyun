package com.huiyi.modules.department;

import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "科室管理")
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "科室列表(机构角色强制本院;institutionId 为空返回全部;附医师数)")
    @GetMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.INSTITUTION, RoleConstants.GUEST})
    public R<List<DepartmentVO>> list(@RequestParam(required = false) Long institutionId) {
        return R.ok(departmentService.list(institutionId));
    }

    @OperationLog(module = "科室", operation = "新增")
    @Operation(summary = "新增科室(机构角色强制归属本院)")
    @PostMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.INSTITUTION})
    public R<Long> create(@Valid @RequestBody DepartmentSaveDTO dto) {
        return R.ok(departmentService.save(dto));
    }

    @OperationLog(module = "科室", operation = "编辑")
    @Operation(summary = "修改科室(机构角色仅本院)")
    @PutMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.INSTITUTION})
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody DepartmentSaveDTO dto) {
        departmentService.update(id, dto);
        return R.ok();
    }

    @OperationLog(module = "科室", operation = "删除")
    @Operation(summary = "删除科室(软删;机构角色仅本院;有医师则禁止)")
    @DeleteMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.INSTITUTION})
    public R<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return R.ok();
    }
}
