package com.scripty.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The EPUB 3 container plumbing shared by the screenplay and song exporters: the OCF zip layout,
 * the package document, the nav TOC, and XHTML escaping. Callers supply only what differs — the
 * metadata, the stylesheet, and the spine documents.
 */
final class EpubPackage {

    static final String CONTENT_TYPE = "application/epub+zip";

    private static final String OEBPS = "OEBPS/";

    private EpubPackage() {
    }

    /** One spine document: its manifest id, filename, TOC label, and rendered {@code <body>} contents. */
    record Document(String id, String href, String navLabel, String body) {
    }

    /**
     * @param identifierSeed a stable string naming what was exported; the book's uuid is derived
     *                       from it, so re-exporting the same thing keeps the same identifier
     */
    record Metadata(String title, String author, String identifierSeed) {
    }

    static byte[] zip(Metadata metadata, String css, List<Document> documents) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            // The mimetype entry must come first and be stored uncompressed (EPUB OCF spec).
            writeStored(zip, "mimetype", CONTENT_TYPE.getBytes(StandardCharsets.US_ASCII));
            write(zip, "META-INF/container.xml", containerXml());
            write(zip, OEBPS + "style.css", css);
            for (Document document : documents) {
                write(zip, OEBPS + document.href(), xhtmlDocument(document.navLabel(), document.body()));
            }
            write(zip, OEBPS + "nav.xhtml", xhtmlDocument(metadata.title(), navBody(documents)));
            write(zip, OEBPS + "content.opf", contentOpf(metadata, documents));
        }
        return out.toByteArray();
    }

    static String xhtmlDocument(String title, String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\" "
                + "xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"en\" lang=\"en\">\n"
                + "  <head>\n"
                + "    <title>" + escape(title) + "</title>\n"
                + "    <link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\"/>\n"
                + "  </head>\n"
                + "  <body>\n"
                + body
                + "  </body>\n"
                + "</html>\n";
    }

    /** Escapes text and preserves hard line breaks within a paragraph. */
    static String inlineText(String content) {
        String[] lines = content.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append("<br/>");
            }
            sb.append(escape(lines[i]));
        }
        return sb.toString();
    }

    static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * The author to credit from a project's {@code writers} field, which may lead with a credit
     * line ("Written by") rather than a name.
     *
     * @return the first line that names someone, or null if there is nothing to credit
     */
    static String authorFromWriters(String writers) {
        if (!notBlank(writers)) {
            return null;
        }
        String[] lines = writers.trim().split("\n", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.toLowerCase(Locale.ROOT).matches(
                    "^(?:written(?: and directed)? by|story by|screenplay by|by)\\.?$")) {
                return trimmed;
            }
        }
        return lines[0].trim();
    }

    private static String navBody(List<Document> documents) {
        StringBuilder body = new StringBuilder();
        body.append("    <nav epub:type=\"toc\" id=\"toc\">\n");
        body.append("      <h1>Contents</h1>\n");
        body.append("      <ol>\n");
        for (Document document : documents) {
            body.append("        <li><a href=\"").append(document.href()).append("\">")
                    .append(escape(document.navLabel())).append("</a></li>\n");
        }
        body.append("      </ol>\n");
        body.append("    </nav>\n");
        return body.toString();
    }

    private static String contentOpf(Metadata metadata, List<Document> documents) {
        String modified = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        String identifier = "urn:uuid:" + UUID.nameUUIDFromBytes(
                metadata.identifierSeed().getBytes(StandardCharsets.UTF_8));

        StringBuilder opf = new StringBuilder();
        opf.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        opf.append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" ")
                .append("unique-identifier=\"book-id\" xml:lang=\"en\">\n");
        opf.append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n");
        opf.append("    <dc:identifier id=\"book-id\">").append(identifier).append("</dc:identifier>\n");
        opf.append("    <dc:title>").append(escape(metadata.title())).append("</dc:title>\n");
        opf.append("    <dc:language>en</dc:language>\n");
        if (notBlank(metadata.author())) {
            opf.append("    <dc:creator>").append(escape(metadata.author())).append("</dc:creator>\n");
        }
        opf.append("    <meta property=\"dcterms:modified\">").append(modified).append("</meta>\n");
        opf.append("  </metadata>\n");

        opf.append("  <manifest>\n");
        opf.append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" ")
                .append("properties=\"nav\"/>\n");
        opf.append("    <item id=\"style\" href=\"style.css\" media-type=\"text/css\"/>\n");
        for (Document document : documents) {
            opf.append("    <item id=\"").append(document.id()).append("\" href=\"")
                    .append(document.href()).append("\" media-type=\"application/xhtml+xml\"/>\n");
        }
        opf.append("  </manifest>\n");

        opf.append("  <spine>\n");
        for (Document document : documents) {
            opf.append("    <itemref idref=\"").append(document.id()).append("\"/>\n");
        }
        opf.append("  </spine>\n");
        opf.append("</package>\n");
        return opf.toString();
    }

    private static String containerXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<container version=\"1.0\" "
                + "xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n"
                + "  <rootfiles>\n"
                + "    <rootfile full-path=\"OEBPS/content.opf\" "
                + "media-type=\"application/oebps-package+xml\"/>\n"
                + "  </rootfiles>\n"
                + "</container>\n";
    }

    private static void write(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void writeStored(ZipOutputStream zip, String name, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(content.length);
        entry.setCompressedSize(content.length);
        CRC32 crc = new CRC32();
        crc.update(content);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
        // Subsequent entries return to the default deflate method.
        zip.setMethod(ZipOutputStream.DEFLATED);
    }
}
