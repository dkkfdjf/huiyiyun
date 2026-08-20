package com.huiyi.modules.auth;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginVO {
    private String token;
    private Integer role;          // 0管理员/1公司/2机构管理员/3医师/4游客
    private Long companyId;
    private Long institutionId;
    private String realName;
    private LocalDateTime expireAt;
}
