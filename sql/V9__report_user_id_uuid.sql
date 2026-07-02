-- =============================================================================
-- V9: 报告模块用户 ID 统一为 VARCHAR(36) UUID 字符串
-- =============================================================================
-- 与 Gateway 注入的 X-User-Id 对齐。旧数字值会由 MySQL 原地转为字符串。
-- 本迁移不删除数据、不重建表、不切换数据库。
-- =============================================================================

ALTER TABLE report_record
  MODIFY COLUMN user_id VARCHAR(36) NULL
    COMMENT '创建用户 ID（UUID 字符串，与统一登录 X-User-Id 对齐）';

ALTER TABLE report_template
  MODIFY COLUMN creator_id VARCHAR(36) NULL
    COMMENT '创建人 ID（UUID 字符串；系统模板可为 system）';

ALTER TABLE report_export_task
  MODIFY COLUMN creator_id VARCHAR(36) NULL
    COMMENT '创建人 ID（UUID 字符串）';

ALTER TABLE report_material
  MODIFY COLUMN creator_id VARCHAR(36) NULL
    COMMENT '创建人 ID（UUID 字符串）';
