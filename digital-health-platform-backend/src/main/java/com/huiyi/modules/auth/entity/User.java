package com.huiyi.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.huiyi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {
    private String username;
    /** 密码哈希:只允许写入(反序列化),永不随 JSON 返回前端。 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String realName;
    private Integer role;            // 0管理员/1公司/2机构管理员/3医师
    private Long companyId;
    private Long institutionId;
    private String phone;
    private String email;
    private Integer status;          // 0禁用/1正常(登录唯一依据)
    private Integer loginFailCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginTime;
}
