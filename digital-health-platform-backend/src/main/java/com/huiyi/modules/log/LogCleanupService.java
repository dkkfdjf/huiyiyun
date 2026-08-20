package com.huiyi.modules.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huiyi.modules.system.entity.ErrorLog;
import com.huiyi.modules.system.entity.LoginLog;
import com.huiyi.modules.system.entity.OperationLog;
import com.huiyi.modules.system.mapper.ErrorLogMapper;
import com.huiyi.modules.system.mapper.LoginLogMapper;
import com.huiyi.modules.system.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 审计日志定时清理。login_log / operation_log 为 append-only,保留 180 天;error_log 保留 30 天。
 * 超期物理删除(三表均无 deleted 软删列)。每天凌晨 3:30 执行一次。
 * 需 @EnableScheduling(见 HuiyiApplication)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogCleanupService {

    private static final int KEEP_DAYS = 180;
    private static final int ERR_KEEP_DAYS = 30;

    private final LoginLogMapper loginLogMapper;
    private final OperationLogMapper operationLogMapper;
    private final ErrorLogMapper errorLogMapper;

    /** 每天 03:30 清理一次超期日志(登录/操作 180 天、错误 30 天)。 */
    @Scheduled(cron = "0 30 3 * * ?")
    public void purge() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(KEEP_DAYS);
        int login = loginLogMapper.delete(new LambdaQueryWrapper<LoginLog>().lt(LoginLog::getLoginTime, cutoff));
        int op = operationLogMapper.delete(new LambdaQueryWrapper<OperationLog>().lt(OperationLog::getOperationTime, cutoff));
        LocalDateTime errCutoff = LocalDateTime.now().minusDays(ERR_KEEP_DAYS);
        int err = errorLogMapper.delete(new LambdaQueryWrapper<ErrorLog>().lt(ErrorLog::getErrorTime, errCutoff));
        log.info("日志清理完成:登录 {}、操作 {}(早于 {})/ 错误 {}(早于 {})", login, op, cutoff.toLocalDate(), err, errCutoff.toLocalDate());
    }
}
