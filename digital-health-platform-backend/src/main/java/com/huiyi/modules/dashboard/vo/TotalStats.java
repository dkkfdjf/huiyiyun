package com.huiyi.modules.dashboard.vo;

import lombok.Data;

@Data
public class TotalStats {
    private long institutionCount;
    private long drugCount;
    private long companyCount;       // audit_status=1 的合作企业
    private long locationCount;
}
