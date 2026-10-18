package com.salestracker.agent;

import com.salestracker.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The assistant endpoints with the real Gemini client and no API key configured, as on a fresh install. */
class AgentIntegrationTest extends AbstractIntegrationTest {

    @Test
    void everyAssistantEndpointNeedsALogin() throws Exception {
        for (String url : new String[]{"/api/agent/draft", "/api/agent/revise", "/api/agent/confirm"}) {
            assertEquals(401, mvc.perform(MockMvcRequestBuilders.post(url)
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus(), url);
        }
    }

    @Test
    void theOldTestEndpointIsGone() throws Exception {
        Tenant owner = registerTenant();
        int status = mvc.perform(MockMvcRequestBuilders.post("/api/agent/extract").header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hi\"}")).andReturn().getResponse().getStatus();
        assertEquals(404, status);
    }

    @Test
    void aBlankMessageIsRejected() throws Exception {
        Tenant owner = registerTenant();
        post(owner, "/api/agent/draft", "{\"text\":\"  \"}", 400);
    }

    @Test
    void withoutAKeyTheUserIsToldTheAssistantIsNotSetUp() throws Exception {
        Tenant owner = registerTenant();
        post(owner, "/api/agent/draft", "{\"text\":\"Rahim 01711222333 2 shirts\"}", 503);
    }
}
