CREATE TABLE IF NOT EXISTS km_purge_audit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  doc_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL,
  object_key VARCHAR(512) DEFAULT NULL,
  purge_status VARCHAR(16) NOT NULL COMMENT 'SUCCESS/FAILED',
  error_stage VARCHAR(32) DEFAULT NULL,
  error_message TEXT DEFAULT NULL,
  purged_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_purge_audit_doc (doc_id),
  KEY idx_purge_audit_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物理清理独立审计表';

