package com.kanade.backend.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 图谱实体节点表（MySQL 替代 Neo4j 节点）。
 *
 * @author kanade
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("graph_node")
public class GraphNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 业务唯一标识（跨批次对齐） */
    private String entityId;

    /** 实体名称 */
    private String name;

    /** 实体类型（Concept/Person/Organization/Project 等） */
    private String type;

    /** 所属用户 ID */
    private Long userId;

    /** 来源文档 ID 列表（逗号分隔） */
    private String sourceDocIds;

    /** 扩展属性 JSON */
    private String properties;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleteFlag;
}
