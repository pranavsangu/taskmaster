package com.trustdesk.kb;

/**
 * Reads the header of a knowledge-base Markdown file:
 * <pre>
 * # Refund and Return Policy
 *
 * Doc ID: KB-REFUND-001
 * Audience: Customer support agents
 * Version: 2026.07
 * </pre>
 * The first occurrence of each header wins, and every header is required.
 */
public final class KbMarkdownParser {

    public record ParsedKbDocument(String docId, String title, String audience, String version,
                                   String content, String checksum) {
    }

    private KbMarkdownParser() {
    }

    public static ParsedKbDocument parse(String fileName, String content) {
        String title = null;
        String docId = null;
        String audience = null;
        String version = null;

        for (String rawLine : content.split("\\R")) {
            String line = rawLine.strip();
            if (title == null && line.startsWith("# ")) {
                title = line.substring(2).strip();
            } else if (docId == null && line.startsWith("Doc ID:")) {
                docId = valueAfterColon(line);
            } else if (audience == null && line.startsWith("Audience:")) {
                audience = valueAfterColon(line);
            } else if (version == null && line.startsWith("Version:")) {
                version = valueAfterColon(line);
            }
        }

        return new ParsedKbDocument(
                require(docId, "Doc ID:", fileName),
                require(title, "# <title>", fileName),
                require(audience, "Audience:", fileName),
                require(version, "Version:", fileName),
                content,
                Checksums.sha256(content));
    }

    private static String valueAfterColon(String line) {
        return line.substring(line.indexOf(':') + 1).strip();
    }

    private static String require(String value, String header, String fileName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fileName + " is missing the '" + header + "' line");
        }
        return value;
    }
}