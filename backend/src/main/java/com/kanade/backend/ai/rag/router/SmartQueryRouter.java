package com.kanade.backend.ai.rag.router;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能查询路由器（合并版）。
 *
 * 双模式路由：
 * 1. target 查表（合作者新增）：读取 MultiStrategyQueryTransformer 写入的 queryTargetMap，
 *    将 vector/text 查询精准路由到对应检索器
 * 2. LLM 意图分析（我们原有）：当 target 为空时，用 LLM 分析意图
 *    （SEMANTIC/KEYWORD/RELATION/COMPLEX/GRAPH/HYBRID）
 *
 * 图谱降级：GRAPH/HYBRID 意图在图谱不可用时自动降级到 vector+text。
 *
 * @author kanade
 */
@Slf4j
public class SmartQueryRouter implements QueryRouter {

    private final ChatModel chatModel;
    private final Map<String, ContentRetriever> retrievers;
    private final Map<String, String> queryTargetMap;

    /**
     * 图谱检索器是否可用。
     */
    private boolean graphAvailable = false;

    public SmartQueryRouter(ChatModel chatModel,
                            Map<String, ContentRetriever> retrievers,
                            Map<String, String> queryTargetMap) {
        this.chatModel = chatModel;
        this.retrievers = retrievers;
        this.queryTargetMap = queryTargetMap;
        this.graphAvailable = retrievers.containsKey("graph");
    }

    @Override
    public Collection<ContentRetriever> route(Query query) {
        try {
            // 模式 1：target 查表（合作者的多策略路由）
            String target = queryTargetMap.get(query.text());
            if (target != null) {
                return routeByTarget(query, target);
            }

            // 模式 2：LLM 意图分析（我们的原有路由）
            return routeByIntent(query);

        } catch (Exception e) {
            log.error("❌ [路由失败] 降级使用全部检索器", e);
            return new ArrayList<>(retrievers.values());
        }
    }

    /**
     * 模式 1：按 target 查表路由（合作者新增）。
     */
    private Collection<ContentRetriever> routeByTarget(Query query, String target) {
        return switch (target) {
            case "vector" -> {
                if (retrievers.containsKey("vector")) {
                    log.info("🎯 [路由·target] query='{}' → vector", query.text());
                    yield List.of(retrievers.get("vector"));
                }
                yield fallback(query);
            }
            case "text" -> {
                if (retrievers.containsKey("text")) {
                    log.info("🎯 [路由·target] query='{}' → text", query.text());
                    yield List.of(retrievers.get("text"));
                }
                yield fallback(query);
            }
            case "graph" -> {
                if (graphAvailable) {
                    log.info("🎯 [路由·target] query='{}' → graph", query.text());
                    yield List.of(retrievers.get("graph"));
                }
                yield fallback(query);
            }
            case "hybrid" -> {
                List<ContentRetriever> selected = new ArrayList<>();
                if (graphAvailable) selected.add(retrievers.get("graph"));
                if (retrievers.containsKey("vector")) selected.add(retrievers.get("vector"));
                if (retrievers.containsKey("text")) selected.add(retrievers.get("text"));
                log.info("🎯 [路由·target] query='{}' → hybrid({})", query.text(), selected.size());
                yield selected.isEmpty() ? fallback(query) : selected;
            }
            default -> fallback(query);
        };
    }

    /**
     * 模式 2：LLM 意图分析路由（我们原有）。
     */
    private Collection<ContentRetriever> routeByIntent(Query query) {
        String intent = analyzeIntent(query.text());
        log.info("🎯 [路由·意图] query='{}', intent={}", query.text(), intent);

        Collection<ContentRetriever> selected = selectRetrievers(intent);

        log.info("🎯 [路由决策] 选择了 {} 个检索器: {}",
            selected.size(),
            selected.stream()
                .map(r -> r.getClass().getSimpleName())
                .collect(Collectors.joining(", ")));

        return selected;
    }

    private String analyzeIntent(String query) {
        String prompt = String.format("""
            请分析以下用户查询的意图类型，从以下选项中选择最匹配的一个：

            选项：
            - SEMANTIC: 语义相似性查询（如"什么是XXX"、"XXX的特点"）
            - KEYWORD: 关键词精确匹配（如"XXX的定义"、"XXX的版本号"）
            - RELATION: 实体关系查询（如"XXX和YYY的关系"、"XXX的影响"）
            - GRAPH: 需要知识图谱回答的实体关联问题（如"XXX参与了哪些项目"）
            - HYBRID: 需要同时结合文档内容和实体关系的问题
            - COMPLEX: 复杂综合查询

            用户查询：%s

            意图类型（只输出选项名称）：""", query);

        String intent = chatModel.chat(prompt).trim().toUpperCase();

        if (!Arrays.asList("SEMANTIC", "KEYWORD", "RELATION", "COMPLEX", "GRAPH", "HYBRID")
                .contains(intent)) {
            log.warn("⚠️ [意图识别] 未知意图: {}，默认为 SEMANTIC", intent);
            return "SEMANTIC";
        }

        return intent;
    }

    private Collection<ContentRetriever> selectRetrievers(String intent) {
        List<ContentRetriever> selected = new ArrayList<>();

        switch (intent) {
            case "SEMANTIC" -> {
                if (retrievers.containsKey("vector")) selected.add(retrievers.get("vector"));
            }
            case "KEYWORD" -> {
                if (retrievers.containsKey("text")) selected.add(retrievers.get("text"));
            }
            case "GRAPH" -> {
                if (graphAvailable) {
                    selected.add(retrievers.get("graph"));
                } else if (retrievers.containsKey("vector")) {
                    log.warn("⚠️ [图谱不可用] 降级到 vector 检索");
                    selected.add(retrievers.get("vector"));
                }
            }
            case "HYBRID" -> {
                if (graphAvailable) selected.add(retrievers.get("graph"));
                if (retrievers.containsKey("vector")) selected.add(retrievers.get("vector"));
                if (retrievers.containsKey("text")) selected.add(retrievers.get("text"));
            }
            case "RELATION", "COMPLEX" -> {
                if (graphAvailable) selected.add(retrievers.get("graph"));
                selected.addAll(retrievers.values().stream()
                    .filter(r -> !"graph".equals(getRetrieverKey(r)))
                    .collect(Collectors.toList()));
            }
        }

        if (selected.isEmpty() && retrievers.containsKey("vector")) {
            selected.add(retrievers.get("vector"));
        }

        return selected;
    }

    private Collection<ContentRetriever> fallback(Query query) {
        log.info("🎯 [路由·降级] query='{}' → 全部检索器", query.text());
        return new ArrayList<>(retrievers.values());
    }

    private String getRetrieverKey(ContentRetriever retriever) {
        return retrievers.entrySet().stream()
            .filter(e -> e.getValue() == retriever)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse("unknown");
    }
}
