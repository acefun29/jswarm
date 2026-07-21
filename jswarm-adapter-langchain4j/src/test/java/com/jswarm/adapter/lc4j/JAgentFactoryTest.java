package com.jswarm.adapter.lc4j;

import com.jswarm.adapter.lc4j.filter.SwarmFilter;
import com.jswarm.adapter.lc4j.run.SwarmRunOptions;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmException;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JAgentFactoryTest {

    @AfterEach
    void tearDown() {
        SwarmContext.clear();
    }

    static class AnalystTools {
        @Tool(name = "analyzeData", value = "分析数据")
        String analyzeData() {
            return "analysis:ok";
        }
    }

    interface AnalystAssistant {
        @dev.langchain4j.service.SystemMessage("你是分析师。用户 {user_name}")
        String chat(@UserMessage String msg);
    }

    @Test
    void fromToolsShouldExposeExternalTools() {
        JAgent agent = JAgent.fromTools("analyst", "分析师", "数据分析",
                "你是分析师", stubModel("done"), new AnalystTools());
        assertEquals(1, agent.externalTools().size());
        assertEquals("analyzeData", agent.externalTools().get(0).name());
        assertNotNull(agent.toolExecutor());
    }

    @Test
    void fromAiServiceShouldExtractInstructions() {
        JAgent agent = JAgent.fromAiService("analyst", "分析师", "数据分析",
                AnalystAssistant.class, stubModel("done"));
        assertEquals("你是分析师。用户 {user_name}", agent.instructions());
    }

    @Test
    void builderWithoutToolsShouldHaveEmptyExternalTools() {
        JAgent agent = JAgent.builder("a", "A")
                .description("desc")
                .instructions("hi")
                .model(stubModel("done"))
                .build();
        assertTrue(agent.externalTools().isEmpty());
        assertNull(agent.toolExecutor());
    }

    @Test
    void fromToolsShouldRejectNullModel() {
        assertThrows(IllegalArgumentException.class, () ->
                JAgent.fromTools("a", "A", "d", "inst", null));
    }

    @Test
    void fromToolsShouldRejectNullDescription() {
        assertThrows(IllegalArgumentException.class, () ->
                JAgent.fromTools("a", "A", null, "inst", stubModel("x")));
    }

    @Test
    void fromToolsShouldRejectNullInstructions() {
        assertThrows(IllegalArgumentException.class, () ->
                JAgent.fromTools("a", "A", "d", null, stubModel("x")));
    }

    @Test
    void builderToolsShouldMatchFromTools() {
        AnalystTools tools = new AnalystTools();
        JAgent fromTools = JAgent.fromTools("a", "A", "d", "inst", stubModel("x"), tools);
        JAgent fromBuilder = JAgent.builder("a", "A")
                .description("d")
                .instructions("inst")
                .model(stubModel("x"))
                .tools(tools)
                .build();
        assertEquals(fromTools.externalTools().size(), fromBuilder.externalTools().size());
        assertEquals(fromTools.externalTools().get(0).name(), fromBuilder.externalTools().get(0).name());
    }

    @Test
    void delegateSubLoopShouldUseTargetToolsNotDelegatorTools() {
        AtomicReference<String> executedTool = new AtomicReference<>();

        class TrackingTools {
            @Tool(name = "analyzeData", value = "分析数据")
            String analyzeData() {
                executedTool.set("analyzeData");
                return "analysis:ok";
            }
        }

        ChatModel analystModel = sequenceModel(
                toolMsg("analyzeData", "{}"),
                AiMessage.from("analyst done"));

        JAgent analyst = JAgent.fromTools("analyst", "分析师", "数据分析",
                "你是分析师", analystModel, new TrackingTools());

        JAgent router = JAgent.builder("router", "router")
                .description("router")
                .instructions("route")
                .model(stubModel("routed"))
                .build();

        Swarm swarm = Swarm.create("test")
                .agent(router)
                .agent(analyst)
                .entry("router")
                .delegate("router", "analyst")
                .build();

        SwarmContext.set(new SwarmContext());
        SwarmFilter filter = new SwarmFilter(swarm);
        String result = filter.executeDelegate("router", "analyst", "analyze sales", null,
                SwarmRunOptions.builder().maxTurns(5).build());

        assertEquals("analyst done", result);
        assertEquals("analyzeData", executedTool.get());
    }

    @Test
    void resolveTimingShouldNotUpdateFrozenPromptAfterToolWrite() {
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        ChatModel model = new ChatModel() {
            int calls;

            @Override
            public ChatResponse chat(ChatRequest req) {
                if (calls++ == 0) {
                    capturedPrompt.set(((SystemMessage) req.messages().get(0)).text());
                    return ChatResponse.builder().aiMessage(toolMsg("writeCtx", "{}")).build();
                }
                return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            }
        };

        JAgent agent = JAgent.fromTools("a", "A", "d", "status {status}", model, new CtxTools());
        SwarmContext ctx = new SwarmContext();
        ctx.put("status", "initial");
        SwarmContext.set(ctx);

        JAgent router = JAgent.builder("main", "main")
                .description("main")
                .instructions("main")
                .model(stubModel("main"))
                .build();

        Swarm swarm = Swarm.create("test")
                .agent(router)
                .agent(agent)
                .entry("main")
                .delegate("main", "a")
                .build();

        SwarmFilter filter = new SwarmFilter(swarm);
        filter.executeDelegate("main", "a", "task", null, SwarmRunOptions.builder().maxTurns(5).build());

        assertEquals("status initial", capturedPrompt.get());
        assertEquals("updated", ctx.get("status"));
    }

    static class CtxTools {
        @Tool(name = "writeCtx")
        String writeCtx() {
            SwarmContext.current().put("status", "updated");
            return "written";
        }
    }

    private static int toolCallSeq;

    private static AiMessage toolMsg(String name, String args) {
        return AiMessage.from(dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                .id("call-" + (++toolCallSeq))
                .name(name).arguments(args).build());
    }

    private static ChatModel sequenceModel(AiMessage... responses) {
        return new ChatModel() {
            int index;

            @Override
            public ChatResponse chat(ChatRequest req) {
                if (index >= responses.length) {
                    throw new SwarmException("unexpected model call");
                }
                return ChatResponse.builder().aiMessage(responses[index++]).build();
            }
        };
    }

    private static ChatModel stubModel(String text) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest req) {
                return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
            }
        };
    }
}
