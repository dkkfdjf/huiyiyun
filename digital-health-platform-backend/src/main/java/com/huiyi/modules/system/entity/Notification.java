package com.huiyi.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huiyi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内通知。id/审计列/逻辑删除继承 BaseEntity。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification")
public class Notification extends BaseEntity {
    private Long userId;       // 接收者 user.id
    private Integer category;  // 0库存预警/1反馈流转/2药企审核/3系统(见 NotificationService.CAT_*)
    private String title;
    private String body;
    private String refType;    // DEMAND/COMPANY/STOCK(见 NotificationService.REF_*)
    private Long refId;        // 关联业务对象 id
    private Integer isRead;    // 0未读/1已读
}
