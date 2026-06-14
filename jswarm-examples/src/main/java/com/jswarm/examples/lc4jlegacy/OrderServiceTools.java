package com.jswarm.examples.lc4jlegacy;

import com.jswarm.core.SwarmContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class OrderServiceTools {

    private final OrderRepository orderRepository;

    public OrderServiceTools(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Tool(name = "lookupOrder", value = "根据订单号查询物流与状态")
    public String lookupOrder(@P("订单号") String orderId) {
        return orderRepository.findByOrderId(orderId)
                .map(orderRepository::formatForTool)
                .orElse("未找到订单：" + orderId);
    }

    @Tool(name = "listUserOrders", value = "查询当前登录用户的全部订单摘要")
    public String listUserOrders() {
        SwarmContext ctx = SwarmContext.current();
        if (ctx == null) {
            return "无法获取用户上下文";
        }
        String userId = ctx.get("user_id", String.class);
        if (userId == null || userId.isBlank()) {
            return "无法识别当前用户";
        }
        return orderRepository.summarizeForContext(orderRepository.findByUserId(userId));
    }
}
