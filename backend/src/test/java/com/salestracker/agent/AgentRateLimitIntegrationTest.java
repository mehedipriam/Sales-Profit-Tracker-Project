package com.salestracker.agent;

import com.salestracker.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@TestPropertySource(properties = "app.agent.max-requests-per-minute=2")
class AgentRateLimitIntegrationTest extends AbstractIntegrationTest {

    @MockBean AiClient ai;

    @Test
    void aUserPastTheLimitIsStoppedBeforeTheAiIsCalledAndOthersAreNotAffected() throws Exception {
        when(ai.extractOrder(anyString())).thenReturn(new ExtractedOrder("Rahim", "01711222333", "Mirpur", null, null, null,
                List.of(new ExtractedOrder.Item("shirt", 1, new BigDecimal("900")))));
        Tenant busy = registerTenant();
        Tenant other = registerTenant();

        post(busy, "/api/agent/draft", "{\"text\":\"one\"}", 200);
        post(busy, "/api/agent/draft", "{\"text\":\"two\"}", 200);
        String message = post(busy, "/api/agent/draft", "{\"text\":\"three\"}", 429).get("message").asText();

        assertTrue(message.contains("too fast"));
        verify(ai, times(2)).extractOrder(anyString()); // the third never reached the AI
        post(other, "/api/agent/draft", "{\"text\":\"mine\"}", 200);
        // Confirming does not call the AI, so it is not limited; this one fails only because the draft is incomplete.
        post(busy, "/api/agent/confirm", "{}", 400);
    }
}
