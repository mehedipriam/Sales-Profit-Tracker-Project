package com.salestracker.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registerLoginAndMeAllCarryTheBusinessName() throws Exception {
        String email = "auth-" + System.nanoTime() + "@example.com";
        JsonNode registered = postAnonymous("/api/auth/register", """
                {"businessName":"  Acme Groceries ","fullName":"Owner","email":"%s","password":"password123"}
                """.formatted(email), 201);
        assertEquals("Acme Groceries", registered.at("/user/businessName").asText(), "trimmed on the way in");
        assertEquals("OWNER", registered.at("/user/role").asText());

        JsonNode login = postAnonymous("/api/auth/login",
                "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email), 200);
        assertEquals("Acme Groceries", login.at("/user/businessName").asText());

        Tenant t = new Tenant(login.at("/user/tenantId").asLong(), "Bearer " + login.get("token").asText());
        assertEquals("Acme Groceries", get(t, "/api/auth/me", 200).get("businessName").asText());
    }

    @Test
    void wrongPasswordAndDuplicateEmailAreRejected() throws Exception {
        String email = "dup-" + System.nanoTime() + "@example.com";
        String body = """
                {"businessName":"X","fullName":"Y","email":"%s","password":"password123"}
                """.formatted(email);
        postAnonymous("/api/auth/register", body, 201);
        postAnonymous("/api/auth/register", body.replace("password123", "password123"), 409);
        postAnonymous("/api/auth/login", "{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(email), 401);
        get(new Tenant(0, "Bearer not-a-token"), "/api/auth/me", 401);
    }
}
