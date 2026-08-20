package com.huiyi.modules.auth;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 账号修改入参:不动登录名/密码(密码走重置接口)。
 * role 变更与 status 在服务层做"保底管理员/禁自删"校验。
 */
@Data
public class UserUpdateDTO {
    private String realName;

    @NotNull(message = "请选择角色")
    private Integer role;

    private Long companyId;
    private Long institutionId;
    private String phone;
    private String email;
    private Integer status;        // 可空:不调整状态
}
