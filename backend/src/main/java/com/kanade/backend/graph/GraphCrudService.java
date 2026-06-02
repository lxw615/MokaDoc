package com.kanade.backend.graph;

import com.kanade.backend.entity.GraphEdge;
import com.kanade.backend.entity.GraphNode;
import com.kanade.backend.graph.model.GraphEntity;
import com.kanade.backend.graph.model.GraphRelation;
import com.kanade.backend.mapper.GraphEdgeMapper;
import com.kanade.backend.mapper.GraphNodeMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 图谱 CRUD 服务（MySQL 实现，替代 Neo4j）。
 * 所有操作基于 MyBatis-Flex，强制携带 userId 实现多租户隔离。
 *
 * @author kanade
 */
@Slf4j
@Service
public class GraphCrudService {

    private final GraphNodeMapper nodeMapper;
    private final GraphEdgeMapper edgeMapper;

    public GraphCrudService(GraphNodeMapper nodeMapper, GraphEdgeMapper edgeMapper) {
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
    }

    // ==================== 节点操作 ====================

    public GraphEntity mergeEntity(GraphEntity entity) {
        QueryWrapper qw = new QueryWrapper();
        qw.eq("entity_id", entity.getEntityId()).eq("user_id", entity.getUserId());
        GraphNode existing = nodeMapper.selectOneByQuery(qw);

        if (existing != null) {
            existing.setName(entity.getName());
            existing.setType(entity.getType());
            existing.setSourceDocIds(mergeDocIds(existing.getSourceDocIds(), entity.getSourceDocIds()));
            existing.setUpdateTime(LocalDateTime.now());
            nodeMapper.update(existing);
        } else {
            GraphNode node = toNode(entity);
            nodeMapper.insert(node);
        }
        return entity;
    }

    public List<GraphEntity> mergeEntitiesBatch(List<GraphEntity> entities) {
        if (entities == null || entities.isEmpty()) return Collections.emptyList();

        for (GraphEntity entity : entities) {
            mergeEntity(entity);
        }
        log.info("✅ [图谱] 批量合并实体完成: count={}", entities.size());
        return entities;
    }

    public Optional<GraphEntity> findEntity(String entityId, Long userId) {
        QueryWrapper qw = new QueryWrapper();
        qw.eq("entity_id", entityId).eq("user_id", userId).eq("delete_flag", 0);
        GraphNode node = nodeMapper.selectOneByQuery(qw);
        return Optional.ofNullable(node).map(this::toEntity);
    }

    public List<GraphEntity> searchEntities(String keyword, Long userId, int limit) {
        QueryWrapper qw = new QueryWrapper();
        qw.eq("user_id", userId).eq("delete_flag", 0);
        if (keyword != null && !keyword.isEmpty()) {
            qw.like("name", keyword);
        }
        qw.orderBy("name", true).limit(limit);
        return nodeMapper.selectListByQuery(qw).stream()
            .map(this::toEntity)
            .collect(Collectors.toList());
    }

    // ==================== 关系操作 ====================

    public GraphRelation createRelation(GraphRelation relation) {
        QueryWrapper qw = new QueryWrapper();
        qw.eq("from_entity_id", relation.getFromEntityId())
            .eq("to_entity_id", relation.getToEntityId())
            .eq("rel_type", relation.getType())
            .eq("user_id", relation.getUserId())
            .eq("delete_flag", 0);
        if (edgeMapper.selectCountByQuery(qw) == 0) {
            GraphEdge edge = toEdge(relation);
            edgeMapper.insert(edge);
        }
        return relation;
    }

    public List<GraphRelation> createRelationsBatch(List<GraphRelation> relations) {
        if (relations == null || relations.isEmpty()) return Collections.emptyList();
        for (GraphRelation r : relations) {
            createRelation(r);
        }
        log.info("✅ [图谱] 批量创建关系完成: count={}", relations.size());
        return relations;
    }

    // ==================== 子图查询 ====================

    public Map<String, Object> expandSubgraph(String entityName, Long userId, int maxHops) {
        Map<String, Object> result = new LinkedHashMap<>();
        Set<Map<String, Object>> nodes = new LinkedHashSet<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        // 查找种子实体
        QueryWrapper qw = new QueryWrapper();
        qw.eq("user_id", userId).eq("delete_flag", 0).like("name", entityName);
        List<GraphNode> seeds = nodeMapper.selectListByQuery(qw);
        if (seeds.isEmpty()) return result;

        Set<String> entityIds = new LinkedHashSet<>();
        for (GraphNode seed : seeds) {
            entityIds.add(seed.getEntityId());
            nodes.add(Map.of("name", seed.getName(), "type", seed.getType(), "entityId", seed.getEntityId()));
        }

        // 1 跳扩展
        for (String eid : entityIds) {
            // 查询所有与 eid 相关的关系（from 或 to）
            List<GraphEdge> rels = new ArrayList<>();
            rels.addAll(edgeMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId).eq("delete_flag", 0).eq("from_entity_id", eid)));
            rels.addAll(edgeMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId).eq("delete_flag", 0).eq("to_entity_id", eid)));
            for (GraphEdge rel : rels) {
                String otherId = rel.getFromEntityId().equals(eid) ? rel.getToEntityId() : rel.getFromEntityId();
                QueryWrapper nqw = new QueryWrapper();
                nqw.eq("entity_id", otherId).eq("user_id", userId).eq("delete_flag", 0);
                GraphNode other = nodeMapper.selectOneByQuery(nqw);
                if (other != null) {
                    nodes.add(Map.of("name", other.getName(), "type", other.getType(), "entityId", other.getEntityId()));
                }
                edges.add(Map.of(
                    "fromEntityId", rel.getFromEntityId(),
                    "toEntityId", rel.getToEntityId(),
                    "type", rel.getRelType()));
            }
        }

        result.put("nodes", new ArrayList<>(nodes));
        result.put("edges", edges);
        return result;
    }

    public Map<String, Object> fullGraph(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 300));
        QueryWrapper nodeQuery = QueryWrapper.create()
                .eq("user_id", userId)
                .eq("delete_flag", 0)
                .orderBy("update_time", false)
                .limit(safeLimit);
        List<GraphNode> graphNodes = nodeMapper.selectListByQuery(nodeQuery);
        Set<String> nodeIds = graphNodes.stream()
                .map(GraphNode::getEntityId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Map<String, Object>> nodes = graphNodes.stream()
                .map(node -> Map.<String, Object>of(
                        "name", node.getName(),
                        "type", node.getType(),
                        "entityId", node.getEntityId()))
                .toList();

        List<Map<String, Object>> edges = edgeMapper.selectListByQuery(
                        QueryWrapper.create()
                                .eq("user_id", userId)
                                .eq("delete_flag", 0)
                                .limit(safeLimit * 2))
                .stream()
                .filter(edge -> nodeIds.contains(edge.getFromEntityId()) && nodeIds.contains(edge.getToEntityId()))
                .map(edge -> Map.<String, Object>of(
                        "fromEntityId", edge.getFromEntityId(),
                        "toEntityId", edge.getToEntityId(),
                        "type", edge.getRelType()))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    // ==================== 删除 ====================

    public int deleteAllByUser(Long userId) {
        QueryWrapper ew = new QueryWrapper();
        ew.eq("user_id", userId).eq("delete_flag", 0);
        long relCount = edgeMapper.selectCountByQuery(ew);
        // 物理删除关系
        edgeMapper.deleteByQuery(ew);

        QueryWrapper nw = new QueryWrapper();
        nw.eq("user_id", userId);
        long nodeCount = nodeMapper.selectCountByQuery(nw);
        nodeMapper.deleteByQuery(nw);

        log.warn("🗑️ [图谱删除] userId={}, 节点={}, 关系={}", userId, nodeCount, relCount);
        return (int) (nodeCount + relCount);
    }

    public Map<String, Long> stats(Long userId) {
        QueryWrapper nw = new QueryWrapper();
        nw.eq("user_id", userId).eq("delete_flag", 0);
        long nodeCount = nodeMapper.selectCountByQuery(nw);

        QueryWrapper ew = new QueryWrapper();
        ew.eq("user_id", userId);
        long relCount = edgeMapper.selectCountByQuery(ew);

        return Map.of("nodeCount", nodeCount, "relCount", relCount);
    }

    // ==================== 工具方法 ====================

    private GraphNode toNode(GraphEntity e) {
        return GraphNode.builder()
            .entityId(e.getEntityId())
            .name(e.getName())
            .type(e.getType() != null ? e.getType() : "Concept")
            .userId(e.getUserId())
            .sourceDocIds(e.getSourceDocIds())
            .createTime(LocalDateTime.now())
            .updateTime(LocalDateTime.now())
            .deleteFlag(0)
            .build();
    }

    private GraphEntity toEntity(GraphNode n) {
        return GraphEntity.builder()
            .entityId(n.getEntityId())
            .name(n.getName())
            .type(n.getType())
            .userId(n.getUserId())
            .sourceDocIds(n.getSourceDocIds())
            .build();
    }

    private GraphEdge toEdge(GraphRelation r) {
        return GraphEdge.builder()
            .fromEntityId(r.getFromEntityId())
            .toEntityId(r.getToEntityId())
            .relType(r.getType())
            .userId(r.getUserId())
            .sourceDocId(r.getSourceDocId())
            .createTime(LocalDateTime.now())
            .deleteFlag(0)
            .build();
    }

    private String mergeDocIds(String existing, String incoming) {
        Set<String> ids = new LinkedHashSet<>();
        if (existing != null && !existing.isEmpty()) ids.addAll(Arrays.asList(existing.split(",")));
        if (incoming != null && !incoming.isEmpty()) ids.addAll(Arrays.asList(incoming.split(",")));
        return String.join(",", ids);
    }
}
