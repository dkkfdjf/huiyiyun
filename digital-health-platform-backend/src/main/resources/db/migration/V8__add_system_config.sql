-- 慧医云 通用系统配置表(Flyway V8)。
-- KV 结构:config_key 作主键,单行按 key 取值,最适合"管理员可改的少量全局开关"。
-- 当前承载「登录滑块验证模式」(管理员可关,防阿里云费用超限);
-- 未来承载本地知识库 AI 问答等开关,复用同一张表。
-- 属"无租户隔离的全局参照数据",接入 Redis 缓存(SystemConfigService 读穿/写删)。

CREATE TABLE `system_config` (
  `config_key`   VARCHAR(64)  NOT NULL COMMENT '配置键',
  `config_value` VARCHAR(255) DEFAULT NULL COMMENT '配置值',
  `description`  VARCHAR(255) DEFAULT NULL COMMENT '说明',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by`   BIGINT       DEFAULT NULL COMMENT '操作人 user.id',
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置(KV,管理员可改)';

-- 种子:登录滑块验证模式。
-- enabled=启用(前端滑块+后端阿里云二次校验,计费,默认);
-- pass   =关闭放行(不校验滑块,登录照常通过,0 阿里云调用);
-- lock   =关闭并锁定(任何账号都无法登录,纯冻结止损,0 阿里云调用)。
INSERT INTO `system_config`(`config_key`,`config_value`,`description`) VALUES
  ('captcha.mode','enabled','登录滑块验证模式:enabled 启用(计费)/pass 关闭放行/lock 关闭并锁定禁登');
