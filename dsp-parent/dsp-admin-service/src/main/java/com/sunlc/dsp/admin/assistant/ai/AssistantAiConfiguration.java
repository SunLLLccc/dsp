package com.sunlc.dsp.admin.assistant.ai;

import io.agentscope.core.ReActAgent;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 适配层 Bean 装配。
 * <p>
 * 启动安全：apiKey/model/baseUrl 未配置时，{@link AssistantProperties} 与 {@link AiGateway} Bean 仍可正常创建，
 * 不在启动期抛异常；配置缺失在调用 {@link AiGateway#streamChat} 时由 {@link AgentScopeAiGateway} 给出明确异常。
 * <p>
 * {@link AgentScopeAiGateway.AgentSupplier} 提供真实 {@link HarnessAgent}：
 * 基于 {@link OpenAIChatModel}（OpenAI 兼容接口，适用于 Qwen/DeepSeek/GLM 等国产模型）构造 Model，
 * 包装为 {@link ReActAgent} 再交给 {@link HarnessAgent.Builder#fromAgent}。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantAiConfiguration {

    @Bean
    public AiGateway aiGateway(AssistantProperties properties) {
        AgentScopeAiGateway.AgentSupplier supplier = buildRealAgentSupplier(properties);
        return new AgentScopeAiGateway(properties, supplier);
    }

    /**
     * 构造真实 AgentSupplier：OpenAIChatModel → ReActAgent → HarnessAgent。
     * Model 在 supplier 内部惰性构造（每次 get 新建 agent，避免有状态 agent 跨会话复用）。
     * 若 apiKey/model/baseUrl 未配置，构造会在调用时抛明确异常（被 gateway 的配置校验前置拦截）。
     */
    private AgentScopeAiGateway.AgentSupplier buildRealAgentSupplier(AssistantProperties properties) {
        return () -> {
            OpenAIChatModel model = OpenAIChatModel.builder()
                    .apiKey(properties.getApiKey())
                    .modelName(properties.getModel())
                    .baseUrl(properties.getBaseUrl())
                    .stream(true)
                    .build();
            ReActAgent reActAgent = ReActAgent.builder()
                    .name("dsp-assistant")
                    .description("DSP 智能助手")
                    .model(model)
                    .build();
            return HarnessAgent.Builder.fromAgent(reActAgent).build();
        };
    }
}
