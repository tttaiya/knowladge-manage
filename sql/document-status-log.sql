CREATE TABLE IF NOT EXISTS km_document_status_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  doc_id BIGINT NOT NULL,
  stage VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  message TEXT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_status_log_doc (doc_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档状态流转日志';

