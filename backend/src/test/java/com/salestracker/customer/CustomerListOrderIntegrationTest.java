package com.salestracker.customer;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerListOrderIntegrationTest extends AbstractIntegrationTest {

    private static String names(JsonNode page) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode c : page.get("content")) sb.append(sb.length() == 0 ? "" : ",").append(c.get("name").asText());
        return sb.toString();
    }

    @Test
    void theListIsAlphabeticalByDefaultAndInOrderOfArrivalOnRequest() throws Exception {
        Tenant t = registerTenant();
        for (String n : new String[]{"Zahid", "Hasan", "Priam", "Amy"}) {
            post(t, "/api/customers", "{\"name\":\"" + n + "\"}", 201);
        }

        assertEquals("Amy,Hasan,Priam,Zahid", names(get(t, "/api/customers", 200)));                    // pickers keep A-Z
        assertEquals("Zahid,Hasan,Priam,Amy", names(get(t, "/api/customers?sort=created", 200)));         // first come, first listed
        assertEquals("Amy,Hasan,Priam,Zahid", names(get(t, "/api/customers?sort=anything-else", 200)));   // unknown value: the default
    }

    @Test
    void arrivalOrderKeepsWorkingWithSearchAndPaging() throws Exception {
        Tenant t = registerTenant();
        for (String n : new String[]{"Zed Khan", "Bob", "Amy Khan", "Cat"}) {
            post(t, "/api/customers", "{\"name\":\"" + n + "\"}", 201);
        }

        assertEquals("Zed Khan,Amy Khan", names(get(t, "/api/customers?sort=created&q=khan", 200)));
        assertEquals("Zed Khan,Bob", names(get(t, "/api/customers?sort=created&size=2", 200)));
        assertEquals("Amy Khan,Cat", names(get(t, "/api/customers?sort=created&size=2&page=1", 200)));
    }
}
