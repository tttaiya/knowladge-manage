CREATE TABLE IF NOT EXISTS km_processed_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_id VARCHAR(64) NOT NULL,
  task_id BIGINT NOT NULL,
  doc_id BIGINT NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_processed_event (event_id),
  KEY idx_processed_task (task_id),
  KEY idx_processed_doc (doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务结果幂等消费事件表';

