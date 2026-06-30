-- F6 系统配置：km_system_config 加法迁移
-- R12：加法迁移，不破坏现有表
-- R17：SQL 放根 sql/ 目录
-- 13 条 seed 数据：embedding 4 + rerank 5 + parser 4
-- 幂等设计：ON DUPLICATE KEY UPDATE description=VALUES(description)
--   **不覆盖** config_value（保留用户已修改的值）
-- 默认值：api_base / api_key 留空字符串（容器内 localhost 陷阱）
--   模型名 / 维度 / TopN / 阈值 / 并发 / 重试 / 超时保留有效默认值

CREATE TABLE IF NOT EXISTS km_system_config (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    config_key   VARCHAR(128) NOT NULL,
    config_value TEXT         NOT NULL,
    description  VARCHAR(255) NOT NULL DEFAULT '',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_km_system_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='系统配置（F6）：支持嵌入 / 重排序 / 解析器三类配置的持久化与热更新';

-- ============================================================
-- embedding 组（4 条）
-- ============================================================
INSERT INTO km_system_config (config_key, config_value, description) VALUES
('embedding.model',        'text-embedding-v1', '嵌入模型名称'),
('embedding.api_base',     '',                  '嵌入服务 API 地址（容器内不要写 localhost，留空待用户填写真实地址）'),
('embedding.api_key',      '',                  '嵌入服务 API Key（不入 MQ 事件，R25）'),
('embedding.dimension',    '1024',              '向量维度')
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- ============================================================
-- rerank 组（5 条）
-- ============================================================
INSERT INTO km_system_config (config_key, config_value, description) VALUES
('rerank.model',        'bge-reranker-v2-m3', '重排序模型名称'),
('rerank.api_base',     '',                   '重排序服务 API 地址（容器内不要写 localhost）'),
('rerank.api_key',      '',                   '重排序服务 API Key（不入 MQ 事件，R25）'),
('rerank.top_n',        '10',                 'Top N（重排序后返回的最大候选数）'),
('rerank.threshold',    '0.7',                '相似度阈值（低于此分数的候选被过滤）')
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- ============================================================
-- parser 组（4 条）
-- ============================================================
INSERT INTO km_system_config (config_key, config_value, description) VALUES
('parser.paddleocr_enabled',    'false', '是否启用 PaddleOCR（true/false）'),
('parser.max_concurrent_tasks', '4',     '解析任务全局最大并发数（Worker DynamicConfigHolder 热加载，R28）'),
('parser.max_retry_count',      '3',     '任务失败最大重试次数'),
('parser.timeout_seconds',      '30',    '单步任务超时时间（秒）')
ON DUPLICATE KEY UPDATE description = VALUES(description);