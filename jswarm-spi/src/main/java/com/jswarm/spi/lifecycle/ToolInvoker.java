// 工具调用 SPI，计划 03 迁移实现
package com.jswarm.spi.lifecycle;

/**
 * 线程安全：实现类须为无状态或内部同步；须尊重 ToolContext 中的 deadline 与 cancellation。
 */
public interface ToolInvoker {

    ToolResult execute(ToolInvocation invocation, ToolContext context);
}
