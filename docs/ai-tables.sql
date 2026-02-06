-- AI Assistant & RAG System Database Tables
-- 智能助手与RAG知识问答系统数据库表

USE mallchat;

-- ----------------------------
-- Table structure for ai_knowledge_document
-- 知识文档表
-- ----------------------------
DROP TABLE IF EXISTS `ai_knowledge_document`;
CREATE TABLE `ai_knowledge_document` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档标题',
  `document_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档类型（txt, pdf, md, html, docx）',
  `file_size` bigint(20) NOT NULL COMMENT '文档大小（字节）',
  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文档存储路径（OSS）',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文档内容（小文件直接存储）',
  `index_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '索引状态（PENDING, INDEXING, COMPLETED, FAILED）',
  `chunk_count` int(11) NULL DEFAULT 0 COMMENT '分块数量',
  `upload_user_id` bigint(20) NOT NULL COMMENT '上传用户ID',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '错误信息（索引失败时）',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_upload_user_id`(`upload_user_id`) USING BTREE,
  INDEX `idx_index_status`(`index_status`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE,
  INDEX `idx_update_time`(`update_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI知识文档表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_document_chunk
-- 文档分块表
-- ----------------------------
DROP TABLE IF EXISTS `ai_document_chunk`;
CREATE TABLE `ai_document_chunk` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分块ID',
  `document_id` bigint(20) NOT NULL COMMENT '所属文档ID',
  `chunk_index` int(11) NOT NULL COMMENT '分块序号',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分块内容',
  `token_count` int(11) NULL DEFAULT NULL COMMENT '分块token数量',
  `vector_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '向量ID（向量数据库中的ID）',
  `metadata` json NULL DEFAULT NULL COMMENT '元数据（JSON格式，包含来源位置等信息）',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_document_id`(`document_id`) USING BTREE,
  INDEX `idx_vector_id`(`vector_id`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI文档分块表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_conversation
-- AI对话历史表
-- ----------------------------
DROP TABLE IF EXISTS `ai_conversation`;
CREATE TABLE `ai_conversation` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '对话ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `conversation_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话类型（SUMMARY, QA, RAG）',
  `user_input` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户输入',
  `ai_response` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'AI回复',
  `document_id` bigint(20) NULL DEFAULT NULL COMMENT '关联文档ID（RAG场景）',
  `retrieved_chunk_ids` json NULL DEFAULT NULL COMMENT '检索到的分块ID列表（JSON数组）',
  `response_time` bigint(20) NULL DEFAULT NULL COMMENT '响应耗时（毫秒）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SUCCESS' COMMENT '状态（SUCCESS, FAILED, CANCELLED）',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '错误信息',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_conversation_type`(`conversation_type`) USING BTREE,
  INDEX `idx_document_id`(`document_id`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI对话历史表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Add indexes for performance optimization
-- ----------------------------
ALTER TABLE `ai_document_chunk` ADD INDEX `idx_document_chunk`(`document_id`, `chunk_index`) USING BTREE;
ALTER TABLE `ai_conversation` ADD INDEX `idx_user_conversation`(`user_id`, `create_time`) USING BTREE;
