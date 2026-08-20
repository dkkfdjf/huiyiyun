package com.huiyi.modules.location;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 网点新增 / 修改入参。
 * companyId 不由前端传:company 用户固定为本公司,admin 必须显式指定(见 Service)。
 * 经纬度必填(schema NOT NULL),无地图时由前端手填,后端兜底校验范围。
 */
@Data
public class LocationSaveDTO {

    @NotBlank(message = "网点名称不能为空")
    private String name;

    @NotBlank(message = "地址不能为空")
    private String address;

    /** 城市优先用 cityId(已知/编辑);为空时按 cityName 自动 upsert,实现"城市由网点录入派生"。 */
    private Long cityId;

    /** 城市名:cityId 为空时按此名查/建城市(网点录入新城市时自动建档)。 */
    @NotBlank(message = "请选择或输入城市")
    private String cityName;

    /** 省份:cityId 为空且需新建城市时,据此填 city.province(避免自动建档省份为空)。已有城市忽略此值。 */
    private String province;

    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180.0", message = "经度非法")
    @DecimalMax(value = "180.0", message = "经度非法")
    private BigDecimal longitude;

    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90.0", message = "纬度非法")
    @DecimalMax(value = "90.0", message = "纬度非法")
    private BigDecimal latitude;

    /** 仅 admin 新建时使用:所属企业;company 用户忽略此值。 */
    private Long companyId;

    private String contactPerson;
    private String contactPhone;
}
