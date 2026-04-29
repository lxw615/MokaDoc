package com.kanade.backend.ai.rag.injector;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class TemplateContentInjector implements ContentInjector {

    private final String template;

    public TemplateContentInjector() {
        this("""
            你是一个智能助手，请根据以下参考资料回答问题。

            参考资料：
            --------
            {contents}
            --------

            要求：
            1. 基于参考资料回答，不要编造信息
            2. 引用资料时标注来源编号，如 [1]、[2]
            3. 如果参考资料不足以回答问题，请如实告知
            4. 回答要清晰、准确、有条理

            问题：{question}
            """);
    }

    public TemplateContentInjector(String template) {
        this.template = template;
    }

    @Override
    public ChatMessage inject(List<Content> contents, ChatMessage chatMessage) {
        if (contents == null || contents.isEmpty()) {
            log.warn("⚠️ [内容注入] 无参考内容，返回原消息");
            return chatMessage;
        }

        String formattedContents = formatContents(contents);
        String questionText = chatMessage instanceof UserMessage
            ? ((UserMessage) chatMessage).singleText()
            : chatMessage.toString();

        String prompt = template
            .replace("{contents}", formattedContents)
            .replace("{question}", questionText);

        log.info("💉 [内容注入] 注入了 {} 条参考内容", contents.size());

        return UserMessage.from(prompt);
    }

    private String formatContents(List<Content> contents) {
        return IntStream.range(0, contents.size())
            .mapToObj(i -> {
                Content content = contents.get(i);
                String text = content.textSegment().text();
                if (text.length() > 500) {
                    text = text.substring(0, 500) + "...";
                }
                return String.format("[%d] %s", i + 1, text);
            })
            .collect(Collectors.joining("\n\n"));
    }
}
