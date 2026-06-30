CREATE DATABASE IF NOT EXISTS km DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE km;
SOURCE /docker-entrypoint-initdb.d/knowledge-base.sql;
SOURCE /docker-entrypoint-initdb.d/document.sql;
SOURCE /docker-entrypoint-initdb.d/document-process-task.sql;
SOURCE /docker-entrypoint-initdb.d/document-status-log.sql;
SOURCE /docker-entrypoint-initdb.d/processed-event.sql;
SOURCE /docker-entrypoint-initdb.d/document-version.sql;
SOURCE /docker-entrypoint-initdb.d/purge-audit.sql;
SOURCE /docker-entrypoint-initdb.d/review.sql;

INSERT INTO km_knowledge_base(id,name,is_deleted,created_at,updated_at)
VALUES (1,'默认知识库',0,now(),now())
ON DUPLICATE KEY UPDATE name=values(name), updated_at=values(updated_at);

INSERT INTO km_document(kb_id,user_id,file_name,object_key,extension,document_status,created_at,updated_at)
VALUES
  (1,1,'demo-policy.pdf','demo/demo-policy.pdf','pdf','UPLOADED',now(),now())
ON DUPLICATE KEY UPDATE updated_at=values(updated_at);
