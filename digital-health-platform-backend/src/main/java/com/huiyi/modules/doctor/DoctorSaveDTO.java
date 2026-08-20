package com.huiyi.modules.doctor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 医师新增入参:新增时同时创建一个 role=3 的登录账号(1:1 绑定)。
 */
@Data
public class DoctorSaveDTO {
    @NotBlank(message = "登录名不能为空")
    @Size(min = 3, max = 50, message = "登录名长度 3~50")
    private String username;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度 6~50")
    private String password;

    @NotBlank(message = "医师姓名不能为空")
    private String name;

    @NotNull(message = "请选择所属机构")
    private Long institutionId;

    private Long departmentId;   // 可空:机构级医师不属某科室
    private String title;        // 医师/主治医师/副主任医师/主任医师
    private String phone;
    private String email;
}
