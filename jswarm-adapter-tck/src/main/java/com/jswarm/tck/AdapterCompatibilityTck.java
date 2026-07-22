// 两套 Adapter 共享行为契约
package com.jswarm.tck;

import com.jswarm.spi.error.SwarmErrorCode;
import com.jswarm.spi.message.CanonicalMessage;
import com.jswarm.spi.message.MessageRole;
import com.jswarm.spi.message.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AdapterCompatibilityTck {

    protected abstract AdapterHarness harness();

    @Test
    public final void textRunShouldProduceCanonicalHistoryAndTerminalEvent() {
        TckOutcome outcome = harness().execute(TckFixture.text(
                new TckAgent("a", "hello {name}", CanonicalMessage.assistant("done"))));
        assertNull(outcome.errorCode());
        assertEquals("done", outcome.reply());
        assertEquals(List.of(MessageRole.SYSTEM, MessageRole.USER, MessageRole.ASSISTANT),
                outcome.history().stream().map(CanonicalMessage::role).toList());
        assertEquals("hello {name}", outcome.history().get(0).text());
        assertTrue(outcome.events().contains("COMPLETED"));
        assertTrue(outcome.events().indexOf("AGENT_ENTERED")
                < outcome.events().indexOf("AGENT_EXITED"));
        assertTrue(outcome.events().indexOf("AGENT_EXITED")
                < outcome.events().indexOf("COMPLETED"));
        assertThrows(UnsupportedOperationException.class,
                () -> outcome.history().add(CanonicalMessage.user("mutate")));
    }

    @Test
    public final void handoffShouldPreserveHistoryAndReplaceSystemMessage() {
        TckFixture fixture = new TckFixture(
                List.of(
                        new TckAgent("a", "system-a", CanonicalMessage.assistant("", List.of(
                                route("h1", "handoff", Map.of("target", "b"))))),
                        new TckAgent("b", "system-b", CanonicalMessage.assistant("from-b"))),
                "a",
                Map.of("a", Set.of("b")),
                Map.of(),
                "hello",
                Map.of(),
                10,
                false);
        TckOutcome outcome = harness().execute(fixture);
        assertNull(outcome.errorCode());
        assertEquals("b", outcome.currentAgentId());
        assertEquals("system-b", outcome.history().get(0).text());
        assertTrue(outcome.events().contains("HANDOFF"));
        assertTrue(outcome.history().stream().anyMatch(message ->
                message.role() == MessageRole.TOOL_RESULT && "h1".equals(message.toolCallId())));
    }

    @Test
    public final void delegateShouldReturnChildResultToParent() {
        TckFixture fixture = new TckFixture(
                List.of(
                        new TckAgent("a", "system-a",
                                CanonicalMessage.assistant("", List.of(route(
                                        "d1", "delegate", Map.of("target", "b", "task", "inspect")))),
                                CanonicalMessage.assistant("parent-done")),
                        new TckAgent("b", "system-b", CanonicalMessage.assistant("child-result"))),
                "a",
                Map.of(),
                Map.of("a", Set.of("b")),
                "hello",
                Map.of(),
                10,
                false);
        TckOutcome outcome = harness().execute(fixture);
        assertNull(outcome.errorCode());
        assertEquals("parent-done", outcome.reply());
        assertTrue(outcome.events().containsAll(List.of("DELEGATE_STARTED", "DELEGATE_COMPLETED")));
        assertTrue(outcome.history().stream().anyMatch(message ->
                message.role() == MessageRole.TOOL_RESULT && "child-result".equals(message.text())));
    }

    @Test
    public final void externalToolBatchShouldReturnAllResults() {
        TckAgent agent = new TckAgent(
                "a",
                "system-a",
                List.of(
                        CanonicalMessage.assistant("", List.of(
                                new ToolCall("1", "one", "{}"),
                                new ToolCall("2", "two", "{}"))),
                        CanonicalMessage.assistant("done")),
                Map.of("one", "result-one", "two", "result-two"));
        TckOutcome outcome = harness().execute(TckFixture.text(agent));
        assertNull(outcome.errorCode());
        assertEquals(List.of("result-one", "result-two"), outcome.history().stream()
                .filter(message -> message.role() == MessageRole.TOOL_RESULT)
                .map(CanonicalMessage::text)
                .toList());
    }

    @Test
    public final void invalidRouteShouldRecoverDeterministically() {
        TckFixture fixture = new TckFixture(
                List.of(
                        new TckAgent("a", "system-a",
                                CanonicalMessage.assistant("", List.of(route(
                                        "h1", "handoff", Map.of()))),
                                CanonicalMessage.assistant("recovered")),
                        new TckAgent("b", "system-b", CanonicalMessage.assistant("unused"))),
                "a",
                Map.of("a", Set.of("b")),
                Map.of(),
                "hello",
                Map.of(),
                10,
                false);
        TckOutcome outcome = harness().execute(fixture);
        assertNull(outcome.errorCode());
        assertEquals("recovered", outcome.reply());
        assertTrue(outcome.events().contains("RECOVERY"));
    }

    @Test
    public final void maxTurnShouldReturnSameTypedError() {
        TckAgent agent = new TckAgent(
                "a",
                "system-a",
                List.of(
                        CanonicalMessage.assistant("", List.of(new ToolCall("1", "one", "{}"))),
                        CanonicalMessage.assistant("unused")),
                Map.of("one", "result"));
        TckFixture fixture = new TckFixture(
                List.of(agent), "a", Map.of(), Map.of(), "hello", Map.of(), 1, false);
        TckOutcome outcome = harness().execute(fixture);
        assertEquals(SwarmErrorCode.BUDGET_EXCEEDED, outcome.errorCode());
        assertTrue(outcome.events().contains("FAILED"));
        assertTrue(outcome.events().stream().noneMatch("COMPLETED"::equals));
    }

    @Test
    public final void nullContextShouldUseExplicitEmptyDefault() {
        TckFixture fixture = new TckFixture(
                List.of(new TckAgent("a", "system-a", CanonicalMessage.assistant("done"))),
                "a", Map.of(), Map.of(), "hello", Map.of(), 10, true);
        TckOutcome outcome = harness().execute(fixture);
        assertNull(outcome.errorCode());
        assertEquals("done", outcome.reply());
    }

    @Test
    public final void modelTimeoutShouldReturnSameTypedError() {
        TckAgent agent = new TckAgent(
                "a",
                "system-a",
                List.of(CanonicalMessage.assistant("late")),
                Map.of(),
                250,
                false);
        TckFixture fixture = new TckFixture(
                List.of(agent), "a", Map.of(), Map.of(), "hello", Map.of(),
                10, false, 25);
        TckOutcome outcome = harness().execute(fixture);
        assertEquals(SwarmErrorCode.MODEL_TIMEOUT, outcome.errorCode());
        assertTrue(outcome.events().contains("FAILED"));
        assertTrue(outcome.events().stream().noneMatch("COMPLETED"::equals));
    }

    @Test
    public final void exitFailureShouldNeverEmitCompleted() {
        TckAgent agent = new TckAgent(
                "a",
                "system-a",
                List.of(CanonicalMessage.assistant("done")),
                Map.of(),
                0,
                true);
        TckOutcome outcome = harness().execute(TckFixture.text(agent));
        assertEquals(SwarmErrorCode.INTERNAL, outcome.errorCode());
        assertTrue(outcome.events().contains("FAILED"));
        assertTrue(outcome.events().stream().noneMatch("COMPLETED"::equals));
    }

    private static ToolCall route(String id, String name, Map<String, String> arguments) {
        return new ToolCall(id, name, toJson(arguments), arguments);
    }

    private static String toJson(Map<String, String> arguments) {
        if (arguments.isEmpty()) {
            return "{}";
        }
        return arguments.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
}
