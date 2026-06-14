package com.jswarm.examples.lc4jlegacy;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface OrderAssistant {

    @SystemMessage({
            "你是订单专员。当前用户：{user_name}。",
            "必须调用工具获取数据后再回复，禁止编造订单信息。",
            "用户给出订单号时用 lookupOrder；未给订单号或要看全部订单时用 listUserOrders。",
            "回复内容仅基于工具返回结果组织语言。",
            "若用户需要消费趋势/数据分析摘要，可 delegate 给 analyst。"
    })
    String chat(@UserMessage String userMessage);
}
