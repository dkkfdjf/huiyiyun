package com.huiyi.modules.notification;

import com.huiyi.common.aspect.OperationLog;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.modules.system.entity.Notification;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 站内通知:四角色共用。铃铛未读数 / 列表 / 已读。
 */
@Tag(name = "站内通知")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @Operation(summary = "我的通知列表(按时间倒序,支持类目/关键词/已读筛选)")
    @GetMapping
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY, RoleConstants.INSTITUTION, RoleConstants.DOCTOR})
    public R<PageResult<Notification>> page(@RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "20") int pageSize,
                                            @RequestParam(required = false) Integer category,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer isRead) {
        return R.ok(service.myPage(pageNum, pageSize, category, keyword, isRead));
    }

    @Operation(summary = "我的未读数")
    @GetMapping("/unread")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY, RoleConstants.INSTITUTION, RoleConstants.DOCTOR})
    public R<Integer> unread() {
        return R.ok(service.myUnreadCount());
    }

    @OperationLog(module = "站内通知", operation = "标记已读")
    @Operation(summary = "标记单条已读")
    @PutMapping("/{id}/read")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY, RoleConstants.INSTITUTION, RoleConstants.DOCTOR})
    public R<Void> markRead(@PathVariable Long id) {
        service.markRead(id);
        return R.ok();
    }

    @OperationLog(module = "站内通知", operation = "全部已读")
    @Operation(summary = "全部已读")
    @PutMapping("/read-all")
    @RequiresRole({RoleConstants.ADMIN, RoleConstants.COMPANY, RoleConstants.INSTITUTION, RoleConstants.DOCTOR})
    public R<Void> markAllRead() {
        service.markAllRead();
        return R.ok();
    }
}
