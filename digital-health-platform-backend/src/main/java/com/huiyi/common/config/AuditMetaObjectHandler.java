package com.huiyi.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.huiyi.common.security.SecurityContextHolder;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private String currentUser() {
        String name = SecurityContextHolder.getUsername();
        return name == null ? "system" : name;
    }

    @Override
    public void insertFill(MetaObject m) {
        String who = currentUser();
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(m, "createBy", String.class, who);
        strictInsertFill(m, "updateBy", String.class, who);
        strictInsertFill(m, "createTime", LocalDateTime.class, now);
        strictInsertFill(m, "updateTime", LocalDateTime.class, now);
        strictInsertFill(m, "deleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject m) {
        strictUpdateFill(m, "updateBy", String.class, currentUser());
        strictUpdateFill(m, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
