package com.jswarm.examples.lc4jlegacy;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface SalesAssistant {

    @SystemMessage("你是销售专员。当前用户：{user_name}（{vip_level}）。热情解答产品咨询与价格问题。")
    String chat(@UserMessage String userMessage);
}
