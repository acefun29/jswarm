// @Tool 示例：模拟订单查询
package com.jswarm.examples.springai;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;

public class OrderTools {

    private static final Map<String, String> ORDERS = Map.of(
            "ORD-001", "已发货，预计 3 天后到达",
            "ORD-002", "正在拣货，预计明日发货",
            "ORD-003", "已签收（签收人：前台）"
    );

    private static final Map<String, String> USERS = Map.of(
            "ORD-001", "张三",
            "ORD-002", "李四",
            "ORD-003", "王五"
    );

    @Tool(description = "查询订单状态")
    public String lookupOrder(@ToolParam(description = "订单号") String orderId) {
        String status = ORDERS.get(orderId);
        if (status == null) {
            return "未找到订单：" + orderId;
        }
        return "订单 " + orderId + "（" + USERS.get(orderId) + "）：" + status;
    }

    @Tool(description = "查询用户的所有订单")
    public String listOrders(@ToolParam(description = "用户名") String userName) {
        StringBuilder sb = new StringBuilder("用户 " + userName + " 的订单：\n");
        boolean found = false;
        for (var entry : ORDERS.entrySet()) {
            if (USERS.get(entry.getKey()).equals(userName)) {
                sb.append("- ").append(entry.getKey()).append("：").append(entry.getValue()).append("\n");
                found = true;
            }
        }
        return found ? sb.toString() : "用户 " + userName + " 暂无订单";
    }
}
