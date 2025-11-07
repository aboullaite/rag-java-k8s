package me.aboullaite.rag.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public class OrchestratorProperties {

    private String retrieverUrl = "http://localhost:8081";
    private String llmUrl = "http://localhost:8082";
    private double cacheSimThreshold = 0.90;
    private long genTimeoutMs = 1800;
    private String modelName = "gemma-2-2b-it";
    private String systemPrompt;
    private long cacheTtlSeconds = 600;

    public String getRetrieverUrl() {
        return retrieverUrl;
    }

    public void setRetrieverUrl(String retrieverUrl) {
        this.retrieverUrl = retrieverUrl;
    }

    public String getLlmUrl() {
        return llmUrl;
    }

    public void setLlmUrl(String llmUrl) {
        this.llmUrl = llmUrl;
    }

    public double getCacheSimThreshold() {
        return cacheSimThreshold;
    }

    public void setCacheSimThreshold(double cacheSimThreshold) {
        this.cacheSimThreshold = cacheSimThreshold;
    }

    public long getGenTimeoutMs() {
        return genTimeoutMs;
    }

    public void setGenTimeoutMs(long genTimeoutMs) {
        this.genTimeoutMs = genTimeoutMs;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }
}
