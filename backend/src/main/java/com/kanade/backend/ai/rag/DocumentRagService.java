package com.kanade.backend.ai.rag;

import com.kanade.backend.document.DocumentChunk;
import com.kanade.backend.document.DocumentParseService;
import com.kanade.backend.entity.Document;
import com.kanade.backend.service.DocumentService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DocumentRagService {

    @Value("${file.upload.dir:./uploads/documents}")
    private String uploadDir;

    @Resource
    @Lazy
    private DocumentService documentService;

    @Resource
    private ElasticsearchEmbeddingStore embeddingStore;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private DocumentParseService documentParseService;

    private final Map<Long, Set<Long>> sessionDocumentIds = new ConcurrentHashMap<>();
    private final Set<Long> indexedDocuments = ConcurrentHashMap.newKeySet();
    private static final int LOCAL_FALLBACK_MAX_RESULTS = 8;

    /**
     * 获取指定会话的文档内容检索器
     */
    public ContentRetriever getContentRetriever(Long sessionId, List<Long> documentIds, Long userId) {
        if (documentIds == null || documentIds.isEmpty()) {
            return null;
        }

        Set<Long> docIdSet = new HashSet<>(documentIds);
        Set<Long> cachedIds = sessionDocumentIds.get(sessionId);

        if (cachedIds != null && cachedIds.equals(docIdSet)) {
            log.info("复用会话 {} 的 RAG 缓存，文档: {}", sessionId, docIdSet);

            return createStrictDocumentRetriever(
                    createFilteredVectorRetriever(userId, documentIds),
                    loadLocalDocumentContents(docIdSet, userId));
        }

        int newDocs = 0;
        for (Long docId : docIdSet) {
            if (!indexedDocuments.contains(docId)) {
                if (indexDocument(docId, userId)) {
                    indexedDocuments.add(docId);
                    newDocs++;
                }
            }
        }

        sessionDocumentIds.put(sessionId, docIdSet);
        log.info("已构建会话 {} 的 RAG 索引，文档: {}, 新索引: {} 个", sessionId, docIdSet, newDocs);

        return createStrictDocumentRetriever(
                createFilteredVectorRetriever(userId, documentIds),
                loadLocalDocumentContents(docIdSet, userId));
    }

    /**
     * 清除指定会话的 RAG 缓存
     */
    public void clearCache(Long sessionId) {
        sessionDocumentIds.remove(sessionId);
    }

    /**
     * 删除文档的所有向量嵌入
     */
    public void removeDocumentEmbeddings(Long documentId) {
        indexedDocuments.remove(documentId);
        log.info("已从缓存中移除文档索引: docId={}", documentId);
    }

    private boolean indexDocument(Long docId, Long userId) {
        try {
            Document entity = documentService.getById(docId);
            if (entity == null || entity.getDeleteFlag() == 1) {
                log.warn("文档不存在或已删除: docId={}", docId);
                return false;
            }
            if (!entity.getUserId().equals(userId)) {
                log.warn("无权访问文档: docId={}, userId={}", docId, userId);
                return false;
            }

            Path filePath = Paths.get(uploadDir, entity.getFilePath());
            if (!Files.exists(filePath)) {
                log.warn("文档文件不存在: {}", filePath);
                return false;
            }

            List<dev.langchain4j.data.document.Document> documents = parseDocuments(entity, filePath, userId);
            if (documents.isEmpty()) {
                log.warn("文档未解析出可索引内容: docId={}", docId);
                return false;
            }

            // 写入es content & vector
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .textSegmentTransformer(segment -> {
                        Metadata metadata = segment.metadata() != null
                                ? segment.metadata().copy()
                                : new Metadata();
                        metadata.put("userId", userId.toString());
                        metadata.put("documentId", docId.toString());
                        return TextSegment.from(segment.text(), metadata);
                    })
                    .build();

            ingestor.ingest(documents);
            log.info("文档已索引到 ES: docId={}, name={}, chunks={}", docId, entity.getName(), documents.size());
            return true;
        } catch (Exception e) {
            log.error("索引文档失败: docId={}", docId, e);
            return false;
        }
    }

    private List<dev.langchain4j.data.document.Document> parseDocuments(Document entity, Path filePath, Long userId) throws Exception {
        List<DocumentChunk> chunks = documentParseService.parse(filePath, entity.getFileType());
        List<dev.langchain4j.data.document.Document> documents = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                continue;
            }
            Metadata metadata = new Metadata()
                    .put("userId", userId.toString())
                    .put("documentId", entity.getId().toString())
                    .put("documentName", entity.getName())
                    .put("fileType", entity.getFileType() == null ? "" : entity.getFileType())
                    .put("pageNum", chunk.getPageNum() == null ? 0 : chunk.getPageNum())
                    .put("chunkIndex", chunk.getChunkIndex() == null ? 0 : chunk.getChunkIndex());
            documents.add(dev.langchain4j.data.document.Document.from(chunk.getContent(), metadata));
        }
        return documents;
    }

    private ContentRetriever createFilteredVectorRetriever(Long userId, List<Long> documentIds) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .filter(buildDocumentFilter(userId, documentIds))
                .build();
    }

    private ContentRetriever createStrictDocumentRetriever(ContentRetriever vectorRetriever, List<Content> localContents) {
        return new ContentRetriever() {
            @Override
            public List<Content> retrieve(Query query) {
                try {
                    List<Content> vectorContents = vectorRetriever.retrieve(query);
                    if (vectorContents != null && !vectorContents.isEmpty()) {
                        log.info("[Document RAG] Vector retrieval hit {} chunks", vectorContents.size());
                        return vectorContents;
                    }
                } catch (Exception e) {
                    log.warn("[Document RAG] Vector retrieval failed, falling back to parsed document chunks: {}", e.getMessage());
                }
                List<Content> fallbackContents = rankLocalContents(query, localContents);
                log.info("[Document RAG] Local parsed fallback returned {} chunks", fallbackContents.size());
                return fallbackContents;
            }
        };
    }

    private List<Content> loadLocalDocumentContents(Set<Long> docIds, Long userId) {
        List<Content> contents = new ArrayList<>();
        for (Long docId : docIds) {
            try {
                Document entity = documentService.getById(docId);
                if (entity == null || entity.getDeleteFlag() == 1 || !entity.getUserId().equals(userId)) {
                    continue;
                }
                Path filePath = Paths.get(uploadDir, entity.getFilePath());
                if (!Files.exists(filePath)) {
                    continue;
                }
                List<DocumentChunk> chunks = documentParseService.parse(filePath, entity.getFileType());
                for (DocumentChunk chunk : chunks) {
                    if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                        continue;
                    }
                    int pageNum = chunk.getPageNum() == null ? 0 : chunk.getPageNum();
                    String text = "【文档：" + entity.getName() + "】\n"
                            + "【页码：" + pageNum + "】\n"
                            + chunk.getContent();
                    Metadata metadata = new Metadata()
                            .put("userId", userId.toString())
                            .put("documentId", entity.getId().toString())
                            .put("documentName", entity.getName())
                            .put("fileType", entity.getFileType() == null ? "" : entity.getFileType())
                            .put("pageNum", pageNum)
                            .put("chunkIndex", chunk.getChunkIndex() == null ? 0 : chunk.getChunkIndex());
                    contents.add(Content.from(TextSegment.from(text, metadata)));
                }
            } catch (Exception e) {
                log.warn("[Document RAG] Failed to load local document chunks: docId={}, error={}", docId, e.getMessage());
            }
        }
        return contents;
    }

    private List<Content> rankLocalContents(Query query, List<Content> localContents) {
        if (localContents == null || localContents.isEmpty()) {
            return List.of();
        }
        String queryText = query == null ? "" : query.text();
        List<Content> ranked = new ArrayList<>(localContents);
        ranked.sort(Comparator.comparingInt((Content content) ->
                scoreContent(queryText, content.textSegment().text())).reversed());
        return ranked.stream()
                .limit(LOCAL_FALLBACK_MAX_RESULTS)
                .toList();
    }

    private int scoreContent(String query, String text) {
        if (query == null || query.isBlank() || text == null || text.isBlank()) {
            return 0;
        }
        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();
        int score = 0;
        for (String term : lowerQuery.split("[\\s,，。；;：:！!？?、]+")) {
            if (term.length() >= 2 && lowerText.contains(term)) {
                score += term.length() * 3;
            }
        }
        if (score > 0) {
            return score;
        }
        for (int i = 0; i < lowerQuery.length(); i++) {
            char c = lowerQuery.charAt(i);
            if (!Character.isWhitespace(c) && lowerText.indexOf(c) >= 0) {
                score++;
            }
        }
        return score;
    }

    private Filter buildDocumentFilter(Long userId, List<Long> documentIds) {
        Filter userFilter = MetadataFilterBuilder.metadataKey("userId").isEqualTo(userId.toString());
        if (documentIds == null || documentIds.isEmpty()) {
            return userFilter;
        }
        List<String> ids = documentIds.stream()
                .map(String::valueOf)
                .toList();
        Filter documentFilter = MetadataFilterBuilder.metadataKey("documentId").isIn(ids);
        return userFilter.and(documentFilter);
    }
}
