package com.huiyi.modules.demand;

import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "临床用药反馈")
@RestController
@RequestMapping("/api/v1/demands")
@RequiredArgsConstructor
public class DrugDemandController {

    private final DrugDemandService service;

    @Operation(summary = "提交反馈(医师)")
    @PostMapping
    @RequiresRole({RoleConstants.DOCTOR})
    @OperationLog(module = "临床反馈", operation = "提交")
    public R<Long> create(@Valid @RequestBody DemandCreateDTO dto) {
        return R.ok(service.create(dto));
    }

    @Operation(summary = "反馈列表(按身份隔离:医师本人/公司本公司/机构本机构医师只读/管理员全部;带回反馈人/医院/公司名)")
    @GetMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY, RoleConstants.INSTITUTION, RoleConstants.DOCTOR})
    public R<PageResult<DemandVO>> page(@RequestParam(required = false) Integer status,
                                        @RequestParam(required = false) Integer demandType,
                                        @RequestParam(required = false) Integer urgency,
                                        @RequestParam(required = false) Boolean unassigned,
                                        @RequestParam(defaultValue = "1") int pageNum,
                                        @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.page(status, demandType, urgency, unassigned, pageNum, pageSize));
    }

    @Operation(summary = "撤回反馈(医师,仅待处理)")
    @PutMapping("/{id}/withdraw")
    @RequiresRole({RoleConstants.DOCTOR})
    @OperationLog(module = "临床反馈", operation = "撤回")
    public R<Void> withdraw(@PathVariable Long id) {
        service.withdraw(id);
        return R.ok();
    }

    @Operation(summary = "受理反馈(公司,待处理→处理中)")
    @PutMapping("/{id}/accept")
    @RequiresRole({RoleConstants.COMPANY})
    @OperationLog(module = "临床反馈", operation = "受理")
    public R<Void> accept(@PathVariable Long id) {
        service.accept(id);
        return R.ok();
    }

    @Operation(summary = "标记已满足(公司,处理中→已满足;reply 选填)")
    @PutMapping("/{id}/satisfy")
    @RequiresRole({RoleConstants.COMPANY})
    @OperationLog(module = "临床反馈", operation = "标记已满足")
    public R<Void> satisfy(@PathVariable Long id, @RequestBody(required = false) DemandHandleDTO dto) {
        service.satisfy(id, dto);
        return R.ok();
    }

    @Operation(summary = "驳回反馈(公司,处理中→已驳回;reply 必填)")
    @PutMapping("/{id}/reject")
    @RequiresRole({RoleConstants.COMPANY})
    @OperationLog(module = "临床反馈", operation = "驳回")
    public R<Void> reject(@PathVariable Long id, @Valid @RequestBody DemandHandleDTO dto) {
        service.reject(id, dto);
        return R.ok();
    }

    @Operation(summary = "指派公司(管理员,归属未关联反馈)")
    @PutMapping("/{id}/assign")
    @RequiresRole({RoleConstants.ADMIN})
    @OperationLog(module = "临床反馈", operation = "指派")
    public R<Void> assign(@PathVariable Long id, @Valid @RequestBody DemandAssignDTO dto) {
        service.assign(id, dto);
        return R.ok();
    }
}
