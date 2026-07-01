CREATE DATABASE IF NOT EXISTS km DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE km;
-- 基础表已在 01-14 号脚本中创建，此处只插入 demo 测试数据

INSERT INTO km_knowledge_base(id,name,is_deleted,created_at,updated_at)
VALUES (1,'默认知识库',0,now(),now())
ON DUPLICATE KEY UPDATE name=values(name), updated_at=values(updated_at);

INSERT INTO km_document(kb_id,user_id,file_name,object_key,extension,document_status,created_at,updated_at)
VALUES
  (1,1,'demo-policy.pdf','demo/demo-policy.pdf','pdf','UPLOADED',now(),now())
ON DUPLICATE KEY UPDATE updated_at=values(updated_at);
