ALTER TABLE km_document_chunk
  ADD COLUMN IF NOT EXISTS is_edited TINYINT NOT NULL DEFAULT 0 AFTER is_active;

CREATE TABLE IF NOT EXISTS km_review_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  doc_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL COMMENT 'APPROVE/REJECT',
  comment TEXT DEFAULT NULL,
  operator_user_id BIGINT DEFAULT 0,
  operator_name VARCHAR(64) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_review_doc (doc_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工审核记录表';

CREATE TABLE IF NOT EXISTS km_chunk_edit_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  chunk_id BIGINT NOT NULL,
  before_content MEDIUMTEXT DEFAULT NULL,
  after_content MEDIUMTEXT NOT NULL,
  action VARCHAR(32) NOT NULL DEFAULT 'EDIT',
  operator_user_id BIGINT DEFAULT 0,
  operator_name VARCHAR(64) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_chunk_edit_log_chunk (chunk_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='切片人工编辑日志表';
