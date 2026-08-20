package com.huiyi.modules.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 导入知识请求。sourceType 默认 manual(手输文本);后续可扩展 file(上传文件)/ url(抓取)。 */
@Data
public class KbIngestDTO {
    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String text;

    private String sourceType = "manual";   // manual / file / url
    private String sourceRef;               // 可选:文件名 / URL / 备注
    private String scope = "GLOBAL";        // 可见范围 GLOBAL/COMPANY:id/INSTITUTION:id(行级隔离;默认公共)
}
