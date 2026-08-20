package com.huiyi.modules.policy;

import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.modules.system.entity.CompanyPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 医药公司政策(药企公告)。
 * 读(/page、/latest)开放四角色——公司仅本公司、机构/医师作为公告全量只读;
 * 写(POST/PUT/DELETE)仅本公司药企(管理员只读全量,不代发)。
 */
@Tag(name = "医药公司政策")
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class CompanyPolicyController {

    private final CompanyPolicyService policyService;

    @Operation(summary = "政策分页(公司仅本公司;管理员/机构/医师全量,可按公司筛)")
    @GetMapping("/page")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY, RoleConstants.INSTITUTION, RoleConstants.DOCTOR, RoleConstants.GUEST})
    public R<PageResult<CompanyPolicyVO>> page(@RequestParam(required = false) String title,
                                             @RequestParam(required = false) Integer policyType,
                                             @RequestParam(required = false) Long companyId,
                                             @RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(policyService.page(title, policyType, companyId, pageNum, pageSize));
    }

    @Operation(summary = "最新政策(仪表盘公告用)")
    @GetMapping("/latest")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY, RoleConstants.INSTITUTION, RoleConstants.DOCTOR, RoleConstants.GUEST})
    public R<List<CompanyPolicyVO>> latest(@RequestParam(defaultValue = "8") int n) {
        return R.ok(policyService.latest(n));
    }

    @OperationLog(module = "药企政策", operation = "发布")
    @Operation(summary = "发布公告(仅本公司药企;自动归属当前登录公司)")
    @PostMapping
    @RequiresRole({RoleConstants.COMPANY})
    public R<Long> create(@Valid @RequestBody CompanyPolicySaveDTO dto) {
        return R.ok(policyService.save(dto));
    }

    @OperationLog(module = "药企政策", operation = "编辑")
    @Operation(summary = "修改公告(仅本公司药企)")
    @PutMapping("/{id}")
    @RequiresRole({RoleConstants.COMPANY})
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody CompanyPolicySaveDTO dto) {
        policyService.update(id, dto);
        return R.ok();
    }

    @OperationLog(module = "药企政策", operation = "删除")
    @Operation(summary = "删除公告(仅本公司药企;软删)")
    @DeleteMapping("/{id}")
    @RequiresRole({RoleConstants.COMPANY})
    public R<Void> delete(@PathVariable Long id) {
        policyService.delete(id);
        return R.ok();
    }
}
