// Spring AI 与 Canonical 工具描述互转
package com.jswarm.adapter.springai.runtime;

import com.jswarm.spi.message.ToolDescriptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

public final class SpringAiToolCodec {

    public ToolDescriptor decode(ToolCallback callback) {
        ToolDefinition definition = callback.getToolDefinition();
        return new ToolDescriptor(definition.name(), definition.description(), definition.inputSchema());
    }

    public ToolCallback encode(ToolDescriptor descriptor) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(descriptor.name())
                .description(descriptor.description())
                .inputSchema(descriptor.inputSchema())
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return ToolMetadata.builder().build();
            }

            @Override
            public String call(String toolInput) {
                throw new UnsupportedOperationException("Tool execution is controlled by Jswarm Runtime");
            }
        };
    }
}
