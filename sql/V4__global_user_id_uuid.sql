-- =============================================================================
-- V4: 统一用户 ID 为 VARCHAR(36) UUID 字符串
-- =============================================================================
-- 背景：
--   智慧问答统一登录（super-biz-agent-service）用户主键是 VARCHAR(36) UUID。
--   当前 km_document / km_document_process_task / km_review_record /
--   km_chunk_edit_log 的 user_id 字段为 BIGINT，无法承载 UUID 字符串。
--
--   Gateway 调 GET /api/auth/me 拿到的 id 是 UUID 字符串，注入 X-User-Id。
--   若 km-admin-service 仍用 Long userId 接收，会在反序列化阶段报错。
--
-- 迁移策略（R14 全文统一）：
--   - 4 张表 user_id / operator_user_id 全部改为 VARCHAR(36) NULL
--   - 保留 COMMENT 说明，方便代码与 SQL 字段对齐
--   - 系统任务（user_id IS NULL）的用户保持 NULL
--
-- 受影响表：
--   1. km_document                  .user_id              BIGINT -> VARCHAR(36)
--   2. km_document_process_task     .user_id              BIGINT -> VARCHAR(36)
--   3. km_review_record             .operator_user_id     BIGINT -> VARCHAR(36)
--   4. km_chunk_edit_log            .operator_user_id     BIGINT -> VARCHAR(36)
-- =============================================================================

ALTER TABLE km_document
  MODIFY COLUMN user_id VARCHAR(36) NULL
    COMMENT '上传人用户 ID（UUID 字符串，与 super-biz-agent 统一登录对齐）';

ALTER TABLE km_document_process_task
  MODIFY COLUMN user_id VARCHAR(36) NULL
    COMMENT '任务创建者用户 ID（UUID；系统任务保持 NULL）';

ALTER TABLE km_review_record
  MODIFY COLUMN operator_user_id VARCHAR(36) NULL
    COMMENT '审核人用户 ID（UUID）';

ALTER TABLE km_chunk_edit_log
  MODIFY COLUMN operator_user_id VARCHAR(36) NULL
    COMMENT '切片编辑人用户 ID（UUID）';

-- =============================================================================
-- 验证清单
-- =============================================================================
-- SHOW COLUMNS FROM km_document               WHERE Field = 'user_id';
-- SHOW COLUMNS FROM km_document_process_task  WHERE Field = 'user_id';
-- SHOW COLUMNS FROM km_review_record          WHERE Field = 'operator_user_id';
-- SHOW COLUMNS FROM km_chunk_edit_log         WHERE Field = 'operator_user_id';
-- 四个字段 Type 应均为 varchar(36)，Null = YES，Default = NULL
--
-- 若已有联调数据存了数字 ID（如 user_id = 1），保留为字符串 '1' 即可
-- 不会破坏现有查询（MySQL 字符串 '1' 仍可与其它字符串比较）
-- =============================================================================
