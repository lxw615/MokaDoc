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
 * 图谱关系边表（MySQL 替代 Neo4j 关系）。
 *
 * @author kanade
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("graph_edge")
public class GraphEdge implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 起始实体业务 ID */
    private String fromEntityId;

    /** 目标实体业务 ID */
    private String toEntityId;

    /** 关系类型（INCLUDES/TEACHES/RELATED_TO 等） */
    private String relType;

    /** 所属用户 ID */
    private Long userId;

    /** 来源文档 ID */
    private String sourceDocId;

    /** 扩展属性 JSON */
    private String properties;

    private LocalDateTime createTime;
    private Integer deleteFlag;
}
