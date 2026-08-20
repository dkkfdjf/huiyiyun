package com.huiyi.modules.knowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档:一次导入的整篇(手输文本 / 文件 / URL)。切块后由 {@link KnowledgeChunk} 承载。
 * content_hash(SHA-256)用于去重——同内容重复导入直接返回已有文档 id,不重复建索引。
 */
@Data
@TableName("kb_document")
public class KnowledgeDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String sourceType;    // manual / file / url
    private String sourceRef;     // 来源标识(文件名/URL/手输标记)
    private String contentHash;   // SHA-256(内容指纹,去重)
    private Integer chunkCount;   // 切块数
    private String status;        // ACTIVE / FAILED
    private String scope;         // 可见范围 GLOBAL/COMPANY:id/INSTITUTION:id(检索按当前用户可见 scope 过滤)
    private Long createdBy;
    private LocalDateTime createdAt;
}
