// Canonical 消息不可变性测试
package com.jswarm.spi.message;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanonicalMessageTest {

    @Test
    void shouldCopyToolCalls() {
        List<ToolCall> calls = new ArrayList<>();
        calls.add(new ToolCall("1", "lookup", "{}"));
        CanonicalMessage message = CanonicalMessage.assistant("", calls);
        calls.clear();
        assertEquals(1, message.toolCalls().size());
        assertThrows(UnsupportedOperationException.class,
                () -> message.toolCalls().add(new ToolCall("2", "other", "{}")));
    }

    @Test
    void shouldRequireToolResultIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalMessage.toolResult("", "lookup", "result"));
    }

    @Test
    void shouldRejectBlankToolSchema() {
        assertThrows(IllegalArgumentException.class,
                () -> new ToolDescriptor("lookup", "", " "));
    }
}
