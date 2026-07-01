// Swarm 与 Spring Bean 配置
package com.jswarm.examples.springai;

import com.jswarm.adapter.springai.JAgent;
import com.jswarm.core.Swarm;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShowcaseConfig {

    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";

    @Bean
    public OpenAiChatModel chatModel() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getProperty("deepseek.api.key");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请设置环境变量 DEEPSEEK_API_KEY");
        }
        ClientOptions clientOptions = ClientOptions.builder()
                .baseUrl(DEEPSEEK_BASE_URL)
                .apiKey(apiKey)
                .build();
        OpenAIClient client = new OpenAIClientImpl(clientOptions);
        return OpenAiChatModel.builder()
                .openAiClient(client)
                .options(OpenAiChatOptions.builder().model("deepseek-chat").build())
                .build();
    }

    @Bean
    public Swarm swarm(OpenAiChatModel chatModel) {
        JAgent sales = JAgent.builder("sales", "销售专员")
                .description("处理产品咨询、报价和购买意向")
                .instructions("""
                        你是销售专员。用户 {user_name}。
                        回答产品问题并提供报价。如果用户需要技术支持，handoff 给 tech。
                        """)
                .model(chatModel)
                .build();

        JAgent tech = JAgent.builder("tech", "技术支持")
                .description("解决技术问题和订单查询")
                .instructions("""
                        你是技术支持。用户 {user_name}。
                        帮助用户解决技术问题、查询订单状态。
                        """)
                .model(chatModel)
                .tools(new OrderTools())
                .build();

        JAgent router = JAgent.builder("router", "路由专员")
                .description("分析用户意图并分发")
                .instructions("""
                        你是客服路由专员。用户 {user_name}。
                        分析用户问题并 handoff 给合适的专员：
                        - 产品咨询、报价 → handoff 给 sales
                        - 技术问题、订单查询 → handoff 给 tech
                        """)
                .model(chatModel)
                .build();

        return Swarm.create("智能客服")
                .agent(router).agent(sales).agent(tech)
                .entry("router")
                .handoff("router", "sales")
                .handoff("router", "tech")
                .handoff("sales", "tech")
                .handoff("tech", "sales")
                .build();
    }
}
