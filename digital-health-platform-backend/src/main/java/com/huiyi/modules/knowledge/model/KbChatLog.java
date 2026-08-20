package com.huiyi.modules.knowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库对话日志:每次 /ask 落一条,供管理员审计 + 异常提问检测。
 *
 * 异常标记规则(用户定「0命中 + 高频」):见 {@link com.huiyi.modules.knowledge.KbChatLogService}。
 * 字段驼峰自动映射蛇形列(query_text / answer_text / hit_count / flag_reason / created_at …)。
 */
@Data
@TableName("kb_chat_log")
public class KbChatLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private Integer role;
    private String clientIp;     // 提问来源 IP(游客无 user_id,按此计频/审计)
    private String queryText;
    private String answerText;
    private Integer hitCount;
    private Integer flagged;      // 0/1
    private String flagReason;    // 越界提问 / 无效提问 / 0命中 / 高频 / 重复(可叠加)
    private LocalDateTime createdAt;
}
