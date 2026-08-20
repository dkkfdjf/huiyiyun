package com.huiyi.modules.auth;

import com.huiyi.common.security.RoleConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huiyi.common.result.BusinessException;
import com.huiyi.common.result.ResultCode;
import com.huiyi.common.security.CurrentUser;
import com.huiyi.common.security.JwtUtil;
import com.huiyi.common.security.SecurityContextHolder;
import com.huiyi.modules.auth.entity.User;
import com.huiyi.modules.auth.mapper.UserMapper;
import com.huiyi.modules.system.entity.LoginLog;
import com.huiyi.modules.system.entity.PharmaCompany;
import com.huiyi.modules.system.entity.Doctor;
import com.huiyi.modules.system.CaptchaMode;
import com.huiyi.modules.system.SystemConfigService;
import com.huiyi.modules.system.mapper.LoginLogMapper;
import com.huiyi.modules.system.mapper.PharmaCompanyMapper;
import com.huiyi.modules.system.mapper.DoctorMapper;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 登录与账号锁定(详细设计 §6.3)。
 * 流程:校验验证码 → IP 限流 → 查用户 → 校验 status/公司审核 → 校验锁定 → BCrypt 比对 → 失败计数/锁定 → 签发 JWT。
 */
@Slf4j
@Service
public class LoginService {

    @Autowired private UserMapper userMapper;
    @Autowired private LoginLogMapper loginLogMapper;
    @Autowired private BCryptPasswordEncoder encoder;
    @Setter @Autowired private JwtUtil jwtUtil;
    @Setter @Autowired private PharmaCompanyMapper companyMapper;
    @Setter @Autowired private DoctorMapper doctorMapper;
    @Autowired private CaptchaService captchaService;
    @Autowired private AliyunCaptchaService aliyunCaptchaService;
    @Autowired private SystemConfigService systemConfigService;

    // 轻量加固(对冲"验证码 opt-in"的暴力破解面):收紧账号锁定 + 同 IP 失败限流。
    private static final int MAX_LOGIN_FAILS = 3;     // 连续失败 N 次锁定(原 5)
    private static final int LOCK_MINUTES = 60;       // 锁定时长(原 30)
    private static final int IP_WINDOW_MIN = 10;      // IP 限流统计窗口
    private static final int IP_FAIL_THRESHOLD = 10;  // 窗口内同 IP 失败达此数即拒

    // 游客领令牌成功限流:挡"持续成功领令牌"刷量(失败限流只挡撞库式失败重试,挡不住每次都成功)。
    private static final int GUEST_OK_WINDOW_MIN = 10;   // 统计窗口(与失败限流同口径)
    private static final int GUEST_OK_THRESHOLD = 5;     // 窗口内同 IP 游客成功领令牌上限,超即拒

    public LoginVO login(LoginDTO dto, String ip, String userAgent) {
        // 滑块验证三态(系统配置 captcha.mode,管理员可改,防阿里云费用超限):
        //   LOCK   冻结止损:挡普通账号(药企/机构/医师),但放行管理员——
        //          否则设 LOCK 的管理员把自己也锁在门外、无法从 UI 改回(只能进库)。0 阿里云调用。
        //   PASS   关闭放行:跳过滑块二次校验,登录照常通过,0 阿里云调用。
        //   ENABLED 启用(默认):前端滑块通过后带 captchaVerifyParam,后端调阿里云验真(计费点)。
        CaptchaMode mode = systemConfigService.getCaptchaMode();
        if (mode == CaptchaMode.LOCK) {
            // 仅放行"启用管理员"恢复登录;其余账号(含不存在/禁用/非管理员)一律拒,且不暴露用户是否存在。
            User probe = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, dto.getUsername()).last("limit 1"));
            boolean adminCanRecover = probe != null
                    && probe.getRole() != null && probe.getRole() == RoleConstants.ADMIN
                    && probe.getStatus() != null && probe.getStatus() == 1;
            if (!adminCanRecover) {
                failLog(probe == null ? null : probe.getId(), dto.getUsername(), ip, userAgent, "系统登录已锁定");
                throw new BusinessException(ResultCode.LOGIN_LOCKED);
            }
            // 管理员放行:跳过阿里云滑块(0 计费),继续走后续密码校验。
        }
        if (mode == CaptchaMode.ENABLED
                && !aliyunCaptchaService.verify(dto.getCaptchaVerifyParam())) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }
        // 图形验证码(设计 §6.3):请求带验证码才校验;不带则沿用前端滑块当人机校验。dev 万能码在 CaptchaService 内放行。
        if (dto.getCaptcha() != null && !dto.getCaptcha().isBlank()
                && !captchaService.verify(dto.getCaptchaKey(), dto.getCaptcha())) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }

        // IP 限流:同一 IP 在窗口内失败达阈值 → 直接拒(防撞库),不查用户/不比密码,省资源。
        if (loginLogMapper.countRecentFailsByIp(ip, LocalDateTime.now().minusMinutes(IP_WINDOW_MIN)) >= IP_FAIL_THRESHOLD) {
            throw new BusinessException(ResultCode.LOGIN_TOO_MANY);
        }

        User u = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername())
                .last("limit 1"));

        if (u == null || u.getStatus() == null || u.getStatus() == 0) {
            failLog(null, dto.getUsername(), ip, userAgent, "账号不可用");
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        // 公司账号需处于正常态(audit_status=1);停用(3)则拦截登录
        if (u.getRole() != null && u.getRole() == RoleConstants.COMPANY && u.getCompanyId() != null) {
            PharmaCompany c = companyMapper.selectById(u.getCompanyId());
            if (c == null || c.getAuditStatus() == null || c.getAuditStatus() != 1) {
                failLog(u.getId(), dto.getUsername(), ip, userAgent, "公司已停用");
                throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
            }
        }

        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(LocalDateTime.now())) {
            failLog(u.getId(), dto.getUsername(), ip, userAgent, "账号锁定");
            throw new BusinessException(ResultCode.ACCOUNT_LOCKED);
        }

        if (!encoder.matches(dto.getPassword(), u.getPassword())) {
            int fails = (u.getLoginFailCount() == null ? 0 : u.getLoginFailCount()) + 1;
            u.setLoginFailCount(fails);
            if (fails >= MAX_LOGIN_FAILS) {
                u.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            }
            userMapper.updateById(u);
            failLog(u.getId(), dto.getUsername(), ip, userAgent, "密码错误");
            // 凭据错误用 4007(非 401):401 在前端=会话过期→弹"登录已过期"并跳登录,
            // 登录页输错密码应是"用户名或密码错误"且留在登录页,故与 TOKEN_INVALID(401) 区分。
            throw new BusinessException(ResultCode.BAD_CREDENTIALS);
        }

        // 成功
        u.setLoginFailCount(0);
        u.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(u);
        okLog(u, ip, userAgent);

        CurrentUser cu = new CurrentUser();
        cu.setUserId(u.getId());
        cu.setUsername(u.getUsername());
        cu.setRole(u.getRole());
        cu.setCompanyId(u.getCompanyId());
        cu.setInstitutionId(u.getInstitutionId());
        // 医师:按 user 查 doctor 档案补 doctorId(M8 临床反馈按医师隔离需要;前端从 JWT 解出)
        if (u.getRole() != null && u.getRole() == RoleConstants.DOCTOR) {
            Doctor doc = doctorMapper.selectOne(new LambdaQueryWrapper<Doctor>()
                    .eq(Doctor::getUserId, u.getId()).last("limit 1"));
            if (doc != null) {
                cu.setDoctorId(doc.getId());
                // 医生档案的归属机构比 User.institutionId 可靠:种子/历史账号 User 表该字段常为空,
                // 但 Doctor 档案总有。回填到 JWT,供 KB scope 隔离等按机构过滤的场景(否则医生仅见 GLOBAL)。
                if (doc.getInstitutionId() != null) {
                    cu.setInstitutionId(doc.getInstitutionId());
                }
            } else {
                // 数据自愈:role=3 账号缺失 doctor 档案(经 /users 直接建账号等历史路径产生)。
                // 不补建会导致 JWT 无 doctorId → /doctors/me 与提交反馈都 404"资源不存在"。
                // 并发兜底:doctor.user_id 有唯一约束 uk_user_deleted(V1),两条登录同时自愈时
                // 后者 insert 抛 DuplicateKeyException → 回查前者已建的档案,不让登录报 500。
                Doctor d = new Doctor();
                d.setUserId(u.getId());
                d.setName(u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : u.getUsername());
                d.setInstitutionId(u.getInstitutionId());
                try {
                    doctorMapper.insert(d);
                    cu.setDoctorId(d.getId());
                } catch (DuplicateKeyException e) {
                    Doctor existed = doctorMapper.selectOne(new LambdaQueryWrapper<Doctor>()
                            .eq(Doctor::getUserId, u.getId()).last("limit 1"));
                    if (existed != null) cu.setDoctorId(existed.getId());
                }
            }
        }

        // 登录接口在 JWT 白名单内,JwtAuthFilter 不 set 上下文 → 审计切面 OperationLogAspect 在 finally
        // 取不到登录人,导致登录日志 user_id/username 落 NULL、操作日志页"登录名"列空白。这里补 set 供切面记录。
        SecurityContextHolder.set(cu);

        LoginVO vo = new LoginVO();
        vo.setToken(jwtUtil.generate(cu));
        vo.setRole(u.getRole());
        vo.setCompanyId(u.getCompanyId());
        vo.setInstitutionId(u.getInstitutionId());
        vo.setRealName(u.getRealName());
        vo.setExpireAt(LocalDateTime.now().plusMinutes(jwtUtil.getExpireMinutes()));
        return vo;
    }

    /**
     * 游客体验登录:签发 GUEST 临时令牌(只读,无真实账号)。
     * 受 guest.enabled 开关 + captcha.mode 门槛 + IP 限流三重约束;任何拒签都记 login_log(result=0)
     * 喂同一条 IP 限流计数器(游客端点恒成功不增计数,故靠拒签落库自限流)。
     */
    public LoginVO guestLogin(GuestLoginDTO dto, String ip, String userAgent) {
        // 开关门:管理员未开放游客入口 → 拒(前端按探活结果不渲染按钮,此处是关后被直调的兜底)。
        if (!systemConfigService.isGuestEnabled()) {
            failLog(null, "游客", ip, userAgent, "游客入口已关闭");
            throw new BusinessException(403, "游客体验入口已关闭");
        }
        CaptchaMode mode = systemConfigService.getCaptchaMode();
        // LOCK:系统冻结止损,游客无管理员恢复路径 → 一律拒(与普通登录 LOCK 语义一致)。
        if (mode == CaptchaMode.LOCK) {
            failLog(null, "游客", ip, userAgent, "系统登录已锁定");
            throw new BusinessException(ResultCode.LOGIN_LOCKED);
        }
        // ENABLED:与普通登录同样的阿里云滑块验真(同门槛,防机器人批量领令牌)。
        if (mode == CaptchaMode.ENABLED && !aliyunCaptchaService.verify(dto.getCaptchaVerifyParam())) {
            failLog(null, "游客", ip, userAgent, "游客滑块验真失败");
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }
        // 图形验证码(请求带才校验,与普通登录一致)。
        if (dto.getCaptcha() != null && !dto.getCaptcha().isBlank()
                && !captchaService.verify(dto.getCaptchaKey(), dto.getCaptcha())) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }
        // IP 失败限流:同窗口失败达阈值 → 拒(防撞库),与普通登录共用 countRecentFailsByIp。
        if (loginLogMapper.countRecentFailsByIp(ip, LocalDateTime.now().minusMinutes(IP_WINDOW_MIN)) >= IP_FAIL_THRESHOLD) {
            throw new BusinessException(ResultCode.LOGIN_TOO_MANY);
        }
        // 游客领令牌成功限流:同 IP 窗口内成功领令牌达阈值 → 拒。
        // 失败限流只挡"撞库式失败重试",挡不住"每次都成功领令牌"(尤其 captcha.mode=PASS 零门槛时);
        // 故单独按成功记录计数,挡脚本批量领令牌。拒签落 failLog(管理员审计可见)。
        if (loginLogMapper.countRecentGuestOkByIp(ip, LocalDateTime.now().minusMinutes(GUEST_OK_WINDOW_MIN)) >= GUEST_OK_THRESHOLD) {
            failLog(null, "游客", ip, userAgent, "领令牌过频");
            throw new BusinessException(ResultCode.LOGIN_TOO_MANY);
        }

        // 签发 GUEST 临时令牌:无真实 user 行,userId/org 全 null → KbScopeResolver 自然落到 {GLOBAL}。
        // 用 generateGuest(短期 2h,远短于正式 12h),缩临时只读令牌的泄露窗口。
        CurrentUser cu = new CurrentUser();
        cu.setUsername("游客");
        cu.setRole(RoleConstants.GUEST);
        SecurityContextHolder.set(cu);  // 供 OperationLog 切面记录(与 login() 同理)

        LoginVO vo = new LoginVO();
        vo.setToken(jwtUtil.generateGuest(cu));
        vo.setRole(RoleConstants.GUEST);
        vo.setRealName("游客");
        vo.setExpireAt(LocalDateTime.now().plusMinutes(jwtUtil.getGuestExpireMinutes()));

        // 成功也记一条 login_log(result=1),游客入口可观测性与正常登录同源(审计/日志页可见;userId 留空)。
        LoginLog ok = new LoginLog();
        ok.setUsername("游客");
        ok.setLoginTime(LocalDateTime.now());
        ok.setIp(ip);
        ok.setUserAgent(userAgent);
        ok.setLoginResult(1);
        loginLogMapper.insert(ok);
        return vo;
    }

    public void logout() {
        SecurityContextHolder.clear();
    }

    private void failLog(Long userId, String username, String ip, String ua, String reason) {
        LoginLog l = new LoginLog();
        l.setUserId(userId);
        l.setUsername(username);
        l.setLoginTime(LocalDateTime.now());
        l.setIp(ip);
        l.setUserAgent(ua);
        l.setLoginResult(0);
        l.setFailReason(reason);
        loginLogMapper.insert(l);
    }

    private void okLog(User u, String ip, String ua) {
        LoginLog l = new LoginLog();
        l.setUserId(u.getId());
        l.setUsername(u.getUsername());
        l.setLoginTime(LocalDateTime.now());
        l.setIp(ip);
        l.setUserAgent(ua);
        l.setLoginResult(1);
        // 异常 IP 告警:该用户有成功登录历史,但从未从此 IP 登录过 → 疑似异地/换设备。
        // 首次登录(无历史)不报,避免误报。每次成功登录最多多 2 次 count 查询,登录低频可接受。
        if (ip != null && !ip.isBlank()) {
            long userPrior = loginLogMapper.selectCount(new LambdaQueryWrapper<LoginLog>()
                    .eq(LoginLog::getUserId, u.getId()).eq(LoginLog::getLoginResult, 1));
            if (userPrior > 0) {
                long ipPrior = loginLogMapper.selectCount(new LambdaQueryWrapper<LoginLog>()
                        .eq(LoginLog::getUserId, u.getId()).eq(LoginLog::getIp, ip).eq(LoginLog::getLoginResult, 1));
                if (ipPrior == 0) {
                    l.setAnomaly(1);
                    l.setAnomalyReason("首次在此 IP 登录(疑似异地/换设备)");
                }
            }
        }
        loginLogMapper.insert(l);
    }
}
