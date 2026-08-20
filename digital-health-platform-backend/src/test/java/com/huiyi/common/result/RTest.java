package com.huiyi.common.result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RTest {

    @Test
    void ok_carries_data() {
        R<String> r = R.ok("x");
        assertEquals(200, r.getCode());
        assertEquals("success", r.getMessage());
        assertEquals("x", r.getData());
    }

    @Test
    void fail_from_result_code() {
        R<Void> r = R.fail(ResultCode.STOCK_NOT_ENOUGH);
        assertEquals(1001, r.getCode());
        assertEquals("库存不足", r.getMessage());
        assertNull(r.getData());
    }

    @Test
    void fail_with_explicit_message() {
        R<Void> r = R.fail(4001, "账号已锁定");
        assertEquals(4001, r.getCode());
        assertEquals("账号已锁定", r.getMessage());
    }
}
