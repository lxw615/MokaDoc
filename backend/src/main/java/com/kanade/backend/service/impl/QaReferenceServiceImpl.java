package com.kanade.backend.service.impl;

import com.kanade.backend.document.DocumentChunk;
import com.kanade.backend.document.DocumentParseService;
import com.kanade.backend.entity.Document;
import com.kanade.backend.entity.QaMessage;
import com.kanade.backend.entity.QaReference;
import com.kanade.backend.entity.QaSession;
import com.kanade.backend.mapper.QaReferenceMapper;
import com.kanade.backend.service.DocumentService;
import com.kanade.backend.service.QaMessageService;
import com.kanade.backend.service.QaReferenceService;
import com.kanade.backend.service.QaSessionService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.rag.content.Content;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 回答引用表 服务层实现。
 */
@Slf4j
@Service
public class QaReferenceServiceImpl extends ServiceImpl<QaReferenceMapper, QaReference> implements QaReferenceService {

    private static final Pattern CITATION_PATTERN = Pattern.compile("[\\[［【]([\\d\\s,，、]+)[\\]］】]");
    private static final Pattern DOCUMENT_HEADER_PATTERN = Pattern.compile("【文档：([^】]+)】");
    private static final Pattern PAGE_HEADER_PATTERN = Pattern.compile("【页码：(\\d+)】");

    @Value("${file.upload.dir:./uploads/documents}")
    private String uploadDir;

    @Resource
    private DocumentService documentService;

    @Resource
    private DocumentParseService documentParseService;

    @Resource
    private QaMessageService qaMessageService;

    @Resource
    @Lazy
    private QaSessionService qaSessionService;

    @Override
    public void saveReferences(Long messageId, List<Long> documentIds, Long userId, String question) {
        if (messageId == null || CollectionUtils.isEmpty(documentIds)) return;
        try {
            List<QaReference> refs = new ArrayList<>();
            Set<String> keywords = extractKeywords(question);

            for (Long docId : documentIds) {
                Document doc = documentService.getById(docId);
                if (doc == null || Objects.equals(doc.getDeleteFlag(), 1) || !Objects.equals(doc.getUserId(), userId)) continue;
                Path filePath = Paths.get(uploadDir, doc.getFilePath());
                if (!Files.exists(filePath)) continue;

                List<DocumentChunk> chunks = documentParseService.parse(filePath, doc.getFileType());
                chunks.stream()
                        .sorted(Comparator.comparingInt(c -> -score(c.getContent(), keywords)))
                        .limit(2)
                        .filter(c -> c.getContent() != null && !c.getContent().isBlank())
                        .forEach(c -> refs.add(QaReference.builder()
                                .messageId(messageId)
                                .documentId(docId)
                                .content(trim(c.getContent(), 500))
                                .pageNum(c.getPageNum() == null ? 0 : c.getPageNum())
                                .createTime(LocalDateTime.now())
                                .build()));
            }
            if (!refs.isEmpty()) {
                this.saveBatch(refs);
                log.info("✅ [引用溯源] 已保存引用: messageId={}, count={}", messageId, refs.size());
            }
        } catch (Exception e) {
            log.warn("引用溯源保存失败，不影响问答主流程: messageId={}, err={}", messageId, e.getMessage());
        }
    }

    @Override
    public void saveInjectedReferences(Long messageId,
                                       List<Long> documentIds,
                                       Long userId,
                                       String question,
                                       String aiResponse,
                                       List<Content> injectedContents) {
        if (messageId == null || CollectionUtils.isEmpty(documentIds)) {
            return;
        }
        if (CollectionUtils.isEmpty(injectedContents)) {
            saveReferences(messageId, documentIds, userId, question);
            return;
        }

        try {
            List<Integer> sourceIndexes = extractCitationIndexes(aiResponse, injectedContents.size());
            if (sourceIndexes.isEmpty()) {
                sourceIndexes = new ArrayList<>();
                for (int i = 1; i <= injectedContents.size(); i++) {
                    sourceIndexes.add(i);
                }
            }

            List<QaReference> refs = new ArrayList<>();
            for (Integer sourceIndex : sourceIndexes) {
                Content content = injectedContents.get(sourceIndex - 1);
                Long docId = extractDocumentId(content, documentIds, userId);
                if (docId == null || !documentIds.contains(docId)) {
                    continue;
                }

                Document doc = documentService.getById(docId);
                if (doc == null || Objects.equals(doc.getDeleteFlag(), 1) || !Objects.equals(doc.getUserId(), userId)) {
                    continue;
                }

                String text = stripSourceHeaders(contentText(content));
                if (text == null || text.isBlank()) {
                    continue;
                }

                refs.add(QaReference.builder()
                        .messageId(messageId)
                        .documentId(docId)
                        .content("【引用编号：" + sourceIndex + "】\n" + trim(text, 500))
                        .pageNum(extractPageNum(content))
                        .createTime(LocalDateTime.now())
                        .build());
            }

            if (refs.isEmpty()) {
                saveReferences(messageId, documentIds, userId, question);
                return;
            }

            this.remove(QueryWrapper.create().eq("message_id", messageId));
            this.saveBatch(refs);
            log.info("✅ [引用溯源] 已保存实际注入引用: messageId={}, count={}, injected={}, cited={}",
                    messageId, refs.size(), injectedContents.size(), sourceIndexes);
        } catch (Exception e) {
            log.warn("实际注入引用保存失败，降级为本地引用选择: messageId={}, err={}", messageId, e.getMessage());
            saveReferences(messageId, documentIds, userId, question);
        }
    }

    @Override
    public List<QaReference> listByMessageId(Long messageId, Long userId) {
        QaMessage msg = qaMessageService.getById(messageId);
        if (msg == null) return Collections.emptyList();
        QaSession session = qaSessionService.getById(msg.getSessionId());
        if (session == null || !Objects.equals(session.getUserId(), userId)) return Collections.emptyList();

        QueryWrapper qw = QueryWrapper.create()
                .eq("message_id", messageId)
                .orderBy("id", true);
        return this.list(qw);
    }

    private Set<String> extractKeywords(String question) {
        Set<String> result = new LinkedHashSet<>();
        if (question == null) {
            return result;
        }

        String clean = question.replaceAll(
                "[\\p{Punct}，。！？、；：（）【】《》]",
                " "
        );

        for (String token : clean.split("\\s+")) {
            if (token.length() >= 2) {
                result.add(token.toLowerCase());
            }
        }

        return result;
    }

    private int score(String text, Set<String> keywords) {
        if (text == null || keywords.isEmpty()) return 0;
        String lower = text.toLowerCase();
        int score = 0;
        for (String kw : keywords) if (lower.contains(kw)) score += kw.length();
        return score;
    }

    private String trim(String text, int max) {
        String clean = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "...";
    }

    private List<Integer> extractCitationIndexes(String aiResponse, int contentCount) {
        if (aiResponse == null || aiResponse.isBlank() || contentCount <= 0) {
            return List.of();
        }
        Set<Integer> indexes = new LinkedHashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(aiResponse);
        while (matcher.find()) {
            for (String part : matcher.group(1).split("[\\s,，、]+")) {
                if (part.isBlank()) {
                    continue;
                }
                try {
                    int index = Integer.parseInt(part);
                    if (index >= 1 && index <= contentCount) {
                        indexes.add(index);
                    }
                } catch (NumberFormatException ignored) {
                    // ignore malformed citation markers
                }
            }
        }
        return new ArrayList<>(indexes);
    }

    private Long extractDocumentId(Content content, List<Long> documentIds, Long userId) {
        Long metadataDocId = parseLong(metadataValue(content, "documentId"));
        if (metadataDocId != null) {
            return metadataDocId;
        }

        String documentName = extractFirstGroup(DOCUMENT_HEADER_PATTERN, contentText(content));
        if (documentName != null) {
            for (Long docId : documentIds) {
                Document doc = documentService.getById(docId);
                if (doc != null
                        && Objects.equals(doc.getUserId(), userId)
                        && Objects.equals(doc.getName(), documentName)) {
                    return docId;
                }
            }
        }

        return documentIds.size() == 1 ? documentIds.get(0) : null;
    }

    private Integer extractPageNum(Content content) {
        Integer metadataPageNum = parseInteger(metadataValue(content, "pageNum"));
        if (metadataPageNum != null) {
            return metadataPageNum;
        }

        Integer headerPageNum = parseInteger(extractFirstGroup(PAGE_HEADER_PATTERN, contentText(content)));
        return headerPageNum == null ? 0 : headerPageNum;
    }

    private Object metadataValue(Content content, String key) {
        if (content == null || content.textSegment() == null || content.textSegment().metadata() == null) {
            return null;
        }
        Metadata metadata = content.textSegment().metadata();
        return metadata.toMap().get(key);
    }

    private Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer parseInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String contentText(Content content) {
        if (content == null || content.textSegment() == null || content.textSegment().text() == null) {
            return "";
        }
        return content.textSegment().text();
    }

    private String stripSourceHeaders(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceFirst("(?s)^【文档：[^】]+】\\s*", "")
                .replaceFirst("(?s)^【页码：\\d+】\\s*", "");
    }

    private String extractFirstGroup(Pattern pattern, String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
