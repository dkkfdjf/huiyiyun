package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** append-only 日志:保留 180 天 */
@Data
@TableName("login_log")
public class LoginLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private LocalDateTime loginTime;
    private String ip;
    private String userAgent;
    private Integer loginResult;     // 0失败/1成功
    private String failReason;
    private Integer anomaly;         // 0正常/1异常IP(老用户首次从该IP登录,疑似异地/盗号)
    private String anomalyReason;    // 异常原因
}
