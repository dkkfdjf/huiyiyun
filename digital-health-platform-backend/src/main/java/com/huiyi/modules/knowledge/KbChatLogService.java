package com.huiyi.modules.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huiyi.common.result.PageResult;
import com.huiyi.common.security.CurrentUser;
import com.huiyi.modules.knowledge.mapper.KbChatLogMapper;
import com.huiyi.modules.knowledge.model.KbChatLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库对话日志:记录每次 /ask,供管理员审计 + 异常/滥用提问检测。
 *
 * <p>标记规则(防「故意浪费 API key 的无效对话」):
 * <ul>
 *   <li><b>无效提问</b> —— 垃圾输入(空/单字/纯标点数字/全同字符/测试乱码);由 {@link KbQueryGuard} 判定,
 *       Controller 在调嵌入+LLM 前硬拦截,不烧 key;</li>
 *   <li><b>越界提问</b> —— 过了 guard(正常中文)、检索也松匹配到资料,但 LLM 作答时判定与业务无关(闲聊/脏话/玩笑)
 *       而礼貌拒答;由 {@link KbRefusalDetector} 按作答里定死的拒绝话术识别。不计「0命中」(来源是被清的显示产物,非真没检索到);</li>
 *   <li><b>0命中</b> —— 命中来源片段数 = 0(知识库答不上:盲区 / 越权 / 无关);</li>
 *   <li><b>高频</b> —— 同一用户 {@value #FREQ_WINDOW_MIN} 分钟内提问 > {@value #FREQ_THRESHOLD} 次(刷量/爬取);</li>
 *   <li><b>重复</b> —— 同一用户 {@value #FREQ_WINDOW_MIN} 分钟内同一提问历史出现 ≥ {@value #REPEAT_THRESHOLD} 次(重复刷问)。</li>
 * </ul>
 * 命中任一即标记,flag_reason 记原因(可叠加,如「0命中+高频」)。落库失败不影响作答(本类内部 try/catch 吞掉,只告警)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbChatLogService {

    static final int FREQ_WINDOW_MIN = 10;   // 高频/重复统计窗口(分钟)
    static final int FREQ_THRESHOLD = 10;    // 窗口内总提问次数阈值(> 即高频)
    static final int REPEAT_THRESHOLD = 3;   // 窗口内同一(截断)提问历史次数阈值(≥ 即重复;selectCount 在插入前,故第 4 次起标记)

    private final KbChatLogMapper kbChatLogMapper;

    /** 落一条对话日志,并按规则计算异常/滥用标记(无效提问 / 0命中 / 高频 / 重复,可叠加)。日志失败不影响作答。
     *  clientIp 一并落库:已登录用户追溯提问来源,游客(无 user_id)按 IP 回退计高频/重复。 */
    public void record(CurrentUser u, String query, String answer, int hitCount, String clientIp) {
        try {
            Long uid = u == null ? null : u.getUserId();
            String qStored = truncate(query, 500);   // 与 repeat 比对用的同一把尺
            boolean invalid = KbQueryGuard.isInvalid(query);
            boolean outScope = KbRefusalDetector.isRefusal(answer);             // 过了 guard、却被 AI 判无关拒答
            boolean zeroHit = !invalid && !outScope && hitCount <= 0;           // 越界拒答不计「0命中」(来源被清是显示需要,非真没检索到)
            boolean hiFreq = isHighFrequency(uid, clientIp);
            boolean hiRepeat = isRepeated(uid, clientIp, qStored);
            List<String> reasons = new ArrayList<>();
            if (invalid) reasons.add("无效提问");
            if (outScope) reasons.add("越界提问");
            if (zeroHit) reasons.add("0命中");
            if (hiFreq) reasons.add("高频");
            if (hiRepeat) reasons.add("重复");
            boolean flagged = !reasons.isEmpty();
            String reason = reasons.isEmpty() ? null : String.join("+", reasons);

            KbChatLog entry = new KbChatLog();
            entry.setUserId(uid);
            entry.setUsername(u == null ? null : u.getUsername());
            entry.setRole(u == null ? null : u.getRole());
            entry.setClientIp(clientIp);
            entry.setQueryText(qStored);
            entry.setAnswerText(truncate(answer, 4000));
            entry.setHitCount(hitCount);
            entry.setFlagged(flagged ? 1 : 0);
            entry.setFlagReason(reason);
            entry.setCreatedAt(LocalDateTime.now());
            kbChatLogMapper.insert(entry);
        } catch (Exception e) {
            // 日志落库失败绝不影响知识库作答
            log.warn("kb chat log persist failed: {}", e.getMessage());
        }
    }

    /** 同一来源在 FREQ_WINDOW_MIN 分钟内的提问数 > 阈值 → 高频(防刷量/爬取)。
     *  已登录用户按 user_id 计;游客无 user_id,回退按 client_ip 计(同一 NAT/代理 IP 共享计数是已知近似,满足审计即可)。 */
    public boolean isHighFrequency(Long userId, String clientIp) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(FREQ_WINDOW_MIN);
        Long c;
        if (userId != null) {
            c = kbChatLogMapper.selectCount(
                    new LambdaQueryWrapper<KbChatLog>()
                            .eq(KbChatLog::getUserId, userId)
                            .gt(KbChatLog::getCreatedAt, since));
        } else if (clientIp != null) {
            c = kbChatLogMapper.selectCount(
                    new LambdaQueryWrapper<KbChatLog>()
                            .isNull(KbChatLog::getUserId)
                            .eq(KbChatLog::getClientIp, clientIp)
                            .gt(KbChatLog::getCreatedAt, since));
        } else {
            return false;
        }
        return c != null && c > FREQ_THRESHOLD;
    }

    /** 同一来源在 FREQ_WINDOW_MIN 分钟内同一(截断)提问历史出现 ≥ REPEAT_THRESHOLD 次 → 重复刷问。
     *  已登录按 user_id;游客回退按 client_ip。 */
    public boolean isRepeated(Long userId, String clientIp, String queryStored) {
        if (queryStored == null) return false;
        LocalDateTime since = LocalDateTime.now().minusMinutes(FREQ_WINDOW_MIN);
        Long c;
        if (userId != null) {
            c = kbChatLogMapper.selectCount(
                    new LambdaQueryWrapper<KbChatLog>()
                            .eq(KbChatLog::getUserId, userId)
                            .eq(KbChatLog::getQueryText, queryStored)
                            .gt(KbChatLog::getCreatedAt, since));
        } else if (clientIp != null) {
            c = kbChatLogMapper.selectCount(
                    new LambdaQueryWrapper<KbChatLog>()
                            .isNull(KbChatLog::getUserId)
                            .eq(KbChatLog::getClientIp, clientIp)
                            .eq(KbChatLog::getQueryText, queryStored)
                            .gt(KbChatLog::getCreatedAt, since));
        } else {
            return false;
        }
        return c != null && c >= REPEAT_THRESHOLD;
    }

    /** 管理员分页查询:登录名模糊 / 异常标记 / 日期区间,按提问时间倒序。 */
    public PageResult<KbChatLog> page(String username, Integer flagged,
                                      LocalDate start, LocalDate end,
                                      int pageNum, int pageSize) {
        LambdaQueryWrapper<KbChatLog> w = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) w.like(KbChatLog::getUsername, username.trim());
        if (flagged != null) w.eq(KbChatLog::getFlagged, flagged);
        if (start != null) w.ge(KbChatLog::getCreatedAt, start.atStartOfDay());
        if (end != null) w.le(KbChatLog::getCreatedAt, end.atTime(23, 59, 59));
        w.orderByDesc(KbChatLog::getCreatedAt);
        Page<KbChatLog> p = kbChatLogMapper.selectPage(new Page<>(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
