package com.scripty.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ScriptImportTextExtractor {

    public String extract(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return "";
        }

        String filename = file.getOriginalFilename();
        String lowerName = filename != null ? filename.toLowerCase(Locale.ROOT) : "";
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";

        if (isFdx(lowerName, contentType)) {
            return FdxToFountainConverter.convert(file.getInputStream());
        }
        if (isDocx(lowerName, contentType)) {
            return extractDocx(file.getInputStream());
        }
        if (isDoc(lowerName, contentType)) {
            return extractDoc(file.getInputStream());
        }

        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Extract plain text for songs/drafts (never Fountain-converts DOCX screenplay layout).
     */
    public String extractPlain(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return "";
        }

        String filename = file.getOriginalFilename();
        String lowerName = filename != null ? filename.toLowerCase(Locale.ROOT) : "";
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";

        if (isFdx(lowerName, contentType)) {
            return FdxToFountainConverter.convertPlain(file.getInputStream());
        }
        if (isDocx(lowerName, contentType)) {
            try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
                return extractDocxPlain(document);
            }
        }
        if (isDoc(lowerName, contentType)) {
            return extractDoc(file.getInputStream());
        }

        return normalizeLineEndings(new String(file.getBytes(), StandardCharsets.UTF_8));
    }

    private static boolean isFdx(String lowerName, String contentType) {
        return FdxToFountainConverter.looksLikeFdx(lowerName, contentType);
    }

    private static boolean isDocx(String lowerName, String contentType) {
        return lowerName.endsWith(".docx")
                || contentType.contains("wordprocessingml.document");
    }

    private static boolean isDoc(String lowerName, String contentType) {
        return lowerName.endsWith(".doc")
                || "application/msword".equals(contentType);
    }

    private static String extractDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            if (DocxToFountainConverter.looksLikeScreenplayLayout(document)) {
                return DocxToFountainConverter.convert(document);
            }
            return extractDocxPlain(document);
        }
    }

    private static String extractDocxPlain(XWPFDocument document) {
        StringBuilder text = new StringBuilder();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String paragraphText = paragraph.getText();
            if (paragraphText == null) {
                continue;
            }
            String trimmed = paragraphText.stripTrailing();
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(trimmed);
        }
        return text.toString();
    }

    private static String extractDoc(InputStream inputStream) throws IOException {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return normalizeLineEndings(extractor.getText());
        }
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
