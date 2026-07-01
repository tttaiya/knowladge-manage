-- V5 知识库管理加法迁移（commit #27）
-- 设计依据：F2 整合实施步骤 v1.0 文档第 5 章
-- 原则：加法迁移，保留现有数据
-- 兼容写法：使用动态 SQL + information_schema 检测，避免 ADD COLUMN IF NOT EXISTS 兼容性问题

-- ============================================================
-- 1. 新增 F2 字段（按 F2 v1.0 文档 5.1 节）
-- ============================================================

SET @sql_desc := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_knowledge_base' AND COLUMN_NAME = 'description') > 0,
    'SELECT 1', 'ALTER TABLE km_knowledge_base ADD COLUMN description VARCHAR(500) NULL AFTER name'
  )
);
PREPARE stmt FROM @sql_desc; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_cat := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_knowledge_base' AND COLUMN_NAME = 'category') > 0,
    'SELECT 1', 'ALTER TABLE km_knowledge_base ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT ''GENERAL'' AFTER description'
  )
);
PREPARE stmt FROM @sql_cat; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_chunk_strategy := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_knowledge_base' AND COLUMN_NAME = 'chunk_strategy') > 0,
    'SELECT 1', 'ALTER TABLE km_knowledge_base ADD COLUMN chunk_strategy VARCHAR(32) NOT NULL DEFAULT ''HEADING'' AFTER retrieval_strategy'
  )
);
PREPARE stmt FROM @sql_chunk_strategy; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_chunk_size := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_knowledge_base' AND COLUMN_NAME = 'chunk_size') > 0,
    'SELECT 1', 'ALTER TABLE km_knowledge_base ADD COLUMN chunk_size INT NOT NULL DEFAULT 500 AFTER chunk_strategy'
  )
);
PREPARE stmt FROM @sql_chunk_size; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_chunk_overlap := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_knowledge_base' AND COLUMN_NAME = 'chunk_overlap') > 0,
    'SELECT 1', 'ALTER TABLE km_knowledge_base ADD COLUMN chunk_overlap INT NOT NULL DEFAULT 50 AFTER chunk_size'
  )
);
PREPARE stmt FROM @sql_chunk_overlap; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_separators := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_knowledge_base' AND COLUMN_NAME = 'separators_json') > 0,
    'SELECT 1', 'ALTER TABLE km_knowledge_base ADD COLUMN separators_json JSON NULL AFTER chunk_overlap'
  )
);
PREPARE stmt FROM @sql_separators; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_doc_count := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_knowledge_base' AND COLUMN_NAME = 'document_count') > 0,
    'SELECT 1', 'ALTER TABLE km_knowledge_base ADD COLUMN document_count INT NOT NULL DEFAULT 0 AFTER separators_json'
  )
);
PREPARE stmt FROM @sql_doc_count; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_created_uid := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_knowledge_base' AND COLUMN_NAME = 'created_by_user_id') > 0,
    'SELECT 1', 'ALTER TABLE km_knowledge_base ADD COLUMN created_by_user_id VARCHAR(36) NULL AFTER document_count'
  )
);
PREPARE stmt FROM @sql_created_uid; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_created_name := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_knowledge_base' AND COLUMN_NAME = 'created_by_name') > 0,
    'SELECT 1', 'ALTER TABLE km_knowledge_base ADD COLUMN created_by_name VARCHAR(64) NULL AFTER created_by_user_id'
  )
);
PREPARE stmt FROM @sql_created_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_strat_ver := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_knowledge_base' AND COLUMN_NAME = 'strategy_version') > 0,
    'SELECT 1', 'ALTER TABLE km_knowledge_base ADD COLUMN strategy_version BIGINT NOT NULL DEFAULT 1 AFTER created_by_name'
  )
);
PREPARE stmt FROM @sql_strat_ver; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_deleted_at := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_knowledge_base' AND COLUMN_NAME = 'deleted_at') > 0,
    'SELECT 1', 'ALTER TABLE km_knowledge_base ADD COLUMN deleted_at DATETIME NULL AFTER is_deleted'
  )
);
PREPARE stmt FROM @sql_deleted_at; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. 历史数据补齐默认值（针对现有列）
-- ============================================================

UPDATE km_knowledge_base
SET category = COALESCE(category, 'GENERAL'),
    chunk_strategy = COALESCE(chunk_strategy, 'HEADING'),
    chunk_size = COALESCE(chunk_size, 500),
    chunk_overlap = COALESCE(chunk_overlap, 50),
    separators_json = COALESCE(separators_json, JSON_ARRAY()),
    strategy_version = COALESCE(strategy_version, 1),
    document_count = COALESCE(document_count, 0);

-- ============================================================
-- 3. 名称唯一性改造（F2 v1.0 5.2 节）
--    逻辑删除项目：仅活动知识库唯一；已删除名称允许复用
-- ============================================================

SET @sql_active_name := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_knowledge_base' AND COLUMN_NAME = 'active_name') > 0,
    'SELECT 1', 'ALTER TABLE km_knowledge_base ADD COLUMN active_name VARCHAR(128) GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN name ELSE NULL END) STORED'
  )
);
PREPARE stmt FROM @sql_active_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_uk_active_name := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_knowledge_base' AND INDEX_NAME = 'uk_kb_active_name') > 0,
    'SELECT 1', 'ALTER TABLE km_knowledge_base ADD UNIQUE KEY uk_kb_active_name (active_name)'
  )
);
PREPARE stmt FROM @sql_uk_active_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 4. 索引补充
-- ============================================================

SET @sql_idx_cat := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_knowledge_base' AND INDEX_NAME = 'idx_kb_category_deleted') > 0,
    'SELECT 1', 'ALTER TABLE km_knowledge_base ADD KEY idx_kb_category_deleted (category, is_deleted)'
  )
);
PREPARE stmt FROM @sql_idx_cat; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql_idx_created := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'km_knowledge_base' AND INDEX_NAME = 'idx_kb_created_at') > 0,
    'SELECT 1', 'ALTER TABLE km_knowledge_base ADD KEY idx_kb_created_at (created_at)'
  )
);
PREPARE stmt FROM @sql_idx_created; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 5. 业务约束（F2 v1.0 6.3 节）
--    由应用层校验，不在 DDL 加 CHECK（MySQL 8.0 才支持且不推荐）
--    - category IN ('REGULATION','REPORT_PAPER','TERM','GENERAL')
--    - retrieval_strategy IN ('SEMANTIC','VECTOR_RERANK')
--    - chunk_strategy IN ('HEADING','FIXED')
--    - FIXED 时 chunk_overlap < chunk_size
-- ============================================================
