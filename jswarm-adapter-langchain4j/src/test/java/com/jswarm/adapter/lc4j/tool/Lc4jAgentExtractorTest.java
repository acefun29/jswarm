package com.jswarm.adapter.lc4j.tool;

import com.jswarm.core.SwarmException;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Lc4jAgentExtractorTest {

    interface SinglePrompt {
        @SystemMessage("你是路由专员")
        String chat(@UserMessage String msg);
    }

    interface MultiLinePrompt {
        @SystemMessage(value = {"第一行", "第二行"}, delimiter = "\n---\n")
        String chat(@UserMessage String msg);
    }

    interface NoPrompt {
        String chat(@UserMessage String msg);
    }

    interface TwoPrompts {
        @SystemMessage("a")
        String chat1(@UserMessage String msg);

        @SystemMessage("b")
        String chat2(@UserMessage String msg);
    }

    interface ResourcePrompt {
        @SystemMessage(fromResource = "/lc4j-test-prompt.txt")
        String chat(@UserMessage String msg);
    }

    @Test
    void extractShouldReadSingleSystemMessage() {
        assertEquals("你是路由专员", Lc4jAgentExtractor.extractInstructions(SinglePrompt.class));
    }

    @Test
    void extractShouldJoinStringArrayWithDelimiter() {
        assertEquals("第一行\n---\n第二行", Lc4jAgentExtractor.extractInstructions(MultiLinePrompt.class));
    }

    @Test
    void extractShouldFailWhenNoSystemMessage() {
        SwarmException ex = assertThrows(SwarmException.class,
                () -> Lc4jAgentExtractor.extractInstructions(NoPrompt.class));
        assertTrue(ex.getMessage().contains("exactly one"));
    }

    @Test
    void extractShouldFailWhenMultipleSystemMessages() {
        SwarmException ex = assertThrows(SwarmException.class,
                () -> Lc4jAgentExtractor.extractInstructions(TwoPrompts.class));
        assertTrue(ex.getMessage().contains("exactly one"));
    }

    @Test
    void extractShouldReadFromResource() {
        assertEquals("resource prompt", Lc4jAgentExtractor.extractInstructions(ResourcePrompt.class).trim());
    }
}
