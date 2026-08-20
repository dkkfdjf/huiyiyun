package com.huiyi.modules.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huiyi.common.result.PageResult;
import com.huiyi.modules.system.entity.ErrorLog;
import com.huiyi.modules.system.entity.LoginLog;
import com.huiyi.modules.system.entity.OperationLog;
import com.huiyi.modules.system.mapper.ErrorLogMapper;
import com.huiyi.modules.system.mapper.LoginLogMapper;
import com.huiyi.modules.system.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 审计日志只读查询(管理员)。login_log / operation_log 均为 append-only,
 * 不提供删除接口以保审计完整性。区间按业务时间(loginTime/operationTime)过滤。
 */
@Service
@RequiredArgsConstructor
public class LogService {

    private final LoginLogMapper loginLogMapper;
    private final OperationLogMapper operationLogMapper;
    private final ErrorLogMapper errorLogMapper;

    /** 登录日志分页:登录名模糊、成功/失败、异常IP、日期区间。 */
    public PageResult<LoginLog> pageLogin(String username, Integer loginResult, Integer anomaly,
                                          LocalDate start, LocalDate end,
                                          int pageNum, int pageSize) {
        LambdaQueryWrapper<LoginLog> w = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) w.like(LoginLog::getUsername, username.trim());
        if (loginResult != null) w.eq(LoginLog::getLoginResult, loginResult);
        if (anomaly != null) w.eq(LoginLog::getAnomaly, anomaly);
        if (start != null) w.ge(LoginLog::getLoginTime, start.atStartOfDay());
        if (end != null) w.le(LoginLog::getLoginTime, end.atTime(23, 59, 59));
        w.orderByDesc(LoginLog::getLoginTime);
        Page<LoginLog> p = loginLogMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 操作日志分页:登录名模糊、模块、敏感、日期区间。 */
    public PageResult<OperationLog> pageOperation(String username, String module, Integer sensitive,
                                                  LocalDate start, LocalDate end,
                                                  int pageNum, int pageSize) {
        LambdaQueryWrapper<OperationLog> w = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) w.like(OperationLog::getUsername, username.trim());
        if (module != null && !module.isBlank()) w.eq(OperationLog::getModule, module.trim());
        if (sensitive != null) w.eq(OperationLog::getSensitive, sensitive);
        if (start != null) w.ge(OperationLog::getOperationTime, start.atStartOfDay());
        if (end != null) w.le(OperationLog::getOperationTime, end.atTime(23, 59, 59));
        w.orderByDesc(OperationLog::getOperationTime);
        Page<OperationLog> p = operationLogMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    /** 错误日志分页:异常类型/请求地址模糊、日期区间。 */
    public PageResult<ErrorLog> pageError(String errorType, String requestUrl,
                                          LocalDate start, LocalDate end,
                                          int pageNum, int pageSize) {
        LambdaQueryWrapper<ErrorLog> w = new LambdaQueryWrapper<>();
        if (errorType != null && !errorType.isBlank()) w.like(ErrorLog::getErrorType, errorType.trim());
        if (requestUrl != null && !requestUrl.isBlank()) w.like(ErrorLog::getRequestUrl, requestUrl.trim());
        if (start != null) w.ge(ErrorLog::getErrorTime, start.atStartOfDay());
        if (end != null) w.le(ErrorLog::getErrorTime, end.atTime(23, 59, 59));
        w.orderByDesc(ErrorLog::getErrorTime);
        Page<ErrorLog> p = errorLogMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }
}
