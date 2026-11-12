package com.trustdesk.kb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Calls the real search and ingest endpoints against the seeded database.
 * Documents created by these tests use IDs starting with KB-TEST- and are deleted afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "trustdesk.demo-token=test-token")
class DocumentControllerTest {

    private static final String AUTH = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KbDocumentRepository kbDocumentRepository;

    @AfterEach
    void deleteTestDocuments() {
        kbDocumentRepository.deleteAllById(List.of("KB-TEST-001", "KB-TEST-002"));
    }

    @Test
    void searchRanksRefundPolicyFirstForDamagedItem() throws Exception {
        mockMvc.perform(get("/documents/search").param("q", "damaged item replacement").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("damaged item replacement"))
                .andExpect(jsonPath("$.results[0].doc_id").value("KB-REFUND-001"))
                .andExpect(jsonPath("$.results[0].title").value("Refund and Return Policy"))
                .andExpect(jsonPath("$.results[0].snippet", containsString("damaged")));
    }

    @Test
    void searchFindsShippingPolicyForStaleTracking() throws Exception {
        mockMvc.perform(get("/documents/search").param("q", "tracking not moving").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].doc_id").value("KB-SHIPPING-001"));
    }

    @Test
    void searchRespectsLimit() throws Exception {
        mockMvc.perform(get("/documents/search").param("q", "refund replacement coupon policy")
                        .param("limit", "2").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2));
    }

    @Test
    void searchWithOnlyCommonWordsReturnsNoResults() throws Exception {
        mockMvc.perform(get("/documents/search").param("q", "the a of").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", empty()));
    }

    @Test
    void searchRejectsBlankMissingAndOutOfRangeInput() throws Exception {
        mockMvc.perform(get("/documents/search").param("q", "  ").header("Authorization", AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        mockMvc.perform(get("/documents/search").header("Authorization", AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'q'"));
        mockMvc.perform(get("/documents/search").param("q", "refund").param("limit", "0").header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/documents/search").param("q", "refund").param("limit", "abc").header("Authorization", AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'limit'"));
    }

    @Test
    void searchRequiresToken() throws Exception {
        mockMvc.perform(get("/documents/search").param("q", "refund"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingestedDocumentKeepsItsIdAndBecomesSearchable() throws Exception {
        String body = """
                {"documents": [{
                  "doc_id": "KB-TEST-001",
                  "title": "Gift Wrapping Policy",
                  "content": "Gift wrapping is available for orders shipped within India.",
                  "source_path": "tests/gift_wrapping.md"
                }]}
                """;

        mockMvc.perform(post("/documents/ingest").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingested").value(1))
                .andExpect(jsonPath("$.document_ids[0]").value("KB-TEST-001"));

        mockMvc.perform(get("/documents/search").param("q", "gift wrapping").header("Authorization", AUTH))
                .andExpect(jsonPath("$.results[0].doc_id").value("KB-TEST-001"));

        KbDocument stored = kbDocumentRepository.findById("KB-TEST-001").orElseThrow();
        assertThat(stored.getAudience()).isEqualTo("unspecified");
        assertThat(stored.getChecksum()).hasSize(64);
    }

    @Test
    void ingestingSameIdTwiceUpdatesInsteadOfDuplicating() throws Exception {
        long before = kbDocumentRepository.count();
        String template = """
                {"documents": [{"doc_id": "KB-TEST-002", "title": "%s", "content": "Version text %s",
                                "source_path": "tests/repeat.md"}]}
                """;

        for (String version : List.of("first", "second")) {
            mockMvc.perform(post("/documents/ingest").contentType(MediaType.APPLICATION_JSON)
                            .content(template.formatted(version, version)).header("Authorization", AUTH))
                    .andExpect(status().isOk());
        }

        assertThat(kbDocumentRepository.count()).isEqualTo(before + 1);
        assertThat(kbDocumentRepository.findById("KB-TEST-002").orElseThrow().getTitle()).isEqualTo("second");
    }

    @Test
    void ingestRejectsInvalidDocuments() throws Exception {
        mockMvc.perform(post("/documents/ingest").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documents\": [{\"doc_id\": \"bad id!\", \"title\": \"\", \"content\": \"x\"}]}")
                        .header("Authorization", AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"))
                .andExpect(jsonPath("$.message", containsString("doc_id may only contain")))
                .andExpect(jsonPath("$.message", containsString("title is required")))
                .andExpect(jsonPath("$.message", containsString("source_path is required")));

        mockMvc.perform(post("/documents/ingest").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documents\": []}").header("Authorization", AUTH))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/documents/ingest").contentType(MediaType.APPLICATION_JSON)
                        .content("not json").header("Authorization", AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or is not valid JSON"));
    }

    @Test
    void ingestRejectsDuplicateIdsInOneRequest() throws Exception {
        String doc = "{\"doc_id\": \"KB-TEST-001\", \"title\": \"T\", \"content\": \"C\", \"source_path\": \"p.md\"}";
        mockMvc.perform(post("/documents/ingest").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documents\": [" + doc + "," + doc + "]}").header("Authorization", AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Duplicate doc_id in request: KB-TEST-001"));

        assertThat(kbDocumentRepository.existsById("KB-TEST-001")).isFalse();
    }

    @Test
    void seedDocumentIdsArePreservedExactly() throws Exception {
        mockMvc.perform(get("/documents/search").param("q", "policy").param("limit", "20").header("Authorization", AUTH))
                .andExpect(jsonPath("$.results[*].doc_id", hasItem("KB-REFUND-001")))
                .andExpect(jsonPath("$.results[*].doc_id", hasItem("KB-ADVERSARIAL-001")));
    }
}
