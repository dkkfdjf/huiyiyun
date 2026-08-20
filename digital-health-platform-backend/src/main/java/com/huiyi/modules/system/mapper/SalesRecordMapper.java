package com.huiyi.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huiyi.modules.inventory.LedgerFlowVO;
import com.huiyi.modules.system.entity.SalesRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface SalesRecordMapper extends BaseMapper<SalesRecord> {

    /**
     * 台账出库汇总:按 药品×网点 分组 SUM 销量。可选公司/药品/网点/时间窗口过滤。
     * sales_record 为 append-only(无 deleted 列),无需软删过滤。
     */
    @Select("<script>" +
            "SELECT drug_id AS drugId, location_id AS locationId, COALESCE(SUM(qty),0) AS qty " +
            "FROM sales_record " +
            "<where>" +
            "  <if test='companyId != null'>AND company_id = #{companyId}</if>" +
            "  <if test='drugId != null'>AND drug_id = #{drugId}</if>" +
            "  <if test='locationId != null'>AND location_id = #{locationId}</if>" +
            "  <if test='start != null'>AND sale_time &gt;= #{start}</if>" +
            "  <if test='end != null'>AND sale_time &lt;= #{end}</if>" +
            "</where>" +
            "GROUP BY drug_id, location_id" +
            "</script>")
    List<LedgerFlowVO> sumByDrugLocation(@Param("companyId") Long companyId,
                                         @Param("drugId") Long drugId,
                                         @Param("locationId") Long locationId,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);
}
