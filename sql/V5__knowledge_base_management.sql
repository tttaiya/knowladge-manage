-- V5 知识库管理加法迁移（commit #27）
-- 设计依据：F2 整合实施步骤 v1.0 文档第 5 章
-- 原则：加法迁移，保留现有数据
--
-- 真实表结构基线（commit #27 前查 km_knowledge_base）：
--   id BIGINT PK
--   name VARCHAR(128) NOT NULL
--   retrieval_strategy VARCHAR(64) NOT NULL DEFAULT 'VECTOR_RERANK'
--   is_deleted TINYINT NOT NULL DEFAULT 0
--   created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
--   updated_at DATETIME DEFAULT NULL
--   KEY idx_kb_deleted (is_deleted)
--   -- 无 UNIQUE KEY uk_kb_name（V5 不需要 DROP INDEX）

-- ============================================================
-- 1. 新增 F2 字段（按 F2 v1.0 文档 5.1 节）
-- ============================================================

ALTER TABLE km_knowledge_base
  ADD COLUMN IF NOT EXISTS description VARCHAR(500) NULL AFTER name,
  ADD COLUMN IF NOT EXISTS category VARCHAR(32) NOT NULL DEFAULT 'GENERAL' AFTER description,
  ADD COLUMN IF NOT EXISTS chunk_strategy VARCHAR(32) NOT NULL DEFAULT 'HEADING' AFTER retrieval_strategy,
  ADD COLUMN IF NOT EXISTS chunk_size INT NOT NULL DEFAULT 500 AFTER chunk_strategy,
  ADD COLUMN IF NOT EXISTS chunk_overlap INT NOT NULL DEFAULT 50 AFTER chunk_size,
  ADD COLUMN IF NOT EXISTS separators_json JSON NULL AFTER chunk_overlap,
  ADD COLUMN IF NOT EXISTS document_count INT NOT NULL DEFAULT 0 AFTER separators_json,
  ADD COLUMN IF NOT EXISTS created_by_user_id VARCHAR(36) NULL AFTER document_count,
  ADD COLUMN IF NOT EXISTS created_by_name VARCHAR(64) NULL AFTER created_by_user_id,
  ADD COLUMN IF NOT EXISTS strategy_version BIGINT NOT NULL DEFAULT 1 AFTER created_by_name,
  ADD COLUMN IF NOT EXISTS deleted_at DATETIME NULL AFTER is_deleted;

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

ALTER TABLE km_knowledge_base
  ADD COLUMN IF NOT EXISTS active_name VARCHAR(128)
    GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN name ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_kb_active_name (active_name);

-- ============================================================
-- 4. 索引补充
-- ============================================================

ALTER TABLE km_knowledge_base
  ADD KEY IF NOT EXISTS idx_kb_category_deleted (category, is_deleted),
  ADD KEY IF NOT EXISTS idx_kb_created_at (created_at);

-- ============================================================
-- 5. 业务约束（F2 v1.0 6.3 节）
--    由应用层校验，不在 DDL 加 CHECK（MySQL 8.0 才支持且不推荐）
--    - category IN ('REGULATION','REPORT_PAPER','TERM','GENERAL')
--    - retrieval_strategy IN ('SEMANTIC','VECTOR_RERANK')
--    - chunk_strategy IN ('HEADING','FIXED')
--    - FIXED 时 chunk_overlap < chunk_size
-- ============================================================