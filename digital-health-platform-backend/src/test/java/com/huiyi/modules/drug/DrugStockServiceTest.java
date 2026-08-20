package com.huiyi.modules.drug;

import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.system.entity.Drug;
import com.huiyi.modules.system.entity.ReplenishmentOrder;
import com.huiyi.modules.system.entity.SalesLocation;
import com.huiyi.modules.system.mapper.DrugMapper;
import com.huiyi.modules.system.mapper.DrugStockMapper;
import com.huiyi.modules.system.mapper.ReplenishmentOrderMapper;
import com.huiyi.modules.system.mapper.SalesLocationMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 库存铺货服务测试。复现中危缺口 #4:铺货不记入库流水——
 * 台账 totalIn 仅累加后续补货、缺初始铺货量,导致进销存台账永远不平。
 */
@ExtendWith(MockitoExtension.class)
class DrugStockServiceTest {

    @Mock DrugStockMapper stockMapper;
    @Mock DrugMapper drugMapper;
    @Mock SalesLocationMapper locationMapper;
    @Mock ReplenishmentOrderMapper replenishOrderMapper;
    @InjectMocks DrugStockService service;

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

    /** 缺口 #4:铺货必须记一条入库流水(qty=初始铺货量),台账才平得了。 */
    @Test
    void init_records_opening_replenishment_flow() {
        Drug drug = new Drug();
        drug.setId(1L);
        drug.setCompanyId(2L);
        drug.setStatus(1);
        when(drugMapper.selectById(1L)).thenReturn(drug);
        SalesLocation loc = new SalesLocation();
        loc.setId(3L);
        loc.setCompanyId(2L);
        when(locationMapper.selectById(3L)).thenReturn(loc);
        when(stockMapper.selectCount(any())).thenReturn(0L);

        StockInitDTO dto = new StockInitDTO();
        dto.setDrugId(1L);
        dto.setLocationId(3L);
        dto.setStockQty(100);
        dto.setPrice(new BigDecimal("9.90"));
        dto.setThreshold(10);

        service.init(dto);

        ArgumentCaptor<ReplenishmentOrder> cap = ArgumentCaptor.forClass(ReplenishmentOrder.class);
        verify(replenishOrderMapper).insert(cap.capture());
        ReplenishmentOrder o = cap.getValue();
        assertEquals(100, o.getQty(), "铺货初始量应记为入库流水");
        assertEquals(1L, o.getDrugId());
        assertEquals(3L, o.getLocationId());
        assertEquals("铺货初始化", o.getRemark());
    }
}
