package com.huiyi.modules.doctor;

import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.system.entity.Doctor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 医师管理。管理员全局;医疗机构管理员仅本院(服务层强制 institutionId 隔离)。
 */
@Tag(name = "医师管理")
@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    /* —— 本医师自管(M5,医师)—— 字面量路径放在 /{id} 前,优先匹配 */
    @Operation(summary = "本医师信息(医师自管)")
    @GetMapping("/me")
    @RequiresRole({RoleConstants.DOCTOR})
    public R<Doctor> me() {
        return R.ok(doctorService.getMe(SecurityContextHolder.get().getDoctorId()));
    }

    @OperationLog(module = "医师", operation = "修改资料")
    @Operation(summary = "维护本医师联系方式(电话/邮箱)")
    @PutMapping("/me")
    @RequiresRole({RoleConstants.DOCTOR})
    public R<Void> updateMe(@RequestBody DoctorProfileDTO dto) {
        doctorService.updateMe(SecurityContextHolder.get().getDoctorId(), dto);
        return R.ok();
    }

    @Operation(summary = "医师分页查询")
    @GetMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.INSTITUTION, RoleConstants.GUEST})
    public R<PageResult<Doctor>> page(@RequestParam(required = false) String name,
                                      @RequestParam(required = false) Long institutionId,
                                      @RequestParam(required = false) Long departmentId,
                                      @RequestParam(required = false) String title,
                                      @RequestParam(defaultValue = "1") int pageNum,
                                      @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(doctorService.page(name, institutionId, departmentId, title, pageNum, pageSize));
    }

    @OperationLog(module = "医师", operation = "新增")
    @Operation(summary = "新增医师(同时创建登录账号)")
    @PostMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.INSTITUTION})
    public R<Long> create(@Valid @RequestBody DoctorSaveDTO dto) {
        return R.ok(doctorService.save(dto));
    }

    @OperationLog(module = "医师", operation = "编辑")
    @Operation(summary = "修改医师")
    @PutMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.INSTITUTION})
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody DoctorUpdateDTO dto) {
        doctorService.update(id, dto);
        return R.ok();
    }

    @OperationLog(module = "医师", operation = "分配科室")
    @Operation(summary = "分配/调配科室(防越院,设计 §4.2)")
    @PutMapping("/{id}/department")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.INSTITUTION})
    public R<Void> assignDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentAssignDTO dto) {
        doctorService.assignDepartment(id, dto.getDepartmentId());
        return R.ok();
    }

    @OperationLog(module = "医师", operation = "删除")
    @Operation(summary = "删除医师(软删档案 + 禁用账号)")
    @DeleteMapping("/{id}")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.INSTITUTION})
    public R<Void> delete(@PathVariable Long id) {
        doctorService.delete(id);
        return R.ok();
    }

    @OperationLog(module = "医师", operation = "重置密码")
    @Operation(summary = "重置医师账号密码(6~50)")
    @PutMapping("/{id}/password")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.INSTITUTION})
    public R<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        doctorService.resetPassword(id, body == null ? null : body.get("password"));
        return R.ok();
    }
}
