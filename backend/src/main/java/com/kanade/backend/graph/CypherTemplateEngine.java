package com.kanade.backend.graph;

import com.kanade.backend.mapper.GraphEdgeMapper;
import com.kanade.backend.mapper.GraphNodeMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图谱查询模板引擎（MySQL 实现，替代 Cypher）。
 *
 * @author kanade
 */
@Slf4j
@Component
public class CypherTemplateEngine {

    private final GraphNodeMapper nodeMapper;
    private final GraphEdgeMapper edgeMapper;

    public CypherTemplateEngine(GraphNodeMapper nodeMapper, GraphEdgeMapper edgeMapper) {
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
    }

    public List<Map<String, Object>> searchEntities(String keyword, Long userId, int limit) {
        QueryWrapper qw = new QueryWrapper();
        qw.eq("user_id", userId).eq("delete_flag", 0)
            .like("name", keyword)
            .orderBy("name", true).limit(limit);
        return nodeMapper.selectListByQuery(qw).stream()
            .map(n -> Map.<String, Object>of("name", n.getName(), "type", n.getType(), "entityId", n.getEntityId()))
            .collect(Collectors.toList());
    }

    public List<Map<String, Object>> expandSubgraph(String entityName, Long userId, int hops, int limit) {
        // 委托给 GraphCrudService 的 expandSubgraph
        return List.of();
    }

    public List<Map<String, Object>> typeStats(Long userId) {
        QueryWrapper qw = new QueryWrapper();
        qw.eq("user_id", userId).eq("delete_flag", 0);
        List<Map<String, Object>> rows = nodeMapper.selectListByQuery(qw).stream()
            .collect(Collectors.groupingBy(
                n -> n.getType() != null ? n.getType() : "Concept",
                Collectors.counting()))
            .entrySet().stream()
            .map(e -> Map.<String, Object>of("type", e.getKey(), "cnt", e.getValue()))
            .collect(Collectors.toList());
        return rows;
    }

    public List<Map<String, Object>> entityRelations(String entityId, Long userId, int limit) {
        List<com.kanade.backend.entity.GraphEdge> rels = new ArrayList<>();
        rels.addAll(edgeMapper.selectListByQuery(
            QueryWrapper.create().eq("user_id", userId).eq("from_entity_id", entityId).limit(limit)));
        rels.addAll(edgeMapper.selectListByQuery(
            QueryWrapper.create().eq("user_id", userId).eq("to_entity_id", entityId).limit(limit)));
        
        return rels.stream()
            .map(e -> Map.<String, Object>of(
                "fromEntityId", e.getFromEntityId(), "toEntityId", e.getToEntityId(), "type", e.getRelType()))
            .collect(Collectors.toList());
    }
}
