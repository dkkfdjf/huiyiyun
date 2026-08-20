package com.huiyi.modules.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 账号新增入参(管理员统一建号):按角色绑定公司/机构由服务层校验。
 * companyId/institutionId 是否必填取决于 role(见 UserService#validateBinding)。
 */
@Data
public class UserSaveDTO {
    @NotBlank(message = "登录名不能为空")
    @Size(min = 3, max = 50, message = "登录名长度 3~50")
    private String username;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度 6~50")
    private String password;

    private String realName;

    @NotNull(message = "请选择角色")
    private Integer role;          // 0管理员/1公司/2机构管理员/3医师

    private Long companyId;        // role=1 必填
    private Long institutionId;    // role=2/3 必填
    private String phone;
    private String email;
}
