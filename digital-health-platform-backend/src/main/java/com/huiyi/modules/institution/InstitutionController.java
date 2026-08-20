package com.huiyi.modules.institution;

import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.modules.system.entity.MedicalInstitution;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "医疗机构管理")
@RestController
@RequestMapping("/api/v1/institutions")
@RequiredArgsConstructor
public class InstitutionController {

    private final InstitutionService institutionService;

    @Operation(summary = "机构列表(下拉用)")
    @GetMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.GUEST})
    public R<List<MedicalInstitution>> list() {
        return R.ok(institutionService.list());
    }

    @Operation(summary = "机构分页查询")
    @GetMapping("/page")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.GUEST})
    public R<PageResult<MedicalInstitution>> page(@RequestParam(required = false) String name,
                                                   @RequestParam(required = false) Long cityId,
                                                   @RequestParam(required = false) Integer auditStatus,
                                                   @RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(institutionService.page(name, cityId, auditStatus, pageNum, pageSize));
    }

    @OperationLog(module = "医疗机构", operation = "新增")
    @Operation(summary = "新增机构")
    @PostMapping
    @RequiresRole({RoleConstants.ADMIN})
    public R<Long> create(@Valid @RequestBody InstitutionSaveDTO dto) {
        return R.ok(institutionService.save(dto));
    }

    @OperationLog(module = "医疗机构", operation = "编辑")
    @Operation(summary = "修改机构")
    @PutMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN})
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody InstitutionSaveDTO dto) {
        institutionService.update(id, dto);
        return R.ok();
    }

    @OperationLog(module = "医疗机构", operation = "启停")
    @Operation(summary = "机构启停(status 为空则在正常/停用间翻转)")
    @PutMapping("/{id}/status")
    @RequiresRole({RoleConstants.ADMIN})
    public R<Void> toggleStatus(@PathVariable Long id,
                                @RequestParam(required = false) Integer status) {
        institutionService.toggleStatus(id, status);
        return R.ok();
    }

    @OperationLog(module = "医疗机构", operation = "删除")
    @Operation(summary = "删除机构(软删)")
    @DeleteMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN})
    public R<Void> delete(@PathVariable Long id) {
        institutionService.delete(id);
        return R.ok();
    }
}
