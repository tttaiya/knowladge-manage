-- Report generation module, non-destructive migration
-- Source script was rewritten to remove destructive statements, database switching, and sensitive default values.
SET NAMES utf8mb4;

-- =====================================================
-- 报告服务 km-report-service 初始化脚本
-- MySQL 5.7+
-- 说明：
-- 1. 由当前 Docker MYSQL_DATABASE 决定目标库，不切库、不建库
-- 2. JSON 字段统一采用 LONGTEXT，便于 MyBatis-Plus 直接映射为 String
-- 3. 统一补充 version、remark、create_time、update_time、deleted 等常用字段
-- 4. 逻辑删除字段统一使用 deleted：0-未删除，1-已删除
-- =====================================================




SET NAMES utf8mb4;

-- =====================================================
-- 1. 报告模板主表
-- =====================================================
CREATE TABLE IF NOT EXISTS `report_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_name` VARCHAR(128) NOT NULL COMMENT '模板名称',
  `report_type` VARCHAR(32) NOT NULL COMMENT '报告类型',
  `description` VARCHAR(512) DEFAULT '' COMMENT '模板描述',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-草稿；1-已发布；2-已下架',
  `template_scope` VARCHAR(32) NOT NULL DEFAULT 'GLOBAL' COMMENT '可见范围：GLOBAL-全局；PERSONAL-个人私有',
  `chapter_count` INT NOT NULL DEFAULT 0 COMMENT '章节总数',
  `style_config` LONGTEXT COMMENT '统一样式配置，JSON 字符串',
  `original_file_name` VARCHAR(255) DEFAULT '' COMMENT '原始模板文件名',
  `file_url` VARCHAR(512) DEFAULT '' COMMENT '模板文件访问地址',
  `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '模板文件大小',
  `creator_id` VARCHAR(36) DEFAULT NULL COMMENT '创建人ID（UUID 字符串）',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `remark` VARCHAR(512) DEFAULT '' COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_report_type` (`report_type`),
  KEY `idx_status_scope` (`status`, `template_scope`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告模板主表';

-- =====================================================
-- 2. 模板章节表
-- =====================================================
CREATE TABLE IF NOT EXISTS `report_template_chapter` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_id` BIGINT NOT NULL COMMENT '关联模板ID',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父章节ID，根节点为0',
  `chapter_title` VARCHAR(128) NOT NULL COMMENT '章节标题',
  `chapter_no` VARCHAR(32) DEFAULT '' COMMENT '章节编号',
  `chapter_type` VARCHAR(32) NOT NULL DEFAULT 'TEXT' COMMENT '章节类型：TEXT/TEXT_TABLE/IMAGE/CUSTOM',
  `level` INT NOT NULL DEFAULT 1 COMMENT '层级：1-一级；2-二级；3-三级',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
  `default_content` LONGTEXT COMMENT '默认内容',
  `writing_prompt` LONGTEXT COMMENT 'AI 写作提示词',
  `required_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '是否必填：0-否；1-是',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `remark` VARCHAR(512) DEFAULT '' COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_template_id` (`template_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_template_sort` (`template_id`, `parent_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模板章节表';

-- =====================================================
-- 3. 报告生成记录表
-- =====================================================
CREATE TABLE IF NOT EXISTS `report_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_name` VARCHAR(128) NOT NULL COMMENT '报告名称',
  `report_type` VARCHAR(32) NOT NULL COMMENT '报告类型',
  `template_id` BIGINT NOT NULL DEFAULT 0 COMMENT '使用的模板ID',
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '生成来源：KNOWLEDGE_DOC/KNOWLEDGE_ONLY/DOC_ONLY/MANUAL',
  `major` VARCHAR(64) DEFAULT '' COMMENT '专业领域',
  `power_plant` VARCHAR(64) DEFAULT '' COMMENT '所属电厂',
  `report_year` INT DEFAULT NULL COMMENT '报告年份',
  `generation_prompt` LONGTEXT COMMENT '用户输入或自定义提示词',
  `enable_knowledge_retrieval` TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用知识库检索：0-否；1-是',
  `enable_ocr` TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用 OCR：0-否；1-是',
  `enable_history_ref` TINYINT NOT NULL DEFAULT 0 COMMENT '是否引用历史报告：0-否；1-是',
  `source_ids` LONGTEXT COMMENT '关联资源ID列表',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-生成中；1-已完成；2-生成失败；3-已取消',
  `fail_reason` VARCHAR(512) DEFAULT '' COMMENT '失败原因',
  `total_chapter` INT NOT NULL DEFAULT 0 COMMENT '总章节数',
  `finished_chapter` INT NOT NULL DEFAULT 0 COMMENT '已完成章节数',
  `export_status` TINYINT NOT NULL DEFAULT 0 COMMENT '导出状态：0-未导出；1-导出中；2-成功；3-失败',
  `file_url` VARCHAR(512) DEFAULT '' COMMENT '默认导出文件地址',
  `docx_url` VARCHAR(512) DEFAULT '' COMMENT 'Word 导出地址',
  `pdf_url` VARCHAR(512) DEFAULT '' COMMENT 'PDF 导出地址',
  `user_id` VARCHAR(36) DEFAULT NULL COMMENT '创建用户ID（UUID 字符串）',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `remark` VARCHAR(512) DEFAULT '' COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常；1-回收站',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_report_type` (`report_type`),
  KEY `idx_status` (`status`),
  KEY `idx_report_year` (`report_year`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告生成记录表';

-- =====================================================
-- 4. 报告大纲表
-- =====================================================
CREATE TABLE IF NOT EXISTS `report_outline_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_id` BIGINT NOT NULL COMMENT '关联报告ID',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父节点ID',
  `chapter_title` VARCHAR(128) NOT NULL COMMENT '章节标题',
  `chapter_no` VARCHAR(32) DEFAULT '' COMMENT '章节编号',
  `level` INT NOT NULL DEFAULT 1 COMMENT '层级：1-一级；2-二级；3-三级',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `editable` TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许编辑：0-否；1-是',
  `ai_generated` TINYINT NOT NULL DEFAULT 1 COMMENT '是否由 AI 生成：0-否；1-是',
  `generation_prompt` LONGTEXT COMMENT '生成提示词',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/REGENERATED/CONFIRMED',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `remark` VARCHAR(512) DEFAULT '' COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_report_id` (`report_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_report_sort` (`report_id`, `parent_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告大纲表';

-- =====================================================
-- 5. 报告章节内容表
-- =====================================================
CREATE TABLE IF NOT EXISTS `report_chapter_content` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_id` BIGINT NOT NULL COMMENT '关联报告ID',
  `template_chapter_id` BIGINT DEFAULT NULL COMMENT '关联模板章节ID',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父章节ID',
  `chapter_title` VARCHAR(128) NOT NULL COMMENT '章节标题',
  `chapter_no` VARCHAR(32) NOT NULL COMMENT '章节编号',
  `level` INT NOT NULL DEFAULT 1 COMMENT '层级',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `content_format` VARCHAR(16) NOT NULL DEFAULT 'MARKDOWN' COMMENT '内容格式：MARKDOWN/HTML/TEXT',
  `content` LONGTEXT COMMENT '章节内容',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-生成中；1-已完成；2-生成失败；3-人工编辑中',
  `word_count` INT NOT NULL DEFAULT 0 COMMENT '字数',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `remark` VARCHAR(512) DEFAULT '' COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_report_id` (`report_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_report_sort` (`report_id`, `parent_id`, `sort`),
  KEY `idx_template_chapter_id` (`template_chapter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告章节内容表';

-- =====================================================
-- 6. 系统配置表
-- =====================================================
CREATE TABLE IF NOT EXISTS `report_system_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` VARCHAR(128) NOT NULL COMMENT '配置键',
  `config_value` VARCHAR(1024) DEFAULT '' COMMENT '配置值',
  `config_type` VARCHAR(32) NOT NULL DEFAULT 'STRING' COMMENT '配置类型：STRING/NUMBER/BOOLEAN/JSON',
  `description` VARCHAR(512) DEFAULT '' COMMENT '配置说明',
  `editable` TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许修改：0-否；1-是',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- =====================================================
-- 7. AI 调用日志表
-- =====================================================
CREATE TABLE IF NOT EXISTS `report_ai_call_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_id` BIGINT NOT NULL DEFAULT 0 COMMENT '关联报告ID',
  `chapter_id` BIGINT DEFAULT NULL COMMENT '关联章节ID',
  `call_type` VARCHAR(32) NOT NULL COMMENT '调用类型：OUTLINE/CHAPTER_CONTENT/CHAPTER_REGEN/SUMMARY',
  `model_name` VARCHAR(64) NOT NULL COMMENT '模型名称',
  `request_id` VARCHAR(128) DEFAULT '' COMMENT '请求ID',
  `prompt_tokens` INT NOT NULL DEFAULT 0 COMMENT '输入Token',
  `completion_tokens` INT NOT NULL DEFAULT 0 COMMENT '输出Token',
  `total_tokens` INT NOT NULL DEFAULT 0 COMMENT '总Token',
  `duration_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-调用中；1-成功；2-失败',
  `error_msg` VARCHAR(1024) DEFAULT '' COMMENT '错误信息',
  `request_body` LONGTEXT COMMENT '请求内容',
  `response_body` LONGTEXT COMMENT '响应内容',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `finish_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_report_id` (`report_id`),
  KEY `idx_chapter_id` (`chapter_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 调用日志表';

-- =====================================================
-- 8. 导出任务表
-- =====================================================
CREATE TABLE IF NOT EXISTS `report_export_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_id` BIGINT NOT NULL COMMENT '关联报告ID',
  `export_format` VARCHAR(16) NOT NULL COMMENT '导出格式：DOCX/PDF',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待导出；1-导出中；2-成功；3-失败',
  `file_url` VARCHAR(512) DEFAULT '' COMMENT '导出文件地址',
  `bucket` VARCHAR(128) DEFAULT NULL COMMENT '对象存储 Bucket',
  `object_key` VARCHAR(512) DEFAULT NULL COMMENT '对象存储 Key',
  `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小',
  `fail_reason` VARCHAR(1024) DEFAULT '' COMMENT '失败原因',
  `trigger_type` VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '触发方式：MANUAL/AUTO',
  `creator_id` VARCHAR(36) DEFAULT NULL COMMENT '操作人ID（UUID 字符串）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `finish_time` DATETIME DEFAULT NULL COMMENT '完成时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_report_id` (`report_id`),
  KEY `idx_status` (`status`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导出任务表';


-- =====================================================
-- 9. 报告素材表
-- =====================================================
CREATE TABLE IF NOT EXISTS `report_material` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `material_name` VARCHAR(128) NOT NULL COMMENT '素材名称',
  `material_type` VARCHAR(32) NOT NULL DEFAULT 'DOCUMENT' COMMENT '素材类型：DOCUMENT/TABLE/IMAGE',
  `report_type` VARCHAR(32) DEFAULT '' COMMENT '适用报告类型',
  `major` VARCHAR(64) DEFAULT '' COMMENT '专业领域',
  `power_plant` VARCHAR(64) DEFAULT '' COMMENT '所属电厂',
  `report_year` INT DEFAULT NULL COMMENT '报告年份',
  `original_file_name` VARCHAR(255) DEFAULT '' COMMENT '原始文件名',
  `file_url` VARCHAR(512) DEFAULT '' COMMENT '文件访问地址',
  `file_path` VARCHAR(512) DEFAULT '' COMMENT '文件相对路径',
  `bucket` VARCHAR(128) DEFAULT NULL COMMENT '对象存储 Bucket',
  `object_key` VARCHAR(512) DEFAULT NULL COMMENT '对象存储 Key',
  `file_ext` VARCHAR(16) DEFAULT '' COMMENT '文件扩展名',
  `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小',
  `parse_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '解析状态：PENDING/SUCCESS/FAILED',
  `structured_data` LONGTEXT COMMENT '结构化解析结果，JSON 字符串',
  `creator_id` VARCHAR(36) DEFAULT NULL COMMENT '上传用户ID（UUID 字符串）',
  `remark` VARCHAR(512) DEFAULT '' COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除；1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_material_type` (`material_type`),
  KEY `idx_report_type` (`report_type`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告素材表';
-- =====================================================
-- 初始化默认系统配置
-- =====================================================

-- Knowledge references for traceable generated report content.
CREATE TABLE IF NOT EXISTS `report_knowledge_reference` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_id` BIGINT NOT NULL COMMENT '报告ID',
  `chapter_id` BIGINT DEFAULT NULL COMMENT '章节ID',
  `knowledge_base_id` BIGINT DEFAULT NULL COMMENT '知识库ID',
  `document_id` BIGINT DEFAULT NULL COMMENT '文档ID',
  `chunk_id` BIGINT DEFAULT NULL COMMENT '切片ID',
  `vector_id` VARCHAR(128) DEFAULT '' COMMENT '向量ID',
  `retrieval_score` DECIMAL(10,6) DEFAULT NULL COMMENT '检索得分',
  `source_title` VARCHAR(255) DEFAULT '' COMMENT '来源标题',
  `excerpt_snapshot` LONGTEXT COMMENT '片段快照',
  `source_order` INT NOT NULL DEFAULT 0 COMMENT '引用顺序',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_report_id` (`report_id`),
  KEY `idx_chapter_id` (`chapter_id`),
  KEY `idx_document_chunk` (`document_id`, `chunk_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告知识来源引用表';

-- Non-sensitive defaults. API keys must be provided by environment/Nacos or saved explicitly by an authorized admin.
INSERT INTO `report_system_config` (`config_key`, `config_value`, `config_type`, `description`, `editable`) VALUES
('report.llm.enabled', '0', 'BOOLEAN', '报告 LLM 总开关', 1),
('report.llm.base-url', '', 'STRING', '报告 LLM 基础地址', 1),
('report.llm.model', '', 'STRING', '报告 LLM 模型名称', 1),
('report.llm.timeout-seconds', '60', 'NUMBER', '报告 LLM 调用超时时间（秒）', 1),
('report.llm.max-input-chars', '12000', 'NUMBER', '报告 LLM 最大输入字符数', 1),
('report.llm.max-output-tokens', '2048', 'NUMBER', '报告 LLM 最大输出 token 数', 1),
('export.default.font_name', '宋体', 'STRING', '导出文档默认正文字体', 1),
('export.default.font_size', '12', 'NUMBER', '导出文档默认正文字号', 1),
('feature.knowledge_retrieval.enable', '1', 'BOOLEAN', '是否启用知识库检索', 1)
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`), `description` = VALUES(`description`), `editable` = VALUES(`editable`);
