package com.jswarm.examples.lc4jlegacy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class OrderRepository {

    private final Map<String, OrderRecord> byOrderId = new LinkedHashMap<>();

    public OrderRepository() {
        seed("ORD-10086", "u001", "已发货", "顺丰", "SF1234567890");
        seed("ORD-20001", "u002", "待支付", null, null);
        seed("ORD-30088", "u001", "配送中", "京东", "JD9988776655");
    }

    private void seed(String orderId, String userId, String status, String carrier, String trackingNo) {
        byOrderId.put(orderId, new OrderRecord(orderId, userId, status, carrier, trackingNo));
    }

    public Optional<OrderRecord> findByOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byOrderId.get(orderId.trim().toUpperCase()));
    }

    public List<OrderRecord> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        String normalized = userId.trim().toLowerCase();
        List<OrderRecord> result = new ArrayList<>();
        for (OrderRecord order : byOrderId.values()) {
            if (normalized.equals(order.userId())) {
                result.add(order);
            }
        }
        return result;
    }

    public String summarizeForContext(List<OrderRecord> orders) {
        if (orders.isEmpty()) {
            return "暂无订单";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < orders.size(); i++) {
            if (i > 0) {
                sb.append("；");
            }
            sb.append(formatForTool(orders.get(i)));
        }
        return sb.toString();
    }

    public String formatForTool(OrderRecord order) {
        StringBuilder sb = new StringBuilder();
        sb.append("订单号 ").append(order.orderId())
                .append("，状态：").append(order.status());
        if (order.carrier() != null && !order.carrier().isBlank()) {
            sb.append("，承运商 ").append(order.carrier());
        }
        if (order.trackingNo() != null && !order.trackingNo().isBlank()) {
            sb.append("，运单号 ").append(order.trackingNo());
        }
        return sb.toString();
    }
}
