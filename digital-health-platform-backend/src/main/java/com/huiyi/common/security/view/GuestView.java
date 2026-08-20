package com.huiyi.common.security.view;

/**
 * 游客只读序列化视图。
 * <p>激活此视图序列化时:无 @JsonView 注解的字段照常输出;标了 {@link InternalView} 的敏感字段被排除。
 * 即"白名单式脱敏"——游客永远拿不到 InternalView 字段(联系方式/证件号),与具体接口无关。
 */
public interface GuestView {
}
