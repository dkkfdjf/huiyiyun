package com.huiyi.modules.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huiyi.modules.knowledge.model.KbChatLog;

/** 知识库对话日志 Mapper:高频计数 + 管理员分页均用 BaseMapper 通用方法,无需自定义 SQL。 */
public interface KbChatLogMapper extends BaseMapper<KbChatLog> {
}
