package com.jswarm.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SwarmContextTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    @Test
    void shouldPutAndGet() {
        SwarmContext ctx = new SwarmContext();
        ctx.put("key", "value");
        assertEquals("value", ctx.get("key"));
    }

    @Test
    void shouldRemoveKey() {
        SwarmContext ctx = new SwarmContext();
        ctx.put("key", "value");
        ctx.remove("key");
        assertNull(ctx.get("key"));
    }

    @Test
    void shouldCheckContains() {
        SwarmContext ctx = new SwarmContext();
        ctx.put("a", 1);
        assertTrue(ctx.contains("a"));
        assertFalse(ctx.contains("b"));
    }

    @Test
    void shouldReturnNullForMissingKey() {
        SwarmContext ctx = new SwarmContext();
        assertNull(ctx.get("nothing"));
    }

    @Test
    void typedGetShouldCast() {
        SwarmContext ctx = new SwarmContext();
        ctx.put("num", 42);
        assertEquals(42, ctx.get("num", Integer.class));
    }

    @Test
    void typedGetShouldReturnNullForMissing() {
        SwarmContext ctx = new SwarmContext();
        assertNull(ctx.get("x", String.class));
    }

    @Test
    void typedGetShouldThrowOnMismatch() {
        SwarmContext ctx = new SwarmContext();
        ctx.put("num", 42);
        assertThrows(IllegalArgumentException.class, () -> ctx.get("num", String.class));
    }

    @Test
    void putNullShouldRemove() {
        SwarmContext ctx = new SwarmContext();
        ctx.put("key", "value");
        ctx.put("key", null);
        assertFalse(ctx.contains("key"));
    }

    @Test
    void shouldInitializeWithMap() {
        SwarmContext ctx = new SwarmContext(java.util.Map.of("a", 1, "b", "two"));
        assertEquals(1, ctx.get("a"));
        assertEquals("two", ctx.get("b"));
    }

    @Test
    void asMapShouldReturnCopy() {
        SwarmContext ctx = new SwarmContext();
        ctx.put("x", 10);
        var map = ctx.asMap();
        map.put("y", 20);
        assertFalse(ctx.contains("y"));
    }

    @Test
    void shouldBindAndUnbindThreadLocal() {
        SwarmContext ctx = new SwarmContext();
        ctx.put("thread", "main");
        SwarmContext.set(ctx);
        assertNotNull(SwarmContext.current());
        assertEquals("main", SwarmContext.current().get("thread"));
        SwarmContext.clear();
        assertNull(SwarmContext.current());
    }

    @Test
    void threadLocalShouldBeIsolated() throws Exception {
        SwarmContext mainCtx = new SwarmContext();
        mainCtx.put("owner", "main");
        SwarmContext.set(mainCtx);

        AtomicReference<String> subResult = new AtomicReference<>();
        Thread t = new Thread(() -> {
            assertNull(SwarmContext.current());
            SwarmContext subCtx = new SwarmContext();
            subCtx.put("owner", "sub");
            SwarmContext.set(subCtx);
            subResult.set(SwarmContext.current().get("owner", String.class));
            SwarmContext.clear();
        });
        t.start();
        t.join();

        assertEquals("sub", subResult.get());
        assertEquals("main", SwarmContext.current().get("owner"));
    }

    @Test
    void setNullShouldClear() {
        SwarmContext.set(new SwarmContext());
        assertNotNull(SwarmContext.current());
        SwarmContext.set(null);
        assertNull(SwarmContext.current());
    }

    @Test
    void shouldSupportMultipleTypes() {
        SwarmContext ctx = new SwarmContext();
        ctx.put("str", "hello");
        ctx.put("num", 3.14);
        ctx.put("bool", true);
        assertEquals("hello", ctx.get("str"));
        assertEquals(3.14, ctx.get("num"));
        assertEquals(true, ctx.get("bool"));
    }

    @Test
    void resolveShouldReplacePlaceholders() {
        SwarmContext ctx = new SwarmContext();
        ctx.put("name", "张三");
        ctx.put("city", "北京");
        assertEquals("用户 张三 来自 北京", ctx.resolve("用户 {name} 来自 {city}"));
    }

    @Test
    void resolveShouldReturnTemplateWithoutPlaceholders() {
        SwarmContext ctx = new SwarmContext();
        ctx.put("x", 1);
        assertEquals("无占位符", ctx.resolve("无占位符"));
    }

    @Test
    void resolveShouldReturnNullForNullTemplate() {
        SwarmContext ctx = new SwarmContext();
        assertNull(ctx.resolve(null));
    }

    @Test
    void resolveShouldLeaveUnmatchedPlaceholders() {
        SwarmContext ctx = new SwarmContext();
        ctx.put("name", "Alice");
        assertEquals("你好 Alice，{unknown} 保留", ctx.resolve("你好 {name}，{unknown} 保留"));
    }

    @Test
    void resolveShouldHandleLongerKeyFirst() {
        SwarmContext ctx = new SwarmContext();
        ctx.put("name", "短");
        ctx.put("full_name", "长名称");
        assertEquals("用户：长名称", ctx.resolve("用户：{full_name}"));
        assertEquals("用户：短", ctx.resolve("用户：{name}"));
    }

    @Test
    void constructorShouldSkipNullValues() {
        java.util.Map<String, Object> source = new java.util.HashMap<>();
        source.put("a", 1);
        source.put("b", null);
        source.put("c", "hello");
        SwarmContext ctx = new SwarmContext(source);
        assertEquals(1, ctx.get("a"));
        assertEquals("hello", ctx.get("c"));
        assertFalse(ctx.contains("b"));
    }

    @Test
    void constructorShouldHandleAllNullValues() {
        java.util.Map<String, Object> source = new java.util.HashMap<>();
        source.put("x", null);
        source.put("y", null);
        SwarmContext ctx = new SwarmContext(source);
        assertTrue(ctx.asMap().isEmpty());
    }
}
