package com.huiyi.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huiyi.modules.system.entity.LoginLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface LoginLogMapper extends BaseMapper<LoginLog> {

    /** 某 IP 在 since 之后的失败次数(login_result=0),用于登录 IP 限流(防撞库)。 */
    @Select("SELECT COUNT(*) FROM login_log WHERE ip = #{ip} AND login_result = 0 AND login_time > #{since}")
    long countRecentFailsByIp(@Param("ip") String ip, @Param("since") LocalDateTime since);

    /** 某 IP 在 since 之后的游客成功领令牌次数(login_result=1 且 username='游客'),用于游客领令牌限流(防刷令牌)。
     *  游客无真实账号,username 恒为"游客";与 LoginService 落库的 username 取值保持一致。 */
    @Select("SELECT COUNT(*) FROM login_log WHERE ip = #{ip} AND login_result = 1 AND username = '游客' AND login_time > #{since}")
    long countRecentGuestOkByIp(@Param("ip") String ip, @Param("since") LocalDateTime since);
}
