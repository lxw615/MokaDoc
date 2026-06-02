package com.kanade.backend.service;

import com.mybatisflex.core.service.IService;
import com.kanade.backend.entity.QaReference;
import dev.langchain4j.rag.content.Content;

import java.util.List;

/**
 * 回答引用表 服务层。
 *
 * @author kanade
 */
public interface QaReferenceService extends IService<QaReference> {

    /**
     * 根据本次问答选择的文档，保存 AI 回答引用溯源片段。
     */
    void saveReferences(Long messageId, java.util.List<Long> documentIds, Long userId, String question);

    /**
     * 保存本次 RAG 实际注入给模型、并在回答中被标注引用的来源片段。
     */
    void saveInjectedReferences(Long messageId,
                                List<Long> documentIds,
                                Long userId,
                                String question,
                                String aiResponse,
                                List<Content> injectedContents);

    /**
     * 查询某条 AI 消息的引用来源。
     */
    List<QaReference> listByMessageId(Long messageId, Long userId);
}
