package com.huiyi.modules.institution;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 机构已发公告视图(由本院医师收到的同类通知聚合而成)。
 */
@Data
public class AnnouncementVO {
    private String title;
    private String content;
    private LocalDateTime sendTime;   // 首条投递时间(近似发送时间)
    private Integer recipientCount;   // 投递医师数
}
