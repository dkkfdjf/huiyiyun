package com.huiyi.common.security;

import lombok.Data;

@Data
public class CurrentUser {
    private Long userId;
    private String username;
    private Integer role;            // 0管理员/1公司/2机构管理员/3医师
    private Long companyId;
    private Long institutionId;
    private Long doctorId;
}
