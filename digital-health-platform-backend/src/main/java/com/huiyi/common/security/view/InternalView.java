package com.huiyi.common.security.view;

/**
 * 内部(敏感)字段视图。标在此接口上的字段属敏感数据(手机/邮箱/信用代码/许可证号/联系人/联系电话),
 * 仅非游客角色可见。游客响应以 {@link GuestView} 序列化时,这些字段被自动排除。
 * <p>非游客角色不激活任何视图(=全字段),行为不变。
 */
public interface InternalView {
}
