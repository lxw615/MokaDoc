package com.kanade.backend.ai.rag.router;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class SmartQueryRouter implements QueryRouter {

    private final ChatModel chatModel;
    private final Map<String, ContentRetriever> retrievers;

    public SmartQueryRouter(ChatModel chatModel,
                            Map<String, ContentRetriever> retrievers) {
        this.chatModel = chatModel;
        this.retrievers = retrievers;
    }

    @Override
    public Collection<ContentRetriever> route(Query query) {
        try {
            String intent = analyzeIntent(query.text());
            log.info("🎯 [智能路由] query='{}', intent={}", query.text(), intent);

            Collection<ContentRetriever> selectedRetrievers = selectRetrievers(intent);

            log.info("🎯 [路由决策] 选择了 {} 个检索器: {}",
                selectedRetrievers.size(),
                selectedRetrievers.stream()
                    .map(r -> r.getClass().getSimpleName())
                    .collect(Collectors.joining(", ")));

            return selectedRetrievers;
        } catch (Exception e) {
            log.error("❌ [路由失败] 降级使用全部检索器", e);
            return new ArrayList<>(retrievers.values());
        }
    }

    private String analyzeIntent(String query) {
        String prompt = String.format("""
            请分析以下用户查询的意图类型，从以下选项中选择最匹配的一个：

            选项：
            - SEMANTIC: 语义相似性查询（如"什么是XXX"、"XXX的特点"）
            - KEYWORD: 关键词精确匹配（如"XXX的定义"、"XXX的版本号"）
            - RELATION: 实体关系查询（如"XXX和YYY的关系"、"XXX的影响"）
            - COMPLEX: 复杂综合查询（需要多角度信息）

            用户查询：%s

            意图类型（只输出选项名称）：""", query);

        String intent = chatModel.chat(prompt).trim().toUpperCase();

        if (!Arrays.asList("SEMANTIC", "KEYWORD", "RELATION", "COMPLEX").contains(intent)) {
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
            case "RELATION", "COMPLEX" -> selected.addAll(retrievers.values());
        }

        if (selected.isEmpty() && retrievers.containsKey("vector")) {
            selected.add(retrievers.get("vector"));
        }

        return selected;
    }
}
