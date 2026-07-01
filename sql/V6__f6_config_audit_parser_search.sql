CREATE TABLE IF NOT EXISTS km_config_change_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    operator_id     VARCHAR(64)  NOT NULL DEFAULT '',
    operator_name   VARCHAR(128) NOT NULL DEFAULT '',
    config_group    VARCHAR(32)  NOT NULL,
    config_version  BIGINT       NOT NULL,
    change_summary  VARCHAR(512) NOT NULL DEFAULT '',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_km_config_change_log_group_version (config_group, config_version),
    KEY idx_km_config_change_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='F6 system config audit log; API keys are never stored here';

INSERT INTO km_system_config (config_key, config_value, description) VALUES
('parser.api_base', '', 'Parser/OCR health-check API base URL'),
('parser.chunk_size', '500', 'Parser default chunk size'),
('parser.chunk_overlap', '50', 'Parser default chunk overlap')
ON DUPLICATE KEY UPDATE description = VALUES(description);
