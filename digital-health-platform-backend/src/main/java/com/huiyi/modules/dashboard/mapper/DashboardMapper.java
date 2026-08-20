package com.huiyi.modules.dashboard.mapper;

import com.huiyi.modules.dashboard.vo.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DashboardMapper {

    /** 医师地理分布:按机构聚合医师数 + 机构坐标 */
    List<DoctorMapPoint> doctorGeo();

    /** 医师职称分布 */
    List<NameValue> titleDistribution();

    /** 医师科室分布 */
    List<NameValue> deptDistribution();

    /** 近 N 周销售/出库趋势 */
    List<TrendPoint> trend12w(@Param("weeks") int weeks);

    /** 库存预警(stock_qty<=threshold) */
    List<StockAlertVO> stockAlerts();

    /** 近 N 条运营流水(入库+出库混排) */
    List<OpsFeedVO> recentOps(@Param("n") int n);

    /** 最近 N 条操作动态(审计日志 operation_log,append-only 无 deleted)。 */
    List<ActivityVO> recentActivity(@Param("n") int n);

    /** 本周(ISO 周,周一起 7 天)入库/出库件数。append-only 流水表无 deleted,不加逻辑删除过滤。 */
    WeekFlowVO weekFlow();

    /** 平台规模计数(药品/网点/机构/医师)。 */
    ScaleVO scaleStats();

    /** 销售网点经纬度打点(已录坐标的网点)。 */
    List<DoctorMapPoint> locationGeo();

    /** 医疗机构经纬度打点(含在册医师数;LEFT JOIN 保留无医师机构)。 */
    List<DoctorMapPoint> institutionGeo();
}
