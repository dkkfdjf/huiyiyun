package com.huiyi.modules.company;

import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.system.entity.PharmaCompany;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "药企管理")
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class PharmaCompanyController {

    private final PharmaCompanyService companyService;

    /* ———— 目录(下拉)———— */
    @Operation(summary = "正常态药企列表(下拉用)")
    @GetMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.GUEST})
    public R<List<PharmaCompany>> list() {
        return R.ok(companyService.listActive());
    }

    /* ———— 本公司自管(M1,公司用户)———— 字面量路径放在 /{id} 前,优先匹配 */
    @Operation(summary = "本公司信息(公司用户自管)")
    @GetMapping("/me")
    @RequiresRole({RoleConstants.COMPANY})
    public R<PharmaCompany> me() {
        return R.ok(companyService.getMe(SecurityContextHolder.get().getCompanyId()));
    }

    @OperationLog(module = "药企", operation = "修改资料")
    @Operation(summary = "维护本公司联系方式/地址")
    @PutMapping("/me")
    @RequiresRole({RoleConstants.COMPANY})
    public R<Void> updateMe(@Valid @RequestBody CompanyProfileDTO dto) {
        companyService.updateMe(SecurityContextHolder.get().getCompanyId(), dto);
        return R.ok();
    }

    @Operation(summary = "本公司发展概览(药品/网点/政策/待处理反馈)")
    @GetMapping("/development")
    @RequiresRole({RoleConstants.COMPANY})
    public R<Map<String, Object>> development() {
        return R.ok(companyService.development(SecurityContextHolder.get().getCompanyId()));
    }

    /* ———— 管理 CRUD + 启停(管理员)———— */
    @Operation(summary = "药企分页查询(支持名称/状态筛选)")
    @GetMapping("/page")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.GUEST})
    public R<PageResult<PharmaCompany>> page(@RequestParam(required = false) String name,
                                             @RequestParam(required = false) Integer auditStatus,
                                             @RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(companyService.page(name, auditStatus, pageNum, pageSize));
    }

    @Operation(summary = "药企详情")
    @GetMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.GUEST})
    public R<PharmaCompany> detail(@PathVariable Long id) {
        return R.ok(companyService.detail(id));
    }

    @OperationLog(module = "药企", operation = "新增")
    @Operation(summary = "新增药企(管理员录入默认正常)")
    @PostMapping
    @RequiresRole({RoleConstants.ADMIN})
    public R<Long> create(@Valid @RequestBody PharmaCompanySaveDTO dto) {
        return R.ok(companyService.save(dto));
    }

    @OperationLog(module = "药企", operation = "编辑")
    @Operation(summary = "修改药企(不改审核态)")
    @PutMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN})
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody PharmaCompanySaveDTO dto) {
        companyService.update(id, dto);
        return R.ok();
    }

    @OperationLog(module = "药企", operation = "启停")
    @Operation(summary = "药企启停(status 为空则在正常/停用间翻转)")
    @PutMapping("/{id}/status")
    @RequiresRole({RoleConstants.ADMIN})
    public R<Void> toggleStatus(@PathVariable Long id,
                                @RequestParam(required = false) Integer status) {
        companyService.toggleStatus(id, status);
        return R.ok();
    }

    @OperationLog(module = "药企", operation = "删除")
    @Operation(summary = "删除药企(软删,存在引用时拒绝)")
    @DeleteMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN})
    public R<Void> delete(@PathVariable Long id) {
        companyService.delete(id);
        return R.ok();
    }
}
