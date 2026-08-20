package com.huiyi.modules.log;

import com.huiyi.common.result.PageResult;
import com.huiyi.common.result.R;
import com.huiyi.common.security.RequiresRole;
import com.huiyi.common.security.RoleConstants;
import com.huiyi.modules.system.entity.ErrorLog;
import com.huiyi.modules.system.entity.LoginLog;
import com.huiyi.modules.system.entity.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 审计日志查询(管理员只读)。仅查询、不提供删除,保 append-only 审计链。
 */
@Tag(name = "审计日志")
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @Operation(summary = "登录日志分页(成功/失败、IP、UA、失败原因)")
    @GetMapping("/login")
    @RequiresRole(RoleConstants.ADMIN)
    public R<PageResult<LoginLog>> pageLogin(@RequestParam(required = false) String username,
                                             @RequestParam(required = false) Integer loginResult,
                                             @RequestParam(required = false) Integer anomaly,
                                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
                                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end,
                                             @RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(logService.pageLogin(username, loginResult, anomaly, start, end, pageNum, pageSize));
    }

    @Operation(summary = "操作日志分页(模块、操作、耗时)")
    @GetMapping("/operation")
    @RequiresRole(RoleConstants.ADMIN)
    public R<PageResult<OperationLog>> pageOperation(@RequestParam(required = false) String username,
                                                     @RequestParam(required = false) String module,
                                                     @RequestParam(required = false) Integer sensitive,
                                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
                                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end,
                                                     @RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(logService.pageOperation(username, module, sensitive, start, end, pageNum, pageSize));
    }

    @Operation(summary = "错误日志分页(异常类型、请求地址、时间)")
    @GetMapping("/error")
    @RequiresRole(RoleConstants.ADMIN)
    public R<PageResult<ErrorLog>> pageError(@RequestParam(required = false) String errorType,
                                             @RequestParam(required = false) String requestUrl,
                                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
                                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end,
                                             @RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(logService.pageError(errorType, requestUrl, start, end, pageNum, pageSize));
    }
}
