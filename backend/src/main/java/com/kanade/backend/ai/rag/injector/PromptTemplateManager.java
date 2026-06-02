package com.kanade.backend.ai.rag.injector;

import com.kanade.backend.ai.rag.RagReferenceCollector;
import org.springframework.stereotype.Component;

@Component
public class PromptTemplateManager {

    public TemplateContentInjector createInjector(String templateName) {
        return new TemplateContentInjector(templateName);
    }

    public TemplateContentInjector createInjector(String templateName, RagReferenceCollector referenceCollector) {
        return new TemplateContentInjector(templateName, referenceCollector);
    }
}
