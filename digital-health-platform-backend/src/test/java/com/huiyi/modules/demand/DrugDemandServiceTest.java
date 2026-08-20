package com.huiyi.modules.demand;

import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.ResultCode;
import com.huiyi.modules.notification.NotificationService;
import com.huiyi.modules.system.entity.DrugDemand;
import com.huiyi.modules.system.mapper.DoctorMapper;
import com.huiyi.modules.system.mapper.DrugDemandMapper;
import com.huiyi.modules.system.mapper.DrugMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 临床反馈服务测试。复现中危缺口 #5:assign() 不重开状态机——
 * 被驳回/撤回的反馈重派给新公司后,accept(要求 status==待处理)失败,形成死胡同。
 */
@ExtendWith(MockitoExtension.class)
class DrugDemandServiceTest {

    @Mock DrugDemandMapper demandMapper;
    @Mock DrugMapper drugMapper;
    @Mock DoctorMapper doctorMapper;
    @Mock NotificationService notificationService;
    @InjectMocks DrugDemandService service;

    /** 缺口 #5:被驳回的反馈重派 → 必须重开为待处理、清空上一手痕迹,否则新公司受理不了。 */
    @Test
    void reassign_reopens_rejected_demand_to_pending() {
        DrugDemand d = new DrugDemand();
        d.setId(7L);
        d.setStatus(3);          // 已驳回
        d.setCompanyId(2L);      // 原 A 公司
        d.setHandlerId(99L);
        d.setReply("无货");
        when(demandMapper.selectById(7L)).thenReturn(d);

        DemandAssignDTO dto = new DemandAssignDTO();
        dto.setCompanyId(5L);    // 改派 B 公司
        service.assign(7L, dto);

        assertEquals(0, d.getStatus(), "重派应重开为待处理(0)");
        assertEquals(5L, d.getCompanyId(), "归属新公司");
        assertNull(d.getHandlerId(), "清空上一手处理人");
        assertNull(d.getReply(), "清空上一手回复");
    }

    /** 已满足(终态)的反馈不可改派,避免重开丢失完结记录。 */
    @Test
    void reassign_satisfied_demand_forbidden() {
        DrugDemand d = new DrugDemand();
        d.setId(8L);
        d.setStatus(2);          // 已满足
        when(demandMapper.selectById(8L)).thenReturn(d);

        DemandAssignDTO dto = new DemandAssignDTO();
        dto.setCompanyId(5L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.assign(8L, dto));
        assertEquals(ResultCode.DEMAND_REASSIGN_FORBIDDEN.getCode(), e.getCode());
        verify(demandMapper, never()).updateById(any(DrugDemand.class));
    }
}
