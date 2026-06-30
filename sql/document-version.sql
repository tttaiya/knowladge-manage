CREATE TABLE IF NOT EXISTS km_document_version (
  id BIGINT NOT NULL AUTO_INCREMENT,
  doc_id BIGINT NOT NULL,
  version_no BIGINT NOT NULL,
  strategy_version BIGINT DEFAULT NULL,
  task_id BIGINT DEFAULT NULL,
  version_status VARCHAR(16) NOT NULL COMMENT 'BUILDING/ACTIVE/RETIRED/FAILED',
  created_at DATETIME NOT NULL,
  activated_at DATETIME DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_doc_version (doc_id, version_no),
  KEY idx_doc_version_status (doc_id, version_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档处理版本表';

CREATE TABLE IF NOT EXISTS km_vector_cleanup_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  doc_id BIGINT NOT NULL,
  keep_version_no BIGINT NOT NULL,
  cleanup_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  error_message TEXT DEFAULT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_vector_cleanup (cleanup_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='旧版本向量异步清理任务表';

