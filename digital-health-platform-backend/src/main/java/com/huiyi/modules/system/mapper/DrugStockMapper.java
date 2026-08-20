package com.huiyi.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huiyi.modules.system.entity.DrugStock;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface DrugStockMapper extends BaseMapper<DrugStock> {

    /**
     * 防超卖原子扣减:仅当库存 ≥ qty 才扣成功。InnoDB 行级 X 锁串行化并发事务,
     * 故 SELECT-then-UPDATE 不会超卖——真正裁决在 WHERE。rows=0 → 库存不足。
     * 顺带 version+1,使并发「维护」的乐观锁能感知库存被改。
     * 注:@Update 裸 SQL 不经 @TableLogic 自动拼接,deleted=0 须显式写出。
     */
    @Update("UPDATE drug_stock SET stock_qty = stock_qty - #{qty}, version = version + 1 " +
            "WHERE id = #{id} AND deleted = 0 AND stock_qty >= #{qty}")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);

    /** 入库加库存;rows=0 → 记录不存在/已删。同样 bump version。 */
    @Update("UPDATE drug_stock SET stock_qty = stock_qty + #{qty}, version = version + 1 " +
            "WHERE id = #{id} AND deleted = 0")
    int addStock(@Param("id") Long id, @Param("qty") int qty);
}
