package com.huiyi.modules.institution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 机构向本院医师群发公告的入参。
 */
@Data
public class AnnouncementDTO {
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 100, message = "标题最长 100 字")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 1000, message = "内容最长 1000 字")
    private String content;
}
