package com.kanade.backend.graph;

import com.kanade.backend.entity.Document;
import com.kanade.backend.entity.GraphTask;
import com.kanade.backend.graph.model.GraphEntity;
import com.kanade.backend.graph.model.GraphRelation;
import com.kanade.backend.mapper.GraphTaskMapper;
import com.kanade.backend.service.DocumentService;
import com.kanade.backend.sse.SseEmitterManager;
import com.kanade.backend.utils.GsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 图谱构建编排器——异步执行完整的构建流水线。
 *
 * 流程：文档加载 → 预处理 → 分批 → LLM抽取 → 实体对齐 → Neo4j写入
 * 通过 SSE 实时推送进度，通过 MySQL GraphTask 记录状态。
 *
 * @author kanade
 */
@Slf4j
@Service
public class GraphBuildOrchestrator {

    private final DocumentService documentService;
    private final GraphDocumentPreprocessor preprocessor;
    private final GraphBatchBuilder batchBuilder;
    private final GraphExtractionService extractionService;
    private final GraphEntityAligner aligner;
    private final GraphCrudService graphCrudService;
    private final GraphTaskMapper graphTaskMapper;
    private final SseEmitterManager sseEmitterManager;

    public GraphBuildOrchestrator(DocumentService documentService,
                                  GraphDocumentPreprocessor preprocessor,
                                  GraphBatchBuilder batchBuilder,
                                  GraphExtractionService extractionService,
                                  GraphEntityAligner aligner,
                                  GraphCrudService graphCrudService,
                                  GraphTaskMapper graphTaskMapper,
                                  SseEmitterManager sseEmitterManager) {
        this.documentService = documentService;
        this.preprocessor = preprocessor;
        this.batchBuilder = batchBuilder;
        this.extractionService = extractionService;
        this.aligner = aligner;
        this.graphCrudService = graphCrudService;
        this.graphTaskMapper = graphTaskMapper;
        this.sseEmitterManager = sseEmitterManager;
    }

    /**
     * 异步执行图谱构建。
     *
     * @param taskId      任务 ID
     * @param userId      用户 ID
     * @param documentIds 选中的文档 ID 列表（可为空，表示全部文档）
     */
    @Async("graphBuildExecutor")
    public void buildAsync(Long taskId, Long userId, List<Long> documentIds) {
        log.info("🚀 [构建开始] taskId={}, userId={}, docIds={}", taskId, userId, documentIds);

        // 等待前端 SSE 订阅连接就绪
        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

        try {
            updateTask(taskId, "PROCESSING", 0, null);

            // 阶段 1：加载文档
            pushProgress(taskId, "PROCESSING", 5, "正在加载文档...");
            List<Document> docs = loadDocuments(userId, documentIds);
            if (docs.isEmpty()) {
                updateTask(taskId, "FAILED", 0, "没有可处理的文档");
                pushProgress(taskId, "FAILED", 0, "没有可处理的文档");
                return;
            }

            // 阶段 2：预处理（文本提取 + token 计数）
            pushProgress(taskId, "PROCESSING", 10, "正在预处理文档...");
            List<GraphDocumentPreprocessor.PreprocessedDoc> preprocessed = new ArrayList<>();
            for (Document doc : docs) {
                GraphDocumentPreprocessor.PreprocessedDoc pd = preprocessor.preprocess(
                    doc.getId(), doc.getName(), doc.getFilePath());
                if (pd != null) {
                    preprocessed.add(pd);
                }
            }
            log.info("📄 [预处理完成] 成功={}/{}", preprocessed.size(), docs.size());
            if (preprocessed.isEmpty()) {
                updateTask(taskId, "FAILED", 0, "文档未解析出有效文本");
                pushProgress(taskId, "FAILED", 0, "文档未解析出有效文本，请确认文档为可复制文本形式");
                return;
            }

            // 阶段 3：智能分批
            pushProgress(taskId, "PROCESSING", 20, "正在分批...");
            GraphBatchBuilder.BatchResult batchResult = batchBuilder.buildBatches(preprocessed);
            updateTaskBatches(taskId, batchResult.batches().size(), 0);

            // 阶段 4：逐批 LLM 抽取
            List<GraphEntity> allEntities = new ArrayList<>();
            List<GraphRelation> allRelations = new ArrayList<>();
            int totalBatches = batchResult.batches().size();

            for (int i = 0; i < totalBatches; i++) {
                GraphBatchBuilder.Batch batch = batchResult.batches().get(i);
                int progressPct = 20 + (int) ((i + 1) * 60.0 / totalBatches);
                pushProgress(taskId, "PROCESSING", progressPct,
                    String.format("正在抽取第 %d/%d 批...", i + 1, totalBatches));

                // 拼接批次文本（标注文档来源）
                String batchText = buildBatchText(batch);
                Map<Long, String> docIdMap = batch.docs().stream()
                    .collect(Collectors.toMap(
                        GraphDocumentPreprocessor.PreprocessedDoc::docId,
                        GraphDocumentPreprocessor.PreprocessedDoc::docName,
                        (a, b) -> a));

                GraphExtractionService.ExtractionResult result =
                    extractionService.extract(batchText, userId, docIdMap);

                if (result.success()) {
                    allEntities.addAll(result.entities());
                    allRelations.addAll(result.relations());
                    log.info("✅ [批次完成] batch={}/{}, 实体={}, 关系={}",
                        i + 1, totalBatches, result.entities().size(), result.relations().size());
                } else {
                    log.error("❌ [批次失败] batch={}/{}, error={}", i + 1, totalBatches, result.errorMessage());
                }

                updateTaskBatches(taskId, totalBatches, i + 1);
            }

            if (allEntities.isEmpty()) {
                pushProgress(taskId, "PROCESSING", 82, "AI抽取失败，正在使用本地术语抽取兜底...");
                GraphExtractionService.ExtractionResult fallbackResult = buildFallbackGraph(preprocessed, userId);
                allEntities.addAll(fallbackResult.entities());
                allRelations.addAll(fallbackResult.relations());
                log.info("🧩 [本地兜底抽取] 实体={}, 关系={}",
                    fallbackResult.entities().size(), fallbackResult.relations().size());
                if (allEntities.isEmpty()) {
                    updateTask(taskId, "FAILED", 0, "所有批次抽取均失败");
                    pushProgress(taskId, "FAILED", 0, "所有批次抽取均失败");
                    return;
                }
            }

            // 阶段 5：实体对齐
            pushProgress(taskId, "PROCESSING", 85, "正在实体对齐...");
            GraphEntityAligner.AlignmentResult alignment = aligner.align(allEntities);
            log.info("🔄 [对齐完成] 原始={}, 对齐后={}", alignment.originalCount(), alignment.mergedCount());

            // 阶段 6：写入 Neo4j
            pushProgress(taskId, "PROCESSING", 92, "正在写入图谱...");
            graphCrudService.mergeEntitiesBatch(alignment.mergedEntities());
            graphCrudService.createRelationsBatch(allRelations);
            log.info("💾 [写入完成] 实体={}, 关系={}", alignment.mergedCount(), allRelations.size());

            // 完成
            Map<String, Long> stats = graphCrudService.stats(userId);
            pushProgress(taskId, "COMPLETED", 100,
                String.format("图谱构建完成！节点=%d, 关系=%d", stats.get("nodeCount"), stats.get("relCount")));
            updateTask(taskId, "COMPLETED", 100, null);

            log.info("🎉 [构建完成] taskId={}, 节点={}, 关系={}",
                taskId, stats.get("nodeCount"), stats.get("relCount"));

        } catch (Exception e) {
            log.error("💥 [构建异常] taskId={}, error={}", taskId, e.getMessage(), e);
            updateTask(taskId, "FAILED", 0, e.getMessage());
            pushProgress(taskId, "FAILED", 0, "构建失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 加载用户文档（指定 ID 或全部）。
     */
    private List<Document> loadDocuments(Long userId, List<Long> documentIds) {
        if (documentIds != null && !documentIds.isEmpty()) {
            return documentIds.stream()
                .map(id -> documentService.getById(id))
                .filter(Objects::nonNull)
                .filter(d -> Objects.equals(d.getUserId(), userId))
                .filter(d -> Integer.valueOf(0).equals(d.getDeleteFlag()))
                .collect(Collectors.toList());
        }
        // 全部文档
        return documentService.listByUser(userId).stream()
            .map(vo -> documentService.getById(vo.getId()))
            .filter(Objects::nonNull)
            .filter(d -> Objects.equals(d.getUserId(), userId))
            .filter(d -> Integer.valueOf(0).equals(d.getDeleteFlag()))
            .collect(Collectors.toList());
    }

    private GraphExtractionService.ExtractionResult buildFallbackGraph(
            List<GraphDocumentPreprocessor.PreprocessedDoc> docs,
            Long userId) {
        List<GraphEntity> entities = new ArrayList<>();
        List<GraphRelation> relations = new ArrayList<>();
        Set<String> seenEntityIds = new LinkedHashSet<>();

        for (GraphDocumentPreprocessor.PreprocessedDoc doc : docs) {
            String docEntityId = stableEntityId("doc", userId, String.valueOf(doc.docId()));
            if (seenEntityIds.add(docEntityId)) {
                entities.add(GraphEntity.builder()
                        .entityId(docEntityId)
                        .name(doc.docName())
                        .type("Document")
                        .userId(userId)
                        .sourceDocIds(String.valueOf(doc.docId()))
                        .build());
            }

            List<String> terms = extractFallbackTerms(doc.text(), 16);
            String previousTermId = null;
            for (String term : terms) {
                String termEntityId = stableEntityId("term", userId, term);
                if (seenEntityIds.add(termEntityId)) {
                    entities.add(GraphEntity.builder()
                            .entityId(termEntityId)
                            .name(term)
                            .type("Concept")
                            .userId(userId)
                            .sourceDocIds(String.valueOf(doc.docId()))
                            .build());
                }
                relations.add(GraphRelation.builder()
                        .fromEntityId(docEntityId)
                        .toEntityId(termEntityId)
                        .type("INCLUDES")
                        .userId(userId)
                        .sourceDocId(String.valueOf(doc.docId()))
                        .build());

                if (previousTermId != null) {
                    relations.add(GraphRelation.builder()
                            .fromEntityId(previousTermId)
                            .toEntityId(termEntityId)
                            .type("RELATED_TO")
                            .userId(userId)
                            .sourceDocId(String.valueOf(doc.docId()))
                            .build());
                }
                previousTermId = termEntityId;
            }
        }

        return GraphExtractionService.ExtractionResult.success(entities, relations, 0);
    }

    private List<String> extractFallbackTerms(String text, int limit) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        Map<String, Integer> frequencies = new LinkedHashMap<>();
        String normalized = text
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("[，。！？、；：,.!?;:()（）【】\\[\\]<>《》\"'“”]", " ");

        addMatches(frequencies, normalized,
                "[\\u4e00-\\u9fa5A-Za-z0-9]{1,12}(结构|原理|系统|方法|模型|算法|单元|寄存器|指令|总线|控制器|运算器|存储器|处理器|数据|功能|过程|部件|逻辑|电路|地址|编码|接口|缓存|中断|程序)");
        addMatches(frequencies, normalized, "[A-Za-z][A-Za-z0-9_\\-]{1,30}");

        if (frequencies.size() < 4) {
            for (String segment : normalized.split("\\s+")) {
                String clean = segment.trim();
                if (isUsefulFallbackTerm(clean)) {
                    frequencies.merge(clean, 1, Integer::sum);
                }
            }
        }

        return frequencies.entrySet().stream()
                .filter(entry -> isUsefulFallbackTerm(entry.getKey()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private void addMatches(Map<String, Integer> frequencies, String text, String regex) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(text);
        while (matcher.find()) {
            String term = matcher.group().trim();
            if (isUsefulFallbackTerm(term)) {
                frequencies.merge(term, 1, Integer::sum);
            }
        }
    }

    private boolean isUsefulFallbackTerm(String term) {
        if (term == null) {
            return false;
        }
        String clean = term.trim();
        if (clean.length() < 2 || clean.length() > 24) {
            return false;
        }
        return !Set.of("可以", "一个", "以及", "如果", "因为", "所以", "进行", "使用", "通过", "包括",
                "the", "and", "for", "with", "this", "that").contains(clean.toLowerCase());
    }

    private String stableEntityId(String prefix, Long userId, String value) {
        String raw = prefix + ":" + userId + ":" + value;
        return prefix + "_" + DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
    }

    /**
     * 构建批次文本（每个文档前标注文档名和 ID）。
     */
    private String buildBatchText(GraphBatchBuilder.Batch batch) {
        StringBuilder sb = new StringBuilder();
        for (GraphDocumentPreprocessor.PreprocessedDoc doc : batch.docs()) {
            sb.append("--- 文档: ")
                .append(doc.docName())
                .append(" (ID: ").append(doc.docId()).append(") ---\n");
            sb.append(doc.text()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 更新任务状态。
     */
    private void updateTask(Long taskId, String status, int progress, String errorMessage) {
        GraphTask task = graphTaskMapper.selectOneById(taskId);
        if (task != null) {
            task.setStatus(status);
            task.setProgress(progress);
            if (errorMessage != null) task.setErrorMessage(errorMessage);
            task.setUpdateTime(LocalDateTime.now());
            graphTaskMapper.update(task);
        }
    }

    /**
     * 更新任务批次进度。
     */
    private void updateTaskBatches(Long taskId, int total, int completed) {
        GraphTask task = graphTaskMapper.selectOneById(taskId);
        if (task != null) {
            task.setTotalBatches(total);
            task.setCompletedBatches(completed);
            task.setUpdateTime(LocalDateTime.now());
            graphTaskMapper.update(task);
        }
    }

    /**
     * 通过 SSE 推送进度。
     */
    private void pushProgress(Long taskId, String status, int progress, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "graph_progress");
        data.put("taskId", taskId);
        data.put("status", status);
        data.put("progress", progress);
        data.put("message", message);
        sseEmitterManager.send(taskId.toString(), GsonUtils.toJson(data));
        if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
            sseEmitterManager.complete(taskId.toString());
        }
    }
}
