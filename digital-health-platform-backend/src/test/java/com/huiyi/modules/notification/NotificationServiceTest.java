package com.huiyi.modules.notification;

import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.ResultCode;
import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.auth.entity.User;
import com.huiyi.modules.auth.mapper.UserMapper;
import com.huiyi.modules.system.entity.Doctor;
import com.huiyi.modules.system.entity.Notification;
import com.huiyi.modules.system.mapper.DoctorMapper;
import com.huiyi.modules.system.mapper.NotificationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内通知服务测试:定向解析(doctor/company→user)、库存预警去重、已读归属校验、最佳努力容错。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationMapper notifMapper;
    @Mock UserMapper userMapper;
    @Mock DoctorMapper doctorMapper;
    @InjectMocks NotificationService service;

    @BeforeEach
    void loginAsMe() {
        CurrentUser u = new CurrentUser();
        u.setUserId(1L);
        SecurityContextHolder.set(u);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clear();
    }

    /** 医师档案 → user_id 定向投递。 */
    @Test
    void notifyDoctor_resolves_doctor_to_user() {
        Doctor d = new Doctor();
        d.setUserId(6L);
        when(doctorMapper.selectById(10L)).thenReturn(d);

        service.notifyDoctor(10L, NotificationService.CAT_DEMAND, "t", "b", NotificationService.REF_DEMAND, 7L);

        verify(notifMapper).insert(any(Notification.class));
    }

    /** 药企 → 该公司所有账号广播(多账号都收到)。 */
    @Test
    void notifyCompany_resolves_all_company_users() {
        User a = new User(); a.setId(2L);
        User b = new User(); b.setId(3L);
        when(userMapper.selectList(any())).thenReturn(List.of(a, b));

        service.notifyCompany(1L, NotificationService.CAT_DEMAND, "t", "b", NotificationService.REF_DEMAND, 7L);

        verify(notifMapper, times(2)).insert(any(Notification.class));
    }

    /** 库存预警去重:已有未读同库存通知 → 不重复打扰。 */
    @Test
    void lowStock_skips_when_unread_already_exists() {
        User a = new User(); a.setId(2L);
        when(userMapper.selectList(any())).thenReturn(List.of(a));
        when(notifMapper.selectCount(any())).thenReturn(1L);

        service.notifyLowStock(1L, "布洛芬", 10, 80, 99L);

        verify(notifMapper, never()).insert(any(Notification.class));
    }

    /** 库存预警:低于阈值且无未读 → 生成。 */
    @Test
    void lowStock_inserts_when_below_and_no_unread() {
        User a = new User(); a.setId(2L);
        when(userMapper.selectList(any())).thenReturn(List.of(a));
        when(notifMapper.selectCount(any())).thenReturn(0L);

        service.notifyLowStock(1L, "布洛芬", 10, 80, 99L);

        verify(notifMapper).insert(any(Notification.class));
    }

    /** 库存预警:高于阈值 → 不查不算,直接跳过。 */
    @Test
    void lowStock_skips_when_above_threshold() {
        service.notifyLowStock(1L, "布洛芬", 100, 80, 99L);

        verify(notifMapper, never()).selectCount(any());
        verify(notifMapper, never()).insert(any(Notification.class));
    }

    /** 已读归属校验:只能标记自己的通知,操作别人的 → 403。 */
    @Test
    void markRead_forbidden_when_not_owner() {
        Notification n = new Notification();
        n.setId(5L); n.setUserId(99L); n.setIsRead(0);
        when(notifMapper.selectById(5L)).thenReturn(n);

        BusinessException e = assertThrows(BusinessException.class, () -> service.markRead(5L));
        assertEquals(ResultCode.NOTIF_FORBIDDEN.getCode(), e.getCode());
        verify(notifMapper, never()).updateById(any(Notification.class));
    }

    @Test
    void markRead_not_found() {
        when(notifMapper.selectById(5L)).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class, () -> service.markRead(5L));
        assertEquals(ResultCode.NOTIF_NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    void markRead_marks_own_notification() {
        Notification n = new Notification();
        n.setId(5L); n.setUserId(1L); n.setIsRead(0);
        when(notifMapper.selectById(5L)).thenReturn(n);

        service.markRead(5L);

        assertEquals(1, n.getIsRead());
        verify(notifMapper).updateById(n);
    }

    /** 未读数仅查当前用户。 */
    @Test
    void unreadCount_queries_current_user() {
        when(notifMapper.selectCount(any())).thenReturn(3L);
        assertEquals(3, service.myUnreadCount());
    }

    /** 最佳努力:持久化失败也不向业务调用方抛出(通知不得破坏业务事务)。 */
    @Test
    void notifyUser_swallows_persistence_failure() {
        when(notifMapper.insert(any(Notification.class))).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() ->
                service.notifyUser(1L, NotificationService.CAT_SYSTEM, "t", "b", null, null));
    }
}
