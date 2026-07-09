package com.scripty.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Converts Final Draft {@code .fdx} XML into Fountain text for
 * {@link FountainImportServiceImpl}.
 */
final class FdxToFountainConverter {

    private static final Pattern CREDIT_LINE = Pattern.compile(
            "^(?:written(?:\\s+and\\s+directed)?\\s+by|story\\s+by|screenplay\\s+by|by)\\.?$",
            Pattern.CASE_INSENSITIVE);

    private FdxToFountainConverter() {
    }

    static boolean looksLikeFdx(String lowerName, String contentType) {
        return lowerName.endsWith(".fdx")
                || "application/x-fdx".equals(contentType);
    }

    static String convertPlain(InputStream inputStream) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(inputStream);
            Element root = document.getDocumentElement();
            if (root == null || !"FinalDraft".equals(root.getTagName())) {
                throw new IOException("Not a Final Draft (.fdx) document");
            }
            Element content = firstChildElement(root, "Content");
            if (content == null) {
                return "";
            }
            StringBuilder out = new StringBuilder();
            for (Element paragraph : childElements(content, "Paragraph")) {
                if (hasChildElement(paragraph, "DualDialogue")) {
                    for (Element side : childElements(paragraph, "DualDialogue")) {
                        for (Element sideParagraph : childElements(side, "Paragraph")) {
                            String text = paragraphText(sideParagraph).trim();
                            if (text.isEmpty()) {
                                continue;
                            }
                            if (out.length() > 0) {
                                out.append('\n');
                            }
                            out.append(text);
                        }
                    }
                    continue;
                }
                String text = paragraphText(paragraph).trim();
                if (text.isEmpty()) {
                    continue;
                }
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(text);
            }
            return out.toString();
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse Final Draft (.fdx) document", e);
        }
    }

    static String convert(InputStream inputStream) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(inputStream);
            Element root = document.getDocumentElement();
            if (root == null || !"FinalDraft".equals(root.getTagName())) {
                throw new IOException("Not a Final Draft (.fdx) document");
            }

            StringBuilder out = new StringBuilder();
            appendTitlePage(out, firstChildElement(root, "TitlePage"));
            appendContent(out, firstChildElement(root, "Content"));
            String exported = out.toString().stripTrailing();
            return exported.isEmpty() ? "" : exported + "\n";
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse Final Draft (.fdx) document", e);
        }
    }

    private static void appendTitlePage(StringBuilder out, Element titlePage) {
        if (titlePage == null) {
            return;
        }
        Element content = firstChildElement(titlePage, "Content");
        if (content == null) {
            return;
        }

        List<String> centered = new ArrayList<>();
        List<String> contact = new ArrayList<>();
        for (Element paragraph : childElements(content, "Paragraph")) {
            String text = paragraphText(paragraph).trim();
            if (text.isEmpty()) {
                continue;
            }
            String alignment = attr(paragraph, "Alignment");
            if ("Left".equalsIgnoreCase(alignment) || "Right".equalsIgnoreCase(alignment)) {
                contact.add(text);
            } else {
                centered.add(text);
            }
        }
        if (centered.isEmpty() && contact.isEmpty()) {
            return;
        }

        if (!centered.isEmpty()) {
            appendLine(out, "Title: " + centered.get(0));
            int i = 1;
            if (i < centered.size() && CREDIT_LINE.matcher(centered.get(i)).matches()) {
                appendLine(out, "Credit: " + centered.get(i));
                i++;
            }
            if (i < centered.size()) {
                appendLine(out, "Author: " + centered.get(i));
                i++;
                while (i < centered.size()) {
                    appendLine(out, centered.get(i));
                    i++;
                }
            }
        }
        if (!contact.isEmpty()) {
            appendLine(out, "Contact: " + contact.get(0));
            for (int i = 1; i < contact.size(); i++) {
                appendLine(out, contact.get(i));
            }
        }
        out.append('\n');
    }

    private static void appendContent(StringBuilder out, Element content) {
        if (content == null) {
            return;
        }

        for (Element paragraph : childElements(content, "Paragraph")) {
            if (hasChildElement(paragraph, "DualDialogue")) {
                appendDualDialogue(out, paragraph);
                continue;
            }

            String type = attr(paragraph, "Type");
            boolean startsNewPage = "Yes".equalsIgnoreCase(attr(paragraph, "StartsNewPage"));
            String text = paragraphText(paragraph);

            if (startsNewPage) {
                ensureBlankLine(out);
                appendLine(out, "===");
            }

            if (type == null || type.isBlank()) {
                type = "Action";
            }

            switch (type) {
                case "Scene Heading" -> {
                    ensureBlankLine(out);
                    if (text.isBlank()) {
                        appendLine(out, ".");
                    } else {
                        appendLine(out, text.trim());
                    }
                }
                case "Action" -> {
                    ensureBlankLine(out);
                    if (!text.isBlank()) {
                        appendMultiline(out, text);
                    }
                }
                case "Character" -> {
                    ensureBlankLine(out);
                    if (!text.isBlank()) {
                        appendLine(out, formatCharacterCue(text, false));
                    }
                }
                case "Dialogue" -> {
                    if (!text.isBlank()) {
                        appendMultiline(out, text);
                    }
                }
                case "Parenthetical" -> {
                    if (!text.isBlank()) {
                        appendLine(out, formatParenthetical(text));
                    }
                }
                case "Transition" -> {
                    ensureBlankLine(out);
                    if (!text.isBlank()) {
                        String trimmed = text.trim();
                        if (trimmed.endsWith(":") || trimmed.toUpperCase(Locale.ROOT).equals(trimmed)) {
                            appendLine(out, trimmed);
                        } else {
                            appendLine(out, ">" + trimmed);
                        }
                    }
                }
                case "Shot" -> {
                    ensureBlankLine(out);
                    if (!text.isBlank()) {
                        appendLine(out, text.trim().toUpperCase(Locale.ROOT));
                    }
                }
                case "General" -> {
                    if (text.isBlank()) {
                        continue;
                    }
                    ensureBlankLine(out);
                    String alignment = attr(paragraph, "Alignment");
                    if ("Center".equalsIgnoreCase(alignment)) {
                        appendLine(out, ">" + text.trim() + "<");
                    } else {
                        appendMultiline(out, text);
                    }
                }
                default -> {
                    if (!text.isBlank()) {
                        ensureBlankLine(out);
                        appendMultiline(out, text);
                    }
                }
            }

            for (Element note : childElements(paragraph, "ScriptNote")) {
                String noteText = paragraphText(note).trim();
                if (!noteText.isEmpty()) {
                    ensureBlankLine(out);
                    appendLine(out, "[[" + noteText + "]]");
                }
            }
        }
    }

    private static void appendDualDialogue(StringBuilder out, Element paragraph) {
        List<Element> sides = childElements(paragraph, "DualDialogue");
        if (sides.isEmpty()) {
            return;
        }

        // First side may also appear as preceding Character/Dialogue siblings;
        // when DualDialogue wraps both sides, emit first as Character and second with ^.
        for (int i = 0; i < sides.size(); i++) {
            Element side = sides.get(i);
            List<Element> sideParagraphs = childElements(side, "Paragraph");
            boolean dual = i > 0;
            for (Element sideParagraph : sideParagraphs) {
                String type = attr(sideParagraph, "Type");
                String text = paragraphText(sideParagraph);
                if ("Character".equals(type)) {
                    ensureBlankLine(out);
                    if (!text.isBlank()) {
                        appendLine(out, formatCharacterCue(text, dual));
                    }
                } else if ("Parenthetical".equals(type)) {
                    if (!text.isBlank()) {
                        appendLine(out, formatParenthetical(text));
                    }
                } else if ("Dialogue".equals(type)) {
                    if (!text.isBlank()) {
                        appendMultiline(out, text);
                    }
                }
            }
        }
    }

    private static String formatCharacterCue(String text, boolean dual) {
        String cue = text.trim().toUpperCase(Locale.ROOT);
        if (!cue.startsWith("@")) {
            cue = "@" + cue;
        }
        if (dual && !cue.endsWith("^")) {
            cue = cue + " ^";
        }
        return cue;
    }

    private static String formatParenthetical(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            return trimmed;
        }
        return "(" + trimmed + ")";
    }

    private static String paragraphText(Element paragraph) {
        StringBuilder text = new StringBuilder();
        collectText(paragraph, text);
        return text.toString().replace("\r\n", "\n").replace('\r', '\n');
    }

    private static void collectText(Element parent, StringBuilder out) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;
            String tag = child.getTagName();
            if ("Text".equals(tag)) {
                String style = attr(child, "Style");
                String value = child.getTextContent() != null ? child.getTextContent() : "";
                out.append(applyEmphasis(value, style));
            } else if ("ScriptNote".equals(tag)
                    || "SceneProperties".equals(tag)
                    || "DualDialogue".equals(tag)
                    || "Tabstops".equals(tag)
                    || "DynamicLabel".equals(tag)) {
                // Skip nested metadata / dual-dialogue (handled separately)
            } else if ("Paragraph".equals(tag)) {
                // Nested paragraphs inside ScriptNote
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
                collectText(child, out);
            }
        }
    }

    private static String applyEmphasis(String text, String style) {
        if (text == null || text.isEmpty() || style == null || style.isBlank()) {
            return text == null ? "" : text;
        }
        String lower = style.toLowerCase(Locale.ROOT);
        boolean bold = lower.contains("bold");
        boolean italic = lower.contains("italic");
        boolean underline = lower.contains("underline");
        if (!bold && !italic && !underline) {
            return text;
        }
        String wrapped = text;
        if (bold && italic) {
            wrapped = "***" + wrapped + "***";
        } else if (bold) {
            wrapped = "**" + wrapped + "**";
        } else if (italic) {
            wrapped = "*" + wrapped + "*";
        }
        if (underline) {
            wrapped = "_" + wrapped + "_";
        }
        return wrapped;
    }

    private static Element firstChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static boolean hasChildElement(Element parent, String tagName) {
        return firstChildElement(parent, tagName) != null;
    }

    private static List<Element> childElements(Element parent, String tagName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static String attr(Element element, String name) {
        String value = element.getAttribute(name);
        return value != null ? value : "";
    }

    private static void ensureBlankLine(StringBuilder out) {
        if (out.isEmpty()) {
            return;
        }
        if (out.charAt(out.length() - 1) != '\n') {
            out.append('\n');
        }
        if (out.length() < 2 || out.charAt(out.length() - 2) != '\n') {
            out.append('\n');
        }
    }

    private static void appendLine(StringBuilder out, String line) {
        if (!out.isEmpty() && out.charAt(out.length() - 1) != '\n') {
            out.append('\n');
        }
        out.append(line).append('\n');
    }

    private static void appendMultiline(StringBuilder out, String text) {
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            appendLine(out, line);
        }
    }
}
