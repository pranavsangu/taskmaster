package com.trustdesk.ticket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Calls the real endpoints against the seeded database (the seed loader runs on startup).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "trustdesk.demo-token=test-token")
class TicketControllerTest {

    private static final String AUTH = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void rejectsRequestWithWrongToken() throws Exception {
        mockMvc.perform(get("/tickets").header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid demo token."));
    }

    @Test
    void listsAllSeedTickets() throws Exception {
        mockMvc.perform(get("/tickets").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].ticket_id").value("tkt_9001"))
                .andExpect(jsonPath("$[0].customer_name").value("Aisha Rao"))
                .andExpect(jsonPath("$[0].order_id").value("ord_5001"));
    }

    @Test
    void returnsTicketWithCustomerAndOrderContext() throws Exception {
        mockMvc.perform(get("/tickets/tkt_9001").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Received damaged earbuds"))
                .andExpect(jsonPath("$.created_at").value("2026-06-28T10:15:00+05:30"))
                .andExpect(jsonPath("$.customer.customer_id").value("cus_1001"))
                .andExpect(jsonPath("$.customer.tier").value("gold"))
                .andExpect(jsonPath("$.customer.verified").value(true))
                .andExpect(jsonPath("$.order.order_id").value("ord_5001"))
                .andExpect(jsonPath("$.order.delivered_at").value("2026-06-24"))
                .andExpect(jsonPath("$.order.items[0].sku").value("BG-AIRPODS-01"))
                .andExpect(jsonPath("$.order.items[0].final_sale").value(false));
    }

    @Test
    void neverExposesEvaluationLabels() throws Exception {
        mockMvc.perform(get("/tickets").header("Authorization", AUTH))
                .andExpect(content().string(not(containsString("expected_"))));
        mockMvc.perform(get("/tickets/tkt_9001").header("Authorization", AUTH))
                .andExpect(content().string(not(containsString("expected_"))));
    }

    @Test
    void unknownTicketReturns404() throws Exception {
        mockMvc.perform(get("/tickets/tkt_0000").header("Authorization", AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"))
                .andExpect(jsonPath("$.message").value("Ticket tkt_0000 not found"));
    }
}