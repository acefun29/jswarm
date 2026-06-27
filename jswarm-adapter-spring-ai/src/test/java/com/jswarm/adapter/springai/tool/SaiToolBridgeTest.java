package com.jswarm.adapter.springai.tool;

import com.jswarm.adapter.springai.ToolNotHandledException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.annotation.Tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SaiToolBridgeTest {

    @Test
    void bridgeShouldRouteMultipleMethods() {
        SaiToolBridge.BridgeResult result = SaiToolBridge.bridge(new OrderTools());

        assertEquals(2, result.callbacks().size());
        assertNotNull(result.executor());

        AssistantMessage.ToolCall lookupCall = toolCall("lookupOrder", "{}");
        AssistantMessage.ToolCall statusCall = toolCall("getStatus", "{}");

        String lookupResult = result.executor().execute(lookupCall);
        String statusResult = result.executor().execute(statusCall);
        assertTrue(lookupResult.contains("lookup:ok"), "Expected lookup result, got: " + lookupResult);
        assertTrue(statusResult.contains("status:ok"), "Expected status result, got: " + statusResult);
    }

    @Test
    void bridgeShouldRejectReservedToolNames() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SaiToolBridge.bridge(new HandoffTool()));
        assertTrue(ex.getMessage().contains("handoff"));
    }

    @Test
    void bridgeShouldRejectDuplicateToolNames() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SaiToolBridge.bridge(new DupToolA(), new DupToolB()));
        assertTrue(ex.getMessage().contains("Duplicate"));
    }

    @Test
    void bridgeShouldReturnEmptyForNoTools() {
        SaiToolBridge.BridgeResult result = SaiToolBridge.bridge();
        assertTrue(result.callbacks().isEmpty());
        assertNotNull(result.executor());
    }

    @Test
    void bridgeShouldThrowToolNotHandledForUnknownName() {
        SaiToolBridge.BridgeResult result = SaiToolBridge.bridge(new OrderTools());
        AssistantMessage.ToolCall unknown = toolCall("missingTool", "{}");
        ToolNotHandledException ex = assertThrows(ToolNotHandledException.class,
                () -> result.executor().execute(unknown));
        assertEquals("missingTool", ex.toolName());
    }

    private static AssistantMessage.ToolCall toolCall(String name, String args) {
        AssistantMessage.ToolCall tc = mock(AssistantMessage.ToolCall.class);
        when(tc.name()).thenReturn(name);
        when(tc.arguments()).thenReturn(args);
        return tc;
    }

    static class OrderTools {
        @Tool(name = "lookupOrder")
        public String lookupOrder() {
            return "lookup:ok";
        }

        @Tool(name = "getStatus")
        public String getStatus() {
            return "status:ok";
        }
    }

    static class HandoffTool {
        @Tool(name = "handoff")
        public String handle(String input) {
            return input;
        }
    }

    static class DupToolA {
        @Tool(name = "sameName")
        public String first(String input) {
            return input;
        }
    }

    static class DupToolB {
        @Tool(name = "sameName")
        public String second(String input) {
            return input;
        }
    }
}
