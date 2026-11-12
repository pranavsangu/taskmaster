package com.trustdesk.kb;

import com.trustdesk.kb.KbMarkdownParser.ParsedKbDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KbMarkdownParserTest {

    private static final String REFUND_POLICY = """
            # Refund and Return Policy

            Doc ID: KB-REFUND-001
            Audience: Customer support agents
            Version: 2026.07

            ## Physical Products

            Physical products may be returned within 7 calendar days of delivery.
            """;

    @Test
    void readsHeaderFieldsExactly() {
        ParsedKbDocument doc = KbMarkdownParser.parse("refund_policy.md", REFUND_POLICY);

        assertThat(doc.docId()).isEqualTo("KB-REFUND-001");
        assertThat(doc.title()).isEqualTo("Refund and Return Policy");
        assertThat(doc.audience()).isEqualTo("Customer support agents");
        assertThat(doc.version()).isEqualTo("2026.07");
        assertThat(doc.content()).isEqualTo(REFUND_POLICY);
    }

    @Test
    void checksumIsStableAndChangesWithContent() {
        String first = KbMarkdownParser.parse("a.md", REFUND_POLICY).checksum();
        String again = KbMarkdownParser.parse("a.md", REFUND_POLICY).checksum();
        String edited = KbMarkdownParser.parse("a.md", REFUND_POLICY + "extra").checksum();

        assertThat(first).hasSize(64).isEqualTo(again).isNotEqualTo(edited);
    }

    @Test
    void firstDocIdWinsWhenTextLaterRepeatsTheHeader() {
        String content = REFUND_POLICY + "\nDoc ID: KB-FAKE-999\n";

        assertThat(KbMarkdownParser.parse("a.md", content).docId()).isEqualTo("KB-REFUND-001");
    }

    @Test
    void missingDocIdIsRejected() {
        String content = REFUND_POLICY.replace("Doc ID: KB-REFUND-001", "");

        assertThatThrownBy(() -> KbMarkdownParser.parse("broken.md", content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("broken.md")
                .hasMessageContaining("Doc ID:");
    }
}
