package com.huiyi.modules.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.ResultCode;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.auth.entity.User;
import com.huiyi.modules.auth.mapper.UserMapper;
import com.huiyi.modules.system.entity.Doctor;
import com.huiyi.modules.system.entity.Notification;
import com.huiyi.modules.system.mapper.DoctorMapper;
import com.huiyi.modules.system.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 站内通知。
 * <p>- 写(notify*):事件驱动,定向投递(doctor→user / company→user / admin 广播 / 库存去重)。
 *   全部最佳努力(catch+log),通知失败绝不回滚或破坏业务事务。
 * <p>- 读(my*):当前用户隔离,正常抛错(用户可见)。
 * <p>类目 CAT_*:0库存/1反馈/2药企/3系统;引用 REF_*:DEMAND/COMPANY/STOCK/ANNOUNCE(机构公告)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final int CAT_STOCK = 0, CAT_DEMAND = 1, CAT_COMPANY = 2, CAT_SYSTEM = 3;
    public static final String REF_DEMAND = "DEMAND", REF_COMPANY = "COMPANY", REF_STOCK = "STOCK", REF_ANNOUNCE = "ANNOUNCE", REF_POLICY = "POLICY";

    private final NotificationMapper notifMapper;
    private final UserMapper userMapper;
    private final DoctorMapper doctorMapper;

    /* —————— 写:定向投递(最佳努力,永不抛)—————— */

    /** 单发:最佳努力,通知失败不得影响业务事务。 */
    public void notifyUser(Long userId, int category, String title, String body, String refType, Long refId) {
        if (userId == null) return;
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setCategory(category);
            n.setTitle(title);
            n.setBody(body);
            n.setRefType(refType);
            n.setRefId(refId);
            n.setIsRead(0);
            notifMapper.insert(n);
        } catch (Exception e) {
            log.warn("notifyUser failed: userId={} title={} err={}", userId, title, e.getMessage());
        }
    }

    /** 通知某医师档案(doctorId → user_id)。 */
    public void notifyDoctor(Long doctorId, int category, String title, String body, String refType, Long refId) {
        safe("doctor", () -> {
            if (doctorId == null) return;
            Doctor d = doctorMapper.selectById(doctorId);
            notifyUser(d == null ? null : d.getUserId(), category, title, body, refType, refId);
        });
    }

    /** 通知某药企下所有账号(company_id → role=1 用户)。 */
    public void notifyCompany(Long companyId, int category, String title, String body, String refType, Long refId) {
        safe("company", () -> {
            for (User u : companyUsers(companyId))
                notifyUser(u.getId(), category, title, body, refType, refId);
        });
    }

    /** 广播给所有在用管理员(role=0, status=1)。 */
    public void notifyAdmins(int category, String title, String body, String refType, Long refId) {
        safe("admins", () -> {
            for (User a : userMapper.selectList(new LambdaQueryWrapper<User>()
                    .eq(User::getRole, RoleConstants.ADMIN).eq(User::getStatus, 1)))
                notifyUser(a.getId(), category, title, body, refType, refId);
        });
    }

    /**
     * 群发通知给某机构下所有在职医师(institution_id → role=3 在用账号)。
     * 用于"机构向本院医师发公告":逐个医师档案 → user_id 投递。返回实际投递人数。
     */
    public int notifyInstitutionDoctors(Long institutionId, int category, String title, String body, String refType, Long refId) {
        if (institutionId == null) return 0;
        int[] n = {0};
        safe("institution-doctors", () -> {
            for (Doctor d : doctorMapper.selectList(new LambdaQueryWrapper<Doctor>()
                    .eq(Doctor::getInstitutionId, institutionId))) {
                notifyUser(d.getUserId(), category, title, body, refType, refId);
                if (d.getUserId() != null) n[0]++;
            }
        });
        return n[0];
    }

    /**
     * 药企公告触达:广播给所有在用医疗机构账号(role=机构, status=1)。
     * 用于药企发布 policy/公告时通知下游医疗机构(类目=药企 CAT_COMPANY,refType=POLICY)。返回实际投递人数。最佳努力。
     */
    public int notifyAllInstitutions(int category, String title, String body, String refType, Long refId) {
        int[] n = {0};
        safe("all-institutions", () -> {
            for (User u : userMapper.selectList(new LambdaQueryWrapper<User>()
                    .eq(User::getRole, RoleConstants.INSTITUTION).eq(User::getStatus, 1))) {
                notifyUser(u.getId(), category, title, body, refType, refId);
                if (u.getId() != null) n[0]++;
            }
        });
        return n[0];
    }

    /**
     * 库存预警(带去重):已达预警线(stock_qty <= threshold)且该公司无未读同库存通知才生成,
     * 避免持续低于阈值时每次出库都重复打扰;补货回到阈值以上后用户读掉即解除,下次再跨阈值会再提醒。
     */
    public void notifyLowStock(Long companyId, String drugName, int stockQty, int threshold, Long stockId) {
        safe("lowStock", () -> {
            if (stockQty > threshold) return;   // 未达预警线
            for (User u : companyUsers(companyId)) {
                Long cnt = notifMapper.selectCount(new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, u.getId())
                        .eq(Notification::getCategory, CAT_STOCK)
                        .eq(Notification::getRefType, REF_STOCK)
                        .eq(Notification::getRefId, stockId)
                        .eq(Notification::getIsRead, 0));
                if (cnt == null || cnt == 0)
                    notifyUser(u.getId(), CAT_STOCK, "库存预警:" + drugName,
                            "当前库存 " + stockQty + " 已低于安全线 " + threshold + ",请及时补货。", REF_STOCK, stockId);
            }
        });
    }

    /* —————— 读:当前用户隔离(正常抛错)—————— */

    /** 我的通知列表(按时间倒序;可按 category/keyword(标题或正文)/isRead 筛选)。 */
    public PageResult<Notification> myPage(int pageNum, int pageSize, Integer category, String keyword, Integer isRead) {
        Long me = currentUser();
        String kw = keyword == null ? null : keyword.trim();
        Page<Notification> p = notifMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, me)
                        .eq(category != null, Notification::getCategory, category)
                        .eq(isRead != null, Notification::getIsRead, isRead)
                        .and(kw != null && !kw.isEmpty(), w -> w.like(Notification::getTitle, kw).or().like(Notification::getBody, kw))
                        .orderByDesc(Notification::getCreateTime));
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 我的未读数。 */
    public int myUnreadCount() {
        Long me = currentUser();
        return Math.toIntExact(notifMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, me).eq(Notification::getIsRead, 0)));
    }

    /** 标记单条已读(归属校验:只能操作自己的通知)。 */
    public void markRead(Long id) {
        Long me = currentUser();
        Notification n = notifMapper.selectById(id);
        if (n == null) throw new BusinessException(ResultCode.NOTIF_NOT_FOUND);
        if (!me.equals(n.getUserId())) throw new BusinessException(ResultCode.NOTIF_FORBIDDEN);
        if (n.getIsRead() != null && n.getIsRead() == 1) return;   // 幂等
        n.setIsRead(1);
        notifMapper.updateById(n);
    }

    /** 全部已读。 */
    public void markAllRead() {
        Long me = currentUser();
        Notification upd = new Notification();
        upd.setIsRead(1);
        notifMapper.update(upd, new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, me).eq(Notification::getIsRead, 0));
    }

    /* —————— 工具 —————— */

    private List<User> companyUsers(Long companyId) {
        if (companyId == null) return Collections.emptyList();
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getCompanyId, companyId).eq(User::getRole, RoleConstants.COMPANY));
    }

    private Long currentUser() {
        return SecurityContextHolder.get().getUserId();
    }

    /** 最佳努力包装:任何异常只记日志,绝不向业务调用方抛出。 */
    private void safe(String label, Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            log.warn("notify[{}] failed: {}", label, e.getMessage());
        }
    }
}
