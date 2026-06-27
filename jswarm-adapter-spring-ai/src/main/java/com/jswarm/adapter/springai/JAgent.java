package com.jswarm.adapter.springai;

import com.jswarm.adapter.springai.tool.SaiAgentExtractor;
import com.jswarm.adapter.springai.tool.SaiToolBridge;
import com.jswarm.core.Agent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Objects;

public interface JAgent extends Agent {

    ChatModel model();

    default StreamingChatModel streamingModel() {
        ChatModel m = model();
        if (m instanceof StreamingChatModel) {
            return (StreamingChatModel) m;
        }
        return null;
    }

    default List<ToolCallback> externalTools() {
        return List.of();
    }

    default ExternalToolExecutor toolExecutor() {
        return null;
    }

    static DefaultJAgent.Builder builder(String id, String name) {
        return DefaultJAgent.builder(id, name);
    }

    static JAgentDecorator decorate(JAgent delegate) {
        return new JAgentDecorator(Objects.requireNonNull(delegate, "delegate"));
    }

    static JAgent fromTools(String id, String name, String description,
                            String instructions, ChatModel model, Object... toolBeans) {
        SaiToolBridge.BridgeResult bridge = SaiToolBridge.bridge(toolBeans);
        return new DefaultJAgent(id, name, description, instructions, model,
                bridge.callbacks(), bridge.executor());
    }

    static JAgent fromAiService(String id, String name, String description,
                                Class<?> aiServiceClass, ChatModel model, Object... toolBeans) {
        String instructions = SaiAgentExtractor.extractInstructions(aiServiceClass);
        return fromTools(id, name, description, instructions, model, toolBeans);
    }
}
