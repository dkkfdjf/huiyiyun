package com.huiyi.modules.inventory;

import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.ResultCode;
import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.notification.NotificationService;
import com.huiyi.modules.system.entity.Drug;
import com.huiyi.modules.system.entity.DrugStock;
import com.huiyi.modules.system.entity.SalesRecord;
import com.huiyi.modules.system.mapper.DrugMapper;
import com.huiyi.modules.system.mapper.DrugStockMapper;
import com.huiyi.modules.system.mapper.ReplenishmentOrderMapper;
import com.huiyi.modules.system.mapper.SalesLocationMapper;
import com.huiyi.modules.system.mapper.SalesRecordMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 库存/销售服务测试。
 * - 高危缺口 #3:客户端篡改售价(0.01)→ 修复后忽略,改用库存挂牌价(amount = 数量 × 单价,单价只读)。
 * - 中危缺口 #6:下架药品不可补货(守卫一致性,与 sell 对齐)。
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock DrugStockMapper stockMapper;
    @Mock SalesRecordMapper salesMapper;
    @Mock ReplenishmentOrderMapper replenishMapper;
    @Mock DrugMapper drugMapper;
    @Mock SalesLocationMapper locationMapper;
    @Mock NotificationService notificationService;
    @InjectMocks InventoryService service;

    @BeforeEach
    void wireCompanyUser() {
        CurrentUser u = new CurrentUser();
        u.setUserId(20L);
        u.setUsername("pharma");
        u.setRole(RoleConstants.COMPANY);
        u.setCompanyId(2L);
        SecurityContextHolder.set(u);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clear();
    }

    /** 高危缺口 #3:客户端篡改售价(0.01)→ 修复后应忽略,改用库存挂牌价(9.90)。 */
    @Test
    void sell_uses_stock_list_price_ignores_client_price() {
        SaleDTO dto = new SaleDTO();
        dto.setDrugId(1L);
        dto.setLocationId(3L);
        dto.setQty(5);
        dto.setPrice(new BigDecimal("0.01")); // 客户端低价做账
        DrugStock s = new DrugStock();
        s.setId(10L);
        s.setDrugId(1L);
        s.setLocationId(3L);
        s.setCompanyId(2L);
        s.setPrice(new BigDecimal("9.90"));
        when(stockMapper.selectOne(any())).thenReturn(s);
        Drug d = new Drug();
        d.setId(1L);
        d.setStatus(1);
        when(drugMapper.selectById(1L)).thenReturn(d);
        when(stockMapper.deductStock(10L, 5)).thenReturn(1);

        service.sell(dto);

        ArgumentCaptor<SalesRecord> cap = ArgumentCaptor.forClass(SalesRecord.class);
        verify(salesMapper).insert(cap.capture());
        SalesRecord rec = cap.getValue();
        assertEquals(new BigDecimal("9.90"), rec.getPrice(), "售价应取库存挂牌价,客户端价格不可信");
        assertEquals(new BigDecimal("49.50"), rec.getAmount(), "金额 = 数量 × 挂牌价(服务端计算)");
    }

    /** 中危缺口 #6:下架药品不可补货(守卫一致性——与 sell 对齐,杜绝向下架品堆库存)。 */
    @Test
    void replenish_rejects_offshelf_drug() {
        ReplenishDTO dto = new ReplenishDTO();
        dto.setDrugId(1L);
        dto.setLocationId(3L);
        dto.setQty(10);
        DrugStock s = new DrugStock();
        s.setId(10L);
        s.setDrugId(1L);
        s.setLocationId(3L);
        s.setCompanyId(2L);
        when(stockMapper.selectOne(any())).thenReturn(s);
        Drug d = new Drug();
        d.setId(1L);
        d.setStatus(0); // 下架
        lenient().when(drugMapper.selectById(1L)).thenReturn(d);

        BusinessException e = assertThrows(BusinessException.class, () -> service.replenish(dto));

        assertEquals(ResultCode.DRUG_OFF_SHELF.getCode(), e.getCode(), "下架药品应拒绝补货");
        verify(stockMapper, never()).addStock(any(), anyInt());
    }
}
