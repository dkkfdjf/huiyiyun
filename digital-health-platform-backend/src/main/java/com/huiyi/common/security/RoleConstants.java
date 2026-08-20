package com.huiyi.common.security;

/**
 * 角色常量:对应 user.role 列(TINYINT)。
 * <p>代码中一律用这些常量,禁止裸写 0/1/2/3/4;DB 仍存 int(索引快、JWT 紧凑、行业标准),
 * 前端经 sys_dict(user_role)显示中文(管理员/药企/医疗机构/医师/游客)。
 */
public final class RoleConstants {
    public static final int ADMIN = 0;        // 系统管理员
    public static final int COMPANY = 1;      // 医药公司用户
    public static final int INSTITUTION = 2;  // 医疗机构管理员
    public static final int DOCTOR = 3;       // 医师
    public static final int GUEST = 4;        // 游客(只读体验,临时令牌,无 user 行)

    private RoleConstants() {}
}
