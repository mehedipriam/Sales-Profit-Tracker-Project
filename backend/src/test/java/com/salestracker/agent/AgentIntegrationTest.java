package com.salestracker.agent;

import com.salestracker.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentIntegrationTest extends AbstractIntegrationTest {

    @Test
    void extractNeedsALoginAndAMessage() throws Exception {
        assertEquals(401, mvc.perform(MockMvcRequestBuilders.post("/api/agent/extract")
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hi\"}")).andReturn().getResponse().getStatus());

        Tenant owner = registerTenant();
        post(owner, "/api/agent/extract", "{\"text\":\"  \"}", 400);
    }

    @Test
    void withoutAKeyTheOwnerIsToldTheAgentIsNotSetUp() throws Exception {
        Tenant owner = registerTenant();
        post(owner, "/api/agent/extract", "{\"text\":\"Rahim 01711222333 2 shirts\"}", 503);
    }
}
