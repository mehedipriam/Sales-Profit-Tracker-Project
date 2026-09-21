package com.salestracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Boots the real app against a throwaway MySQL (Flyway migrations included).
 * One container is shared by every test class; tests isolate themselves by registering their own tenant.
 */
@SpringBootTest(properties = "app.jwt.secret=integration-test-secret-that-is-long-enough-0123456789")
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    static {
        MYSQL.start();
    }

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    /** A registered business owner; token is a ready-made Authorization header value. */
    public record Tenant(long id, String bearer) {}

    protected Tenant registerTenant() throws Exception {
        String body = """
                {"businessName":"Test Co","fullName":"Owner","email":"t%d-%d@example.com","password":"password123"}
                """.formatted(SEQ.incrementAndGet(), System.nanoTime());
        JsonNode res = call(MockMvcRequestBuilders.post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body), 201);
        return new Tenant(res.at("/user/tenantId").asLong(), "Bearer " + res.get("token").asText());
    }

    protected JsonNode get(Tenant t, String url, int expectedStatus) throws Exception {
        return call(MockMvcRequestBuilders.get(url).header("Authorization", t.bearer()), expectedStatus);
    }

    protected JsonNode post(Tenant t, String url, String body, int expectedStatus) throws Exception {
        return call(MockMvcRequestBuilders.post(url).header("Authorization", t.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body), expectedStatus);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        String text = result.getResponse().getContentAsString();
        if (result.getResponse().getStatus() != expectedStatus) {
            throw new AssertionError("Expected HTTP " + expectedStatus + " but got "
                    + result.getResponse().getStatus() + ": " + text);
        }
        return text.isBlank() ? json.createObjectNode() : json.readTree(text);
    }
}
