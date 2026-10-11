package com.salestracker.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The signed-in user's own settings: name, login email, password, and (Owner only) the business name. */
class AccountIntegrationTest extends AbstractIntegrationTest {

    private Tenant owner;
    private String email;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerTenant();
        email = get(owner, "/api/auth/me", 200).get("email").asText();
    }

    private JsonNode login(String email, String password, int expectedStatus) throws Exception {
        return postAnonymous("/api/auth/login",
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password), expectedStatus);
    }

    private static String unique(String prefix) {
        return "%s%d@example.com".formatted(prefix, System.nanoTime());
    }

    @Test
    void renamingNeedsNoPassword() throws Exception {
        JsonNode res = put(owner, "/api/account/profile",
                "{\"fullName\":\"  New Name \",\"email\":\"%s\"}".formatted(email), 200);
        assertEquals("New Name", res.at("/user/fullName").asText());
        assertEquals("New Name", get(owner, "/api/auth/me", 200).get("fullName").asText());
    }

    @Test
    void changingTheEmailNeedsTheCurrentPasswordAndMovesTheLogin() throws Exception {
        String newEmail = unique("moved");
        put(owner, "/api/account/profile",
                "{\"fullName\":\"Owner\",\"email\":\"%s\"}".formatted(newEmail), 400);
        put(owner, "/api/account/profile",
                "{\"fullName\":\"Owner\",\"email\":\"%s\",\"currentPassword\":\"wrong-pass\"}".formatted(newEmail), 400);

        JsonNode res = put(owner, "/api/account/profile",
                "{\"fullName\":\"Owner\",\"email\":\"%s\",\"currentPassword\":\"password123\"}".formatted(newEmail.toUpperCase()), 200);
        assertEquals(newEmail, res.at("/user/email").asText());
        assertFalse(res.get("token").asText().isBlank(), "a fresh token that carries the new email");

        login(email, "password123", 401);
        login(newEmail, "password123", 200);
    }

    @Test
    void anEmailAlreadyInUseIsRejected() throws Exception {
        String taken = get(registerTenant(), "/api/auth/me", 200).get("email").asText();
        put(owner, "/api/account/profile",
                "{\"fullName\":\"Owner\",\"email\":\"%s\",\"currentPassword\":\"password123\"}".formatted(taken), 409);
    }

    @Test
    void changingThePasswordNeedsTheCurrentOne() throws Exception {
        put(owner, "/api/account/password",
                "{\"currentPassword\":\"wrong-pass\",\"newPassword\":\"brand-new-pass\"}", 400);
        put(owner, "/api/account/password",
                "{\"currentPassword\":\"password123\",\"newPassword\":\"short\"}", 400);

        put(owner, "/api/account/password",
                "{\"currentPassword\":\"password123\",\"newPassword\":\"brand-new-pass\"}", 204);
        login(email, "password123", 401);
        login(email, "brand-new-pass", 200);
    }

    @Test
    void verifyingThePasswordAcceptsOnlyTheUsersOwn() throws Exception {
        post(owner, "/api/account/verify-password", "{\"password\":\"password123\"}", 204);
        post(owner, "/api/account/verify-password", "{\"password\":\"wrong-pass\"}", 400);
        post(owner, "/api/account/verify-password", "{\"password\":\"\"}", 400);
    }

    @Test
    void aPinCanStandInForThePasswordOnceSetWithThePassword() throws Exception {
        assertFalse(get(owner, "/api/account/pin", 200).get("set").asBoolean());
        post(owner, "/api/account/verify-password", "{\"pin\":\"1234\"}", 400); // none set yet

        put(owner, "/api/account/pin", "{\"currentPassword\":\"wrong-pass\",\"pin\":\"1234\"}", 400);
        put(owner, "/api/account/pin", "{\"currentPassword\":\"password123\",\"pin\":\"12\"}", 400);
        put(owner, "/api/account/pin", "{\"currentPassword\":\"password123\",\"pin\":\"12ab\"}", 400);
        assertTrue(put(owner, "/api/account/pin", "{\"currentPassword\":\"password123\",\"pin\":\"1234\"}", 200)
                .get("set").asBoolean());

        post(owner, "/api/account/verify-password", "{\"pin\":\"1234\"}", 204);
        post(owner, "/api/account/verify-password", "{\"pin\":\"9999\"}", 400);
        post(owner, "/api/account/verify-password", "{\"password\":\"password123\",\"pin\":\"1234\"}", 400); // one, not both

        assertFalse(put(owner, "/api/account/pin", "{\"currentPassword\":\"password123\",\"pin\":null}", 200)
                .get("set").asBoolean());
        post(owner, "/api/account/verify-password", "{\"pin\":\"1234\"}", 400);
    }

    @Test
    void fiveWrongPinsLockThePinUntilThePasswordIsUsed() throws Exception {
        put(owner, "/api/account/pin", "{\"currentPassword\":\"password123\",\"pin\":\"482915\"}", 200);
        for (int i = 0; i < AccountService.PIN_TRIES; i++) {
            post(owner, "/api/account/verify-password", "{\"pin\":\"000000\"}", 400);
        }
        assertTrue(get(owner, "/api/account/pin", 200).get("locked").asBoolean());
        post(owner, "/api/account/verify-password", "{\"pin\":\"482915\"}", 400); // even the right one

        post(owner, "/api/account/verify-password", "{\"password\":\"password123\"}", 204);
        assertFalse(get(owner, "/api/account/pin", 200).get("locked").asBoolean());
        post(owner, "/api/account/verify-password", "{\"pin\":\"482915\"}", 204);
    }

    @Test
    void theOwnerRenamesTheBusinessButStaffCannot() throws Exception {
        assertEquals("BDT", get(owner, "/api/auth/me", 200).get("currency").asText()); // the default
        JsonNode info = put(owner, "/api/account/business", "{\"name\":\" Rina's Store \",\"currency\":\"usd\"}", 200);
        assertEquals("Rina's Store", info.get("businessName").asText());
        assertEquals("USD", info.get("currency").asText());
        assertEquals("Rina's Store", get(owner, "/api/auth/me", 200).get("businessName").asText());

        String staffEmail = unique("staff");
        post(owner, "/api/users",
                "{\"fullName\":\"Staffer\",\"email\":\"%s\",\"password\":\"password123\"}".formatted(staffEmail), 201);
        JsonNode staffLogin = login(staffEmail, "password123", 200);
        Tenant staff = new Tenant(staffLogin.at("/user/tenantId").asLong(), "Bearer " + staffLogin.get("token").asText());

        put(staff, "/api/account/business", "{\"name\":\"Hijacked\",\"currency\":\"EUR\"}", 403);
        assertEquals("USD", get(staff, "/api/auth/me", 200).get("currency").asText()); // staff see the store's currency
        // Staff still manage their own profile and password.
        put(staff, "/api/account/profile", "{\"fullName\":\"Rina\",\"email\":\"%s\"}".formatted(staffEmail), 200);
        put(staff, "/api/account/password",
                "{\"currentPassword\":\"password123\",\"newPassword\":\"staff-new-pass\"}", 204);
    }

    @Test
    void storeContactDetailsForInvoicesAreOptional() throws Exception {
        JsonNode me = get(owner, "/api/auth/me", 200);
        assertTrue(me.get("businessPhone").isNull());
        assertTrue(me.get("businessAddress").isNull());
        assertTrue(me.get("invoiceNote").isNull());

        JsonNode info = put(owner, "/api/account/business", """
                {"name":"Shop","currency":"BDT","phone":" 01711-000000 ","address":"House 5, Road 2, Dhaka",
                 "invoiceNote":"Thanks for shopping with us!"}
                """, 200);
        assertEquals("01711-000000", info.get("businessPhone").asText());
        assertEquals("House 5, Road 2, Dhaka", info.get("businessAddress").asText());
        assertEquals("Thanks for shopping with us!", get(owner, "/api/auth/me", 200).get("invoiceNote").asText());

        // Blank clears a detail.
        info = put(owner, "/api/account/business", "{\"name\":\"Shop\",\"currency\":\"BDT\",\"phone\":\"  \"}", 200);
        assertTrue(info.get("businessPhone").isNull());
        assertTrue(info.get("businessAddress").isNull());
    }

    @Test
    void anUnknownCurrencyIsRejected() throws Exception {
        put(owner, "/api/account/business", "{\"name\":\"Shop\",\"currency\":\"ABC\"}", 400);
        put(owner, "/api/account/business", "{\"name\":\"Shop\",\"currency\":\"DOLLARS\"}", 400);
        assertEquals("BDT", get(owner, "/api/auth/me", 200).get("currency").asText());
    }

    @Test
    void settingsNeedASignedInUser() throws Exception {
        Tenant anonymous = new Tenant(0, "Bearer not-a-token");
        put(anonymous, "/api/account/password",
                "{\"currentPassword\":\"password123\",\"newPassword\":\"brand-new-pass\"}", 401);
    }
}
