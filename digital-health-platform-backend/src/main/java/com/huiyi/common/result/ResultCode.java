package com.huiyi.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {
    SUCCESS(200, "success"),

    // 1xxx 库存/销售
    STOCK_NOT_ENOUGH(1001, "库存不足"),
    STOCK_NOT_FOUND(1002, "库存记录不存在"),
    DRUG_OFF_SHELF(1003, "药品已下架"),
    QTY_INVALID(1004, "数量非法"),

    // 2xxx 药品
    DRUG_STOCK_DUP(2001, "该网点已铺此药"),
    APPROVAL_DUP(2002, "批准文号已存在"),

    // 3xxx 临床反馈
    DEMAND_HANDLED(3001, "仅待处理可撤回"),
    DEMAND_BAD_TRANSITION(3002, "反馈状态非法转移"),
    DEMAND_NOT_ASSIGNED(3003, "反馈尚未指派公司"),
    DEMAND_REASSIGN_FORBIDDEN(3004, "已满足的反馈不可改派"),

    // 4xxx 账号/权限
    ACCOUNT_LOCKED(4001, "账号已锁定"),
    ACCOUNT_DISABLED(4002, "账号不可用"),
    CAPTCHA_ERROR(4003, "验证码错误"),
    NO_DATA_PERMISSION(4004, "无数据权限"),
    LOGIN_TOO_MANY(4005, "登录尝试过于频繁,请稍后再试"),
    LOGIN_LOCKED(4006, "系统登录已被锁定,请联系管理员"),
    BAD_CREDENTIALS(4007, "用户名或密码错误"),

    // 5xxx 校验 / 通用
    PARAM_INVALID(5001, "参数校验失败"),
    REFERENCE_CONFLICT(5002, "存在引用冲突"),

    // 6xxx 通知
    NOTIF_NOT_FOUND(6001, "通知不存在"),
    NOTIF_FORBIDDEN(6002, "无权操作该通知"),
    UNAUTHORIZED(401, "用户名或密码错误"),
    TOKEN_INVALID(401, "登录已过期,请重新登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    SERVER_ERROR(500, "服务器错误");

    private final int code;
    private final String message;
}
