package com.huiyi.modules.institution;

import lombok.Data;

import java.util.List;

/** 医疗机构工作台主页统计。 */
@Data
public class InstitutionHomeVO {
    private String institutionName;
    private String address;
    private String cityName;
    private String contactPerson;
    private String contactPhone;
    private long doctorCount;
    private long departmentCount;
    private List<DeptStat> departmentStats;

    @Data
    public static class DeptStat {
        private Long departmentId;
        private String departmentName;
        private long count;
    }
}
