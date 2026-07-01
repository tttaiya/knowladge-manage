package com.km.search.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SearchDynamicConfigHolder {
    private static final Logger log = LoggerFactory.getLogger(SearchDynamicConfigHolder.class);

    private final JdbcTemplate jdbcTemplate;
    private volatile long configVersion = 0L;
    private volatile String embeddingModel;
    private volatile String embeddingApiBase;
    private volatile String rerankModel;
    private volatile String rerankApiBase;
    private volatile Integer rerankTopN;
    private volatile Double rerankThreshold;

    public SearchDynamicConfigHolder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void loadInitialConfig() {
        try {
            applyValues(loadSafeConfigValues(), System.currentTimeMillis(), "startup");
        } catch (Exception e) {
            log.warn("Search dynamic config initial load failed: {}", e.getMessage());
        }
    }

    public synchronized boolean applyEvent(ConfigChangedEvent event) {
        if (event == null) {
            return false;
        }
        long incomingVersion = event.getConfigVersion() == null ? System.currentTimeMillis() : event.getConfigVersion();
        if (incomingVersion <= configVersion) {
            log.info("Ignored stale config event: incomingVersion={}, currentVersion={}", incomingVersion, configVersion);
            return false;
        }
        Map<String, String> values = event.getValues();
        if (values == null || values.isEmpty()) {
            values = loadSafeConfigValues();
        }
        applyValues(values, incomingVersion, event.getConfigGroup());
        return true;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public String getEmbeddingApiBase() {
        return embeddingApiBase;
    }

    public String getRerankModel() {
        return rerankModel;
    }

    public String getRerankApiBase() {
        return rerankApiBase;
    }

    public Integer getRerankTopN() {
        return rerankTopN;
    }

    public Double getRerankThreshold() {
        return rerankThreshold;
    }

    public long getConfigVersion() {
        return configVersion;
    }

    private void applyValues(Map<String, String> values, long version, String group) {
        embeddingModel = text(values.get("embedding.model"), embeddingModel);
        embeddingApiBase = text(values.get("embedding.api_base"), embeddingApiBase);
        rerankModel = text(values.get("rerank.model"), rerankModel);
        rerankApiBase = text(values.get("rerank.api_base"), rerankApiBase);
        rerankTopN = intValue(values.get("rerank.top_n"), rerankTopN);
        rerankThreshold = doubleValue(values.get("rerank.threshold"), rerankThreshold);
        configVersion = version;
        log.info("Search config refreshed: group={}, version={}, embeddingModel={}, embeddingApiBase={}, rerankModel={}, rerankApiBase={}, rerankTopN={}, rerankThreshold={}",
                group, version, embeddingModel, embeddingApiBase, rerankModel, rerankApiBase, rerankTopN, rerankThreshold);
    }

    private Map<String, String> loadSafeConfigValues() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT config_key, config_value FROM km_system_config WHERE config_key NOT LIKE '%.api_key'");
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get("config_key");
            Object value = row.get("config_value");
            if (key != null) {
                values.put(String.valueOf(key), value == null ? "" : String.valueOf(value));
            }
        }
        return values;
    }

    private String text(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private Integer intValue(String value, Integer fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Double doubleValue(String value, Double fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
