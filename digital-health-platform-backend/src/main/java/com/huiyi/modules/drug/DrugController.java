package com.huiyi.modules.drug;

import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.modules.system.entity.Drug;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "药品管理")
@RestController
@RequestMapping("/api/v1/drugs")
@RequiredArgsConstructor
public class DrugController {

    private final DrugService drugService;

    @Operation(summary = "药品分页查询")
    @GetMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY, RoleConstants.INSTITUTION, RoleConstants.DOCTOR})
    public R<PageResult<Drug>> page(@RequestParam(required = false) String name,
                                    @RequestParam(required = false) Integer status,
                                    @RequestParam(defaultValue = "1") int pageNum,
                                    @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(drugService.page(name, status, pageNum, pageSize));
    }

    @OperationLog(module = "药品", operation = "新增")
    @Operation(summary = "新增药品")
    @PostMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<Long> create(@Valid @RequestBody DrugSaveDTO dto) {
        return R.ok(drugService.save(dto));
    }

    @OperationLog(module = "药品", operation = "编辑")
    @Operation(summary = "修改药品")
    @PutMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody DrugSaveDTO dto) {
        drugService.update(id, dto);
        return R.ok();
    }

    @OperationLog(module = "药品", operation = "上下架")
    @Operation(summary = "药品上下架(status 为空则翻转)")
    @PutMapping("/{id}/status")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<Void> toggleStatus(@PathVariable Long id,
                                @RequestParam(required = false) Integer status) {
        drugService.toggleStatus(id, status);
        return R.ok();
    }

    @OperationLog(module = "药品", operation = "删除")
    @Operation(summary = "删除药品(软删)")
    @DeleteMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY})
    public R<Void> delete(@PathVariable Long id) {
        drugService.delete(id);
        return R.ok();
    }
}
