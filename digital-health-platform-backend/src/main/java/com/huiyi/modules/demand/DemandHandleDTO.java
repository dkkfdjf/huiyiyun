package com.huiyi.modules.demand;

import lombok.Data;

/** 公司处理反馈的回复:标记已满足时选填,驳回时必填(服务层校验)。 */
@Data
public class DemandHandleDTO {
    private String reply;
}
