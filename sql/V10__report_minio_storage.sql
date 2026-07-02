-- =============================================================================
-- V10: 报告模块文件存储切换到 MinIO 所需元数据
-- =============================================================================
-- 新增 object storage 元数据列，保留 file_url/file_path 用于兼容旧前端和历史数据。
-- =============================================================================

SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'report_material' AND COLUMN_NAME = 'bucket') > 0,
  'SELECT 1',
  'ALTER TABLE report_material ADD COLUMN bucket VARCHAR(128) NULL COMMENT ''对象存储 Bucket'' AFTER file_path'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'report_material' AND COLUMN_NAME = 'object_key') > 0,
  'SELECT 1',
  'ALTER TABLE report_material ADD COLUMN object_key VARCHAR(512) NULL COMMENT ''对象存储 Key'' AFTER bucket'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'report_export_task' AND COLUMN_NAME = 'bucket') > 0,
  'SELECT 1',
  'ALTER TABLE report_export_task ADD COLUMN bucket VARCHAR(128) NULL COMMENT ''对象存储 Bucket'' AFTER file_url'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'report_export_task' AND COLUMN_NAME = 'object_key') > 0,
  'SELECT 1',
  'ALTER TABLE report_export_task ADD COLUMN object_key VARCHAR(512) NULL COMMENT ''对象存储 Key'' AFTER bucket'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
