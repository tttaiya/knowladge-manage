INSERT INTO km_system_config (config_key, config_value, description) VALUES
('parser.chunk_size', '500', 'Parser default chunk size'),
('parser.chunk_overlap', '50', 'Parser default chunk overlap')
ON DUPLICATE KEY UPDATE description = VALUES(description);
