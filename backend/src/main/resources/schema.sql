-- ============================================================
-- MokaDoc 知识图谱模块 — MySQL 管理表 DDL（Spring Boot 自动执行）
-- 与 sql/table.sql 保持同步
-- ============================================================

-- 图谱构建任务表
CREATE TABLE IF NOT EXISTS graph_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|PROCESSING|COMPLETED|FAILED',
    document_ids TEXT COMMENT '关联文档ID列表(JSON数组字符串)',
    progress INT DEFAULT 0 COMMENT '进度百分比 0~100',
    total_batches INT DEFAULT 0 COMMENT '批次总数',
    completed_batches INT DEFAULT 0 COMMENT '已完成批次数',
    error_message VARCHAR(500) COMMENT '错误信息(失败时记录)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    delete_flag TINYINT DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    INDEX idx_user_id (user_id),
    INDEX idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图谱构建任务表';

-- 图谱实体节点表（替代 Neo4j Entity 节点）
CREATE TABLE IF NOT EXISTS graph_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    entity_id VARCHAR(100) NOT NULL COMMENT '业务唯一标识',
    name VARCHAR(500) NOT NULL COMMENT '实体名称',
    type VARCHAR(50) NOT NULL DEFAULT 'Concept' COMMENT '实体类型（Concept/Person/Organization/Project等）',
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    source_doc_ids TEXT COMMENT '来源文档ID列表(逗号分隔)',
    properties TEXT COMMENT '扩展属性JSON',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    delete_flag TINYINT DEFAULT 0 COMMENT '0未删 1已删',
    UNIQUE KEY uk_entity_user (entity_id, user_id),
    INDEX idx_user_name (user_id, name),
    INDEX idx_user_type (user_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图谱实体节点表';

-- 图谱关系边表（替代 Neo4j RELATION 边）
CREATE TABLE IF NOT EXISTS graph_edge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    from_entity_id VARCHAR(100) NOT NULL COMMENT '起始实体业务ID',
    to_entity_id VARCHAR(100) NOT NULL COMMENT '目标实体业务ID',
    rel_type VARCHAR(50) NOT NULL DEFAULT 'RELATED_TO' COMMENT '关系类型（INCLUDES/TEACHES/RELATED_TO等）',
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    source_doc_id VARCHAR(50) COMMENT '来源文档ID',
    properties TEXT COMMENT '扩展属性JSON',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    delete_flag TINYINT DEFAULT 0,
    INDEX idx_user_from (user_id, from_entity_id),
    INDEX idx_user_to (user_id, to_entity_id),
    INDEX idx_user_type (user_id, rel_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图谱关系边表';
