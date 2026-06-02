package com.kanade.backend.document;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class DocumentParseService {

    private static final int CHUNK_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 150;

    public List<DocumentChunk> parse(Path filePath, String fileType) throws IOException {
        String ext = fileType == null ? "" : fileType.toLowerCase();
        List<DocumentChunk> pageChunks = new ArrayList<>();
        switch (ext) {
            case "pdf" -> parsePdf(filePath, pageChunks);
            case "docx" -> splitToChunks(readDocx(filePath), 0, pageChunks);
            case "xlsx", "xls" -> splitToChunks(readExcel(filePath), 0, pageChunks);
            case "txt", "md", "markdown", "json", "csv", "java", "vue", "js", "ts", "xml", "yml", "yaml" ->
                    splitToChunks(Files.readString(filePath, StandardCharsets.UTF_8), 0, pageChunks);
            default -> splitToChunks(Files.readString(filePath, StandardCharsets.UTF_8), 0, pageChunks);
        }
        if (pageChunks.isEmpty()) {
            pageChunks.add(DocumentChunk.builder().content("[文档未解析出有效文本]").pageNum(0).chunkIndex(0).build());
        }
        for (int i = 0; i < pageChunks.size(); i++) {
            pageChunks.get(i).setChunkIndex(i);
        }
        return pageChunks;
    }

    public String parseAsText(Path filePath, String fileType) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (DocumentChunk chunk : parse(filePath, fileType)) {
            sb.append(chunk.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    private void parsePdf(Path filePath, List<DocumentChunk> out) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                splitToChunks(stripper.getText(pdf), page, out);
            }
        }
    }

    private String readDocx(Path filePath) throws IOException {
        try (InputStream in = Files.newInputStream(filePath); XWPFDocument doc = new XWPFDocument(in)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                if (p.getText() != null && !p.getText().isBlank()) sb.append(p.getText()).append('\n');
            }
            return sb.toString();
        }
    }

    private String readExcel(Path filePath) throws IOException {
        try (InputStream in = Files.newInputStream(filePath); Workbook workbook = WorkbookFactory.create(in)) {
            StringBuilder sb = new StringBuilder();
            DataFormatter formatter = new DataFormatter();
            for (Sheet sheet : workbook) {
                sb.append("【Sheet: ").append(sheet.getSheetName()).append("】\n");
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) cells.add(formatter.formatCellValue(cell));
                    sb.append(String.join("\t", cells)).append('\n');
                }
            }
            return sb.toString();
        }
    }

    private void splitToChunks(String text, int pageNum, List<DocumentChunk> out) {
        if (text == null || text.isBlank()) return;
        String clean = text.replace("\r", "").trim();
        int start = 0;
        while (start < clean.length()) {
            int end = Math.min(start + CHUNK_SIZE, clean.length());
            String chunk = clean.substring(start, end).trim();
            if (!chunk.isBlank()) {
                out.add(DocumentChunk.builder().content(chunk).pageNum(pageNum).build());
            }
            if (end >= clean.length()) break;
            start = Math.max(0, end - CHUNK_OVERLAP);
        }
    }
}
