package com.kanade.backend.ai.rag;

import dev.langchain4j.rag.content.Content;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Captures the exact RAG contents injected into a single AI request.
 */
public class RagReferenceCollector {

    private final AtomicReference<List<Content>> contentsRef = new AtomicReference<>(List.of());

    public void capture(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            contentsRef.set(List.of());
            return;
        }
        contentsRef.set(List.copyOf(contents));
    }

    public List<Content> getContents() {
        return new ArrayList<>(contentsRef.get());
    }
}
