package com.kanade.backend.ai.rag.injector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PromptTemplateManager {

    private final Map<String, String> templates = new ConcurrentHashMap<>();

    public PromptTemplateManager() {
        registerDefaultTemplates();
    }

    private void registerDefaultTemplates() {
        templates.put("standard", """
            你是一个智能助手，请根据以下参考资料回答问题。

            参考资料：
            --------
            {contents}
            --------

            要求：
            1. 基于参考资料回答，不要编造信息
            2. 引用资料时标注来源编号
            3. 如果资料不足，请如实告知

            问题：{question}
            """);

        templates.put("concise", """
            根据参考资料回答问题：

            {contents}

            问题：{question}
            回答：
            """);

        templates.put("analytical", """
            你是一个专业的知识分析助手。请仔细分析以下参考资料，然后回答问题。

            参考资料：
            --------
            {contents}
            --------

            分析要求：
            1. 综合多条资料的信息，形成完整回答
            2. 指出资料之间的关联和差异
            3. 引用具体来源编号
            4. 如果资料存在矛盾，请明确指出

            问题：{question}

            请开始分析：
            """);
    }

    public String getTemplate(String templateName) {
        String template = templates.get(templateName);
        if (template == null) {
            log.warn("⚠️ [模板管理] 模板不存在: {}，使用标准模板", templateName);
            return templates.get("standard");
        }
        return template;
    }

    public void registerTemplate(String name, String template) {
        templates.put(name, template);
        log.info("📝 [模板管理] 注册新模板: {}", name);
    }

    public TemplateContentInjector createInjector(String templateName) {
        String template = getTemplate(templateName);
        return new TemplateContentInjector(template);
    }
}
