package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置(KV):管理员可改的少量全局开关。config_key 作主键(INPUT)。
 * 当前键:captcha.mode(登录滑块验证模式,见 {@link com.huiyi.modules.system.CaptchaMode})。
 * 未来扩展本地知识库 AI 问答等开关,直接加行复用。
 */
@Data
@TableName("system_config")
public class SystemConfig {
    @TableId(type = IdType.INPUT)
    private String configKey;
    private String configValue;
    private String description;
    private LocalDateTime updateTime;
    private Long updatedBy;
}
