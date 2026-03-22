package me.aboullaite.rag.retriever.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "retriever")
public class RetrieverProperties {

    /**
     * Base URL for Weaviate, e.g. http://weaviate:8080.
     */
    private String weaviateUrl = "http://localhost:8080";

    /**
     * Optional OpenSearch URL, e.g. http://opensearch:9200.
     */
    private String opensearchUrl;

    /**
     * Retrieval timeout in milliseconds.
     */
    private long timeoutMs = 250;

    /**
     * Default topK value when not supplied.
     */
    private int topKDefault = 5;

    private boolean hybridEnabled = true;

    private boolean rerankEnabled = true;

    private int retrieveK = 20;

    public String getWeaviateUrl() {
        return weaviateUrl;
    }

    public void setWeaviateUrl(String weaviateUrl) {
        this.weaviateUrl = weaviateUrl;
    }

    public String getOpensearchUrl() {
        return opensearchUrl;
    }

    public void setOpensearchUrl(String opensearchUrl) {
        this.opensearchUrl = opensearchUrl;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getTopKDefault() {
        return topKDefault;
    }

    public void setTopKDefault(int topKDefault) {
        this.topKDefault = topKDefault;
    }

    public boolean isHybridEnabled() {
        return hybridEnabled;
    }

    public void setHybridEnabled(boolean hybridEnabled) {
        this.hybridEnabled = hybridEnabled;
    }

    public boolean isRerankEnabled() {
        return rerankEnabled;
    }

    public void setRerankEnabled(boolean rerankEnabled) {
        this.rerankEnabled = rerankEnabled;
    }

    public int getRetrieveK() {
        return retrieveK;
    }

    public void setRetrieveK(int retrieveK) {
        this.retrieveK = retrieveK;
    }
}
