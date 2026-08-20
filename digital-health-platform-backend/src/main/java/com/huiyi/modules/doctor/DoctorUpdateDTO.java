package com.huiyi.modules.doctor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 医师修改入参:不动账号(username/密码属账号管理范畴,不在本接口)。
 */
@Data
public class DoctorUpdateDTO {
    @NotBlank(message = "医师姓名不能为空")
    private String name;

    @NotNull(message = "请选择所属机构")
    private Long institutionId;

    private Long departmentId;
    private String title;
    private String phone;
    private String email;
}
