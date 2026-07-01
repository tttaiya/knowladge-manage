-- =============================================================================
-- V3: 文档管理模块整合（加法迁移，不重命名）
-- =============================================================================
-- 背景：
--   程雨彤的文档管理模块（km-admin-service/document/*）需要以下字段：
--     mime_type / file_size / file_hash / uploader_name / chunk_count / retry_count
--   主项目 km_document 当前只有 16 字段，缺少上述字段。
--
-- 迁移策略（R12 加法迁移）：
--   - 保留 file_name / object_key / document_status / user_id 字段名不重命名
--   - 仅 ADD COLUMN，NOT NULL 字段带 DEFAULT 兼容已有数据
--   - 全部用 information_schema 检测保证幂等，重跑不挂
--
-- 字段顺序（AFTER）：
--   object_key, mime_type, file_size, file_hash, extension(原), ...
--   uploader_name 紧跟 user_id 之后
--   chunk_count / retry_count 紧跟 error_message 之后
-- =============================================================================

-- 使用动态 SQL + information_schema 检测，避免 ADD COLUMN IF NOT EXISTS 兼容性问题
SET @sql_mime_type := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_document' AND COLUMN_NAME = 'mime_type') > 0,
    'SELECT ''mime_type already exists'' AS info',
    'ALTER TABLE km_document ADD COLUMN mime_type VARCHAR(128) NULL COMMENT ''MIME 类型'' AFTER object_key'
  )
);
PREPARE stmt FROM @sql_mime_type; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_file_size := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_document' AND COLUMN_NAME = 'file_size') > 0,
    'SELECT ''file_size already exists'' AS info',
    'ALTER TABLE km_document ADD COLUMN file_size BIGINT NOT NULL DEFAULT 0 COMMENT ''文件大小（字节）'' AFTER mime_type'
  )
);
PREPARE stmt FROM @sql_file_size; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_file_hash := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_document' AND COLUMN_NAME = 'file_hash') > 0,
    'SELECT ''file_hash already exists'' AS info',
    'ALTER TABLE km_document ADD COLUMN file_hash CHAR(64) NULL COMMENT ''SHA-256 哈希'' AFTER file_size'
  )
);
PREPARE stmt FROM @sql_file_hash; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_uploader_name := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_document' AND COLUMN_NAME = 'uploader_name') > 0,
    'SELECT ''uploader_name already exists'' AS info',
    'ALTER TABLE km_document ADD COLUMN uploader_name VARCHAR(64) NULL COMMENT ''上传人名称快照'' AFTER user_id'
  )
);
PREPARE stmt FROM @sql_uploader_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_chunk_count := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_document' AND COLUMN_NAME = 'chunk_count') > 0,
    'SELECT ''chunk_count already exists'' AS info',
    'ALTER TABLE km_document ADD COLUMN chunk_count INT NOT NULL DEFAULT 0 COMMENT ''切片数（冗余）'' AFTER error_message'
  )
);
PREPARE stmt FROM @sql_chunk_count; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_retry_count := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_document' AND COLUMN_NAME = 'retry_count') > 0,
    'SELECT ''retry_count already exists'' AS info',
    'ALTER TABLE km_document ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT ''任务层重试次数'' AFTER chunk_count'
  )
);
PREPARE stmt FROM @sql_retry_count; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 索引：同一知识库下 SHA-256 去重提示
SET @sql_idx_hash := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_document' AND INDEX_NAME = 'idx_kb_hash') > 0,
    'SELECT ''idx_kb_hash already exists'' AS info',
    'ALTER TABLE km_document ADD KEY idx_kb_hash (kb_id, file_hash)'
  )
);
PREPARE stmt FROM @sql_idx_hash; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 幂等建 km_document_tag 唯一索引（doc_id, tag_name）
SET @sql_tag_index := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_document_tag' AND INDEX_NAME = 'uk_doc_tag') > 0,
    'SELECT ''uk_doc_tag already exists'' AS info',
    'ALTER TABLE km_document_tag ADD UNIQUE KEY uk_doc_tag (doc_id, tag_name)'
  )
);
PREPARE stmt FROM @sql_tag_index; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =============================================================================
-- 验证清单（执行后用 DESC 检查）
-- =============================================================================
-- DESC km_document;
-- 应出现字段顺序：
--   id, kb_id, user_id, file_name, object_key,
--   mime_type, file_size, file_hash, extension,
--   document_status, error_stage, error_message,
--   chunk_count, retry_count,
--   uploader_name,
--   current_version_no, next_version_no,
--   is_deleted, deleted_at,
--   created_at, updated_at
--
-- SHOW INDEX FROM km_document_tag;
-- 应包含 Key_name = uk_doc_tag, Column_name = (doc_id, tag_name) 唯一索引
-- =============================================================================
