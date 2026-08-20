package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** append-only 日志:保留 180 天 */
@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String operation;
    private String targetType;
    private String targetId;
    private String method;
    private String requestUrl;
    private String requestParam;
    private String beforeData;
    private String afterData;
    private Long costTime;
    private LocalDateTime operationTime;
    private String ip;
    @TableField("`sensitive`")   // sensitive 是 MySQL 保留字,必须反引号引用,否则 SELECT/WHERE 语法错
    private Integer sensitive;   // 0普通/1敏感(切面自动判定:命中敏感表「用户/认证」写操作,或 删除/停用/重置/密码/权限 等关键词)
}
