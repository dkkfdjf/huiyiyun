package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** append-only 错误日志:保留 30 天(无 deleted 软删列)。设计表 16。 */
@Data
@TableName("error_log")
public class ErrorLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDateTime errorTime;
    private Long userId;
    private String requestUrl;
    private String requestParam;
    private String errorType;
    private String errorMessage;
    private String stackTrace;
    private String ip;
}
