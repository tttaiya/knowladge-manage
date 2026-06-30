package com.km.admin.config;

import com.km.admin.config.dto.ConfigChangedEvent;
import com.km.admin.config.dto.ConnectionTestRequest;
import com.km.admin.config.dto.ConnectionTestResult;
import com.km.admin.config.dto.EmbeddingConfigDTO;
import com.km.admin.config.dto.ParserConfigDTO;
import com.km.admin.config.dto.RerankConfigDTO;

/**
 * 系统配置服务接口。
 * 设计文档 7.5：GET/PUT /api/v1/configs/{embedding,rerank,parser} + POST /api/v1/configs/test-connection。
 */
public interface ConfigService {

    EmbeddingConfigDTO getEmbeddingConfig();

    EmbeddingConfigDTO updateEmbeddingConfig(EmbeddingConfigDTO dto);

    RerankConfigDTO getRerankConfig();

    RerankConfigDTO updateRerankConfig(RerankConfigDTO dto);

    ParserConfigDTO getParserConfig();

    /**
     * 更新 parser 配置（Worker 启动初始化也调这个端点拉取配置）。
     * 该方法不带事务，由 controller 决定是否加 @Transactional。
     */
    ParserConfigDTO updateParserConfig(ParserConfigDTO dto);

    ConnectionTestResult testConnection(ConnectionTestRequest req);
}