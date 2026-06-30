DROP TABLE IF EXISTS `km_knowledge_base`;

CREATE TABLE `km_knowledge_base` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '??',
  `name` VARCHAR(64) NOT NULL COMMENT '?????',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '?????',
  `category` VARCHAR(32) NOT NULL COMMENT '??',
  `retrieval_strategy` VARCHAR(32) NOT NULL COMMENT '????',
  `chunk_strategy` VARCHAR(32) NOT NULL COMMENT '????',
  `chunk_size` INT(11) NOT NULL DEFAULT 500 COMMENT '????',
  `chunk_overlap` INT(11) NOT NULL DEFAULT 50 COMMENT '????',
  `separators_json` JSON DEFAULT NULL COMMENT '?????',
  `document_count` INT(11) NOT NULL DEFAULT 0 COMMENT '???',
  `created_by_user_id` BIGINT(20) DEFAULT NULL COMMENT '???ID',
  `created_by_name` VARCHAR(64) DEFAULT NULL COMMENT '?????',
  `is_deleted` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '??????',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_name` (`name`),
  KEY `idx_category` (`category`),
  KEY `idx_is_deleted` (`is_deleted`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='????';
