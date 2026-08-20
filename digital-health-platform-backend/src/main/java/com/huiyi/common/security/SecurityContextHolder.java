package com.huiyi.common.security;

public class SecurityContextHolder {
    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static String getUsername() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.getUsername();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
