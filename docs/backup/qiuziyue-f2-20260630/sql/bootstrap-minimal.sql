CREATE DATABASE IF NOT EXISTS knowledge_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE knowledge_management;

DROP TABLE IF EXISTS `km_document`;

CREATE TABLE `km_document` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `kb_id` BIGINT(20) NOT NULL COMMENT '所属知识库ID',
  `name` VARCHAR(255) NOT NULL COMMENT '文档名称',
  `status` VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' COMMENT '文档状态',
  `is_deleted` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_kb_id` (`kb_id`),
  KEY `idx_status` (`status`),
  KEY `idx_is_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档最小兼容表';
