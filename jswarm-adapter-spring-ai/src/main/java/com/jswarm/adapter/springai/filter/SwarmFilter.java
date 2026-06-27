package com.jswarm.adapter.springai.filter;

import com.jswarm.adapter.springai.ExternalToolExecutor;
import com.jswarm.adapter.springai.JAgent;
import com.jswarm.adapter.springai.invoke.ChatInvoker;
import com.jswarm.adapter.springai.run.SwarmRunOptions;
import com.jswarm.adapter.springai.tool.SwarmToolInjector;
import com.jswarm.adapter.springai.tool.ToolExecutionMerger;
import com.jswarm.core.Swarm;
import com.jswarm.core.SwarmContext;
import com.jswarm.core.SwarmException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

public final class SwarmFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Swarm swarm;

    public SwarmFilter(Swarm swarm) {
        this.swarm = swarm;
    }

    public FilterDecision decide(AssistantMessage.ToolCall toolCall) {
        String toolName = toolCall.name();
        if ("handoff".equals(toolName)) {
            String targetId = extractArg(toolCall.arguments(), "target");
            validateTargetExists(targetId);
            return FilterDecision.handoff(targetId);
        }
        if ("delegate".equals(toolName)) {
            String targetId = extractArg(toolCall.arguments(), "target");
            String task = extractArg(toolCall.arguments(), "task");
            validateTargetExists(targetId);
            return FilterDecision.delegate(targetId, task);
        }
        return null;
    }

    public String executeDelegate(String targetId, String task,
                                   ExternalToolExecutor swarmFallback,
                                   SwarmRunOptions options) {
        JAgent target = requireJAgent(swarm.getAgent(targetId));
        SwarmContext ctx = SwarmContext.current();

        List<Message> subMessages = new ArrayList<>();
        target.onDelegateEnter(ctx, task);
        String delegateInstructions = target.instructions();
        if (delegateInstructions == null || delegateInstructions.isBlank()) {
            throw new SwarmException("Agent '" + targetId
                    + "' has no instructions configured");
        }
        subMessages.add(new SystemMessage(ctx.resolve(delegateInstructions)));
        subMessages.add(new UserMessage(task));

        List<ToolCallback> subTools = SwarmToolInjector.generateExternalToolsOnly(target.externalTools());
        ExternalToolExecutor subExec = ToolExecutionMerger.merge(target.toolExecutor(), swarmFallback);

        for (int turn = 0; turn < options.maxTurns(); turn++) {
            ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(subTools)
                    .build();
            Prompt subPrompt = new Prompt(subMessages, chatOptions);
            ChatResponse subResponse = ChatInvoker.invoke(target, subPrompt, options.modelTimeout());
            AssistantMessage subAiMsg = subResponse.getResult().getOutput();

            if (!subResponse.hasToolCalls()) {
                String result = subAiMsg.getText();
                target.onDelegateExit(ctx, task, result);
                return result;
            }

            AssistantMessage.ToolCall subToolCall = subAiMsg.getToolCalls().get(0);

            if (turn == options.maxTurns() - 1) {
                String warning = "Jswarm: maximum turns (" + options.maxTurns()
                        + ") exceeded. Please summarize what you have gathered so far.";
                subMessages.add(subAiMsg);
                subMessages.add(ToolResponseMessage.builder().responses(List.of(
                        new ToolResponseMessage.ToolResponse(subToolCall.id(), subToolCall.name(), warning))).build());
                ToolCallingChatOptions finalOptions = ToolCallingChatOptions.builder()
                        .toolCallbacks(subTools)
                        .build();
                Prompt finalPrompt = new Prompt(subMessages, finalOptions);
                ChatResponse finalResponse = ChatInvoker.invoke(target, finalPrompt, options.modelTimeout());
                AssistantMessage finalMsg = finalResponse.getResult().getOutput();
                subMessages.add(finalMsg);
                String result = finalMsg.hasToolCalls()
                        ? "Jswarm: delegate max turns exceeded."
                        : finalMsg.getText();
                target.onDelegateExit(ctx, task, result);
                return result;
            }

            String subResult;
            try {
                subResult = subExec.execute(subToolCall);
            } catch (RuntimeException e) {
                subResult = "Jswarm recovery: tool '" + subToolCall.name()
                        + "' failed. Error: " + e.getMessage();
            }
            subMessages.add(subAiMsg);
            subMessages.add(ToolResponseMessage.builder().responses(List.of(
                    new ToolResponseMessage.ToolResponse(subToolCall.id(), subToolCall.name(), subResult))).build());
        }
        throw new SwarmException("Delegate max turns exceeded");
    }

    private void validateTargetExists(String targetId) {
        if (!swarm.contains(targetId)) {
            throw new SwarmException("Agent not found: " + targetId);
        }
    }

    private String extractArg(String arguments, String key) {
        if (arguments == null || arguments.isBlank()) {
            throw new SwarmException("Tool call arguments are empty");
        }
        try {
            JsonNode node = MAPPER.readTree(arguments);
            JsonNode field = node.get(key);
            if (field == null) {
                throw new SwarmException("No '" + key + "' field found in tool call arguments");
            }
            if (!field.isTextual()) {
                throw new SwarmException("Field '" + key + "' must be a string, got: " + field.getNodeType());
            }
            return field.textValue();
        } catch (Exception e) {
            throw new SwarmException("Failed to parse tool call arguments: " + e.getMessage(), e);
        }
    }

    private JAgent requireJAgent(com.jswarm.core.Agent agent) {
        if (agent instanceof JAgent j) {
            return j;
        }
        throw new SwarmException("Agent '" + agent.id() + "' is not a JAgent");
    }
}
