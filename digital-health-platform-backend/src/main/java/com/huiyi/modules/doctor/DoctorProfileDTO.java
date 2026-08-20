package com.huiyi.modules.doctor;

import lombok.Data;

/**
 * 医师自助维护:仅联系方式(电话/邮箱)。
 * 姓名 / 科室 / 职称 / 归属机构由管理员或机构管理员维护,医师不可自行修改。
 */
@Data
public class DoctorProfileDTO {
    private String phone;
    private String email;
}
