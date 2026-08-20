package com.huiyi.modules.knowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识块:文档切块后的最小检索单元。
 * 向量本身存在 VectorStore(外部),MySQL 只存文本 + 元信息(可回显、可重建索引)。
 * (docId, ordinal) 唯一定位一块,与向量库 id "{docId}:{ordinal}" 对齐。
 */
@Data
@TableName("kb_chunk")
public class KnowledgeChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private Integer ordinal;      // 块序号,从 0
    private String text;          // 块文本
    private Integer tokenCount;   // 近似长度(字符数)
    private String metadata;      // JSON 文本:回显用 {title, sourceType, ...}
    private LocalDateTime createdAt;
}
