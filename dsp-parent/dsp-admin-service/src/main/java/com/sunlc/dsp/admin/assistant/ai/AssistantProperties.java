package com.sunlc.dsp.admin.assistant.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 助手配置。前缀 {@code dsp.assistant}。
 * <p>
 * 敏感项（apiKey）走环境变量 {@code DSP_ASSISTANT_API_KEY}，生产必须配置。
 * 启动期不强校验 apiKey/model/baseUrl：未配置时 Bean 仍可创建（避免 NPE），
 * 在 {@link AgentScopeAiGateway#streamChat} 调用时给出明确异常。
 */
@ConfigurationProperties(prefix = "dsp.assistant")
public class AssistantProperties {

    /** 模型名（如 qwen-plus、gpt-4o 等），由 AgentScope 侧 model 配置决定。 */
    private String model;

    /** API Key，走环境变量 DSP_ASSISTANT_API_KEY（对应 dsp.assistant.api-key）。 */
    private String apiKey;

    /** 模型服务 base url。 */
    private String baseUrl;

    /** 单次调用超时（毫秒）。 */
    private long timeoutMs = 60000L;

    /** 失败重试次数（不含首次）。 */
    private int maxRetries = 0;

    /** SSE 连接超时（毫秒），P4 使用。 */
    private long sseTimeoutMs = 120000L;

    /** 单用户并发会话上限，P4 限流使用。 */
    private int maxConcurrentPerUser = 1;

    /** 资产目录路径（对应环境变量 DSP_ASSISTANT_ASSETS_PATH），P3 检索层使用。 */
    private String assetsPath = "ai-assets";

    /** 元数据读取配置（Text2API T1）。 */
    private Metadata metadata = new Metadata();

    /** 元数据读取安全配置。 */
    public static class Metadata {
        /** 连接超时（秒）。 */
        private int timeoutSeconds = 10;
        /** 最大返回表数。 */
        private int maxTables = 100;
        /** 每表最大字段数。 */
        private int maxColumns = 200;

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getMaxTables() { return maxTables; }
        public void setMaxTables(int maxTables) { this.maxTables = maxTables; }
        public int getMaxColumns() { return maxColumns; }
        public void setMaxColumns(int maxColumns) { this.maxColumns = maxColumns; }
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getSseTimeoutMs() {
        return sseTimeoutMs;
    }

    public void setSseTimeoutMs(long sseTimeoutMs) {
        this.sseTimeoutMs = sseTimeoutMs;
    }

    public int getMaxConcurrentPerUser() {
        return maxConcurrentPerUser;
    }

    public void setMaxConcurrentPerUser(int maxConcurrentPerUser) {
        this.maxConcurrentPerUser = maxConcurrentPerUser;
    }

    public String getAssetsPath() {
        return assetsPath;
    }

    public void setAssetsPath(String assetsPath) {
        this.assetsPath = assetsPath;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
}
