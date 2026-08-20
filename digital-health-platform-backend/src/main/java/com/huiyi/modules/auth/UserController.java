package com.huiyi.modules.auth;

import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.auth.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 账号管理(管理员统一建号/改号/重置密码/启停/解锁/软删)。
 * 仅管理员可访问;User.password 标了 @JsonProperty(WRITE_ONLY),返回体不带哈希。
 */
@Tag(name = "账号管理")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "账号分页查询(支持登录名/角色/状态筛选)")
    @GetMapping
    @RequiresRole(RoleConstants.ADMIN)
    public R<PageResult<User>> page(@RequestParam(required = false) String username,
                                    @RequestParam(required = false) Integer role,
                                    @RequestParam(required = false) Integer status,
                                    @RequestParam(defaultValue = "1") int pageNum,
                                    @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(userService.page(username, role, status, pageNum, pageSize));
    }

    @OperationLog(module = "用户", operation = "新增")
    @Operation(summary = "新增账号(按角色绑定公司/机构)")
    @PostMapping
    @RequiresRole(RoleConstants.ADMIN)
    public R<Long> create(@Valid @RequestBody UserSaveDTO dto) {
        return R.ok(userService.save(dto));
    }

    @OperationLog(module = "用户", operation = "编辑")
    @Operation(summary = "修改账号(不含登录名/密码)")
    @PutMapping("/{id}")
    @RequiresRole(RoleConstants.ADMIN)
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        userService.update(id, dto);
        return R.ok();
    }

    @OperationLog(module = "用户", operation = "重置密码")
    @Operation(summary = "重置账号密码(6~50,同时清锁定)")
    @PutMapping("/{id}/password")
    @RequiresRole(RoleConstants.ADMIN)
    public R<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body == null ? null : body.get("password"));
        return R.ok();
    }

    @OperationLog(module = "用户", operation = "修改密码")
    @Operation(summary = "修改自己的登录密码(校验旧密码,6~50;游客无账号不可改密码)")
    @PutMapping("/me/password")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY, RoleConstants.INSTITUTION, RoleConstants.DOCTOR})
    public R<Void> changeMyPassword(@RequestBody Map<String, String> body) {
        userService.changeMyPassword(
                SecurityContextHolder.get().getUserId(),
                body == null ? null : body.get("oldPassword"),
                body == null ? null : body.get("newPassword"));
        return R.ok();
    }

    @OperationLog(module = "用户", operation = "启停")
    @Operation(summary = "启停账号(status 为空则在 正常/禁用 间翻转)")
    @PutMapping("/{id}/status")
    @RequiresRole(RoleConstants.ADMIN)
    public R<Void> toggleStatus(@PathVariable Long id,
                                @RequestParam(required = false) Integer status) {
        userService.toggleStatus(id, status);
        return R.ok();
    }

    @OperationLog(module = "用户", operation = "解锁")
    @Operation(summary = "解锁账号(清失败计数与锁定)")
    @PutMapping("/{id}/unlock")
    @RequiresRole(RoleConstants.ADMIN)
    public R<Void> unlock(@PathVariable Long id) {
        userService.unlock(id);
        return R.ok();
    }

    @OperationLog(module = "用户", operation = "删除")
    @Operation(summary = "删除账号(软删)")
    @DeleteMapping("/{id}")
    @RequiresRole(RoleConstants.ADMIN)
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }
}
