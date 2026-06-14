package com.jswarm.adapter.lc4j.tool;

import com.jswarm.core.SwarmException;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Lc4jToolBridgeTest {

    static class MultiToolBean {
        @Tool(name = "lookupOrder", value = "查询订单")
        String lookupOrder() {
            return "order:123";
        }

        @Tool(name = "cancelOrder", value = "取消订单")
        String cancelOrder() {
            return "cancelled:456";
        }
    }

    static class HandoffToolBean {
        @Tool(name = "handoff")
        String handoff() {
            return "bad";
        }
    }

    static class DuplicateToolBeanA {
        @Tool(name = "sameName")
        String first() {
            return "a";
        }
    }

    static class DuplicateToolBeanB {
        @Tool(name = "sameName")
        String second() {
            return "b";
        }
    }

    @Test
    void bridgeShouldRouteMultipleMethods() {
        MultiToolBean bean = new MultiToolBean();
        Lc4jToolBridge.BridgeResult bridge = Lc4jToolBridge.bridge(bean);

        assertEquals(2, bridge.specs().size());
        assertEquals("order:123", bridge.executor().execute(
                ToolExecutionRequest.builder().name("lookupOrder").arguments("{}").build()));
        assertEquals("cancelled:456", bridge.executor().execute(
                ToolExecutionRequest.builder().name("cancelOrder").arguments("{}").build()));
    }

    @Test
    void bridgeShouldRejectReservedToolNames() {
        SwarmException ex = assertThrows(SwarmException.class,
                () -> Lc4jToolBridge.bridge(new HandoffToolBean()));
        assertTrue(ex.getMessage().contains("handoff"));
    }

    @Test
    void bridgeShouldRejectDuplicateToolNames() {
        SwarmException ex = assertThrows(SwarmException.class,
                () -> Lc4jToolBridge.bridge(new DuplicateToolBeanA(), new DuplicateToolBeanB()));
        assertTrue(ex.getMessage().contains("Duplicate tool name"));
    }

    @Test
    void bridgeShouldReturnEmptyForNoTools() {
        Lc4jToolBridge.BridgeResult bridge = Lc4jToolBridge.bridge();
        assertTrue(bridge.specs().isEmpty());
        assertNull(bridge.executor());
    }

    @Test
    void bridgeShouldThrowToolNotHandledForUnknownName() {
        Lc4jToolBridge.BridgeResult bridge = Lc4jToolBridge.bridge(new MultiToolBean());
        assertThrows(ToolNotHandledException.class, () -> bridge.executor().execute(
                ToolExecutionRequest.builder().name("unknown").arguments("{}").build()));
    }
}
