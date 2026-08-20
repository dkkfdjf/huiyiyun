package com.huiyi.modules.city;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 城市覆盖看板(管理员):每城市的网点 / 机构 / 覆盖药企 / 累计销售额汇总。
 * 让"查城市"从纯 CRUD 升级为有业务含义的覆盖视图。
 */
@Data
@AllArgsConstructor
public class CityCoverageVO {
    private Long id;
    private String name;
    private String province;
    private String regionCode;
    private Long locationCount;      // 网点数
    private Long institutionCount;   // 机构数
    private Integer companyCount;    // 覆盖药企数(按公司去重)
    private BigDecimal salesAmount;  // 累计销售额(经网点归属城市汇总)
}
