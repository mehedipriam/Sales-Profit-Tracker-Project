package com.salestracker.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.salestracker.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportExportIntegrationTest extends AbstractIntegrationTest {

    private static final String HEADER = "\"Order date\",\"Order #\",\"Platform\",\"Customer\",\"Status\","
            + "\"Product\",\"Quantity\",\"Unit sold price\",\"Unit cost\",\"Revenue\",\"Cost\",\"Profit\",\"Delivery charge\"";

    private Tenant tenant;
    private long facebook;
    private long daraz;
    private long buyer;
    private long evil;
    private long rice;
    private long oil;
    private long order1; // Sep 1  PAID     Facebook Buyer: Rice x2 @150
    private long order2; // Sep 2  PENDING  Daraz    Evil : Rice x1 @90
    private long order3; // Aug 30 PAID     Daraz    Buyer: Rice x1 @120 and Oil x3 @40, customer paid 60 delivery

    @BeforeEach
    void seed() throws Exception {
        tenant = registerTenant();
        for (JsonNode p : get(tenant, "/api/platforms", 200)) {
            if (p.get("name").asText().equals("Facebook Page")) facebook = p.get("id").asLong();
            if (p.get("name").asText().equals("Daraz")) daraz = p.get("id").asLong();
        }
        buyer = customer("Buyer");
        evil = customer("=HYPERLINK(\\\"http://evil.example\\\")");
        rice = product("Rice, Premium \\\"A\\\"", 100); // a comma and quotes: must be escaped
        oil = product("Oil", 50);

        order1 = order(facebook, buyer, "PAID", "2026-09-01T10:00", item(rice, 2, 150));
        order2 = order(daraz, evil, "PENDING", "2026-09-02T11:00", item(rice, 1, 90));
        order3 = order(daraz, buyer, "PAID", "2026-08-30T09:00", item(rice, 1, 120), item(oil, 3, 40));
        put(tenant, "/api/orders/" + order3, """
                {"platformId":%d,"customerId":%d,"status":"PAID","orderedAt":"2026-08-30T09:00","deliveryCharge":60,
                 "items":[%s,%s]}
                """.formatted(daraz, buyer, item(rice, 1, 120), item(oil, 3, 40)), 200);
    }

    private long customer(String jsonName) throws Exception {
        return post(tenant, "/api/customers", "{\"name\":\"" + jsonName + "\"}", 201).get("id").asLong();
    }

    private long product(String jsonName, int cost) throws Exception {
        return post(tenant, "/api/products",
                "{\"name\":\"%s\",\"costPrice\":%d,\"sellingPrice\":%d}".formatted(jsonName, cost, cost * 2), 201)
                .get("id").asLong();
    }

    private static String item(long productId, int quantity, int soldPrice) {
        return "{\"productId\":%d,\"quantity\":%d,\"soldPrice\":%d}".formatted(productId, quantity, soldPrice);
    }

    private long order(long platformId, long customerId, String status, String orderedAt, String... items) throws Exception {
        return post(tenant, "/api/orders", """
                {"platformId":%d,"customerId":%d,"status":"%s","orderedAt":"%s","items":[%s]}
                """.formatted(platformId, customerId, status, orderedAt, String.join(",", items)), 201).get("id").asLong();
    }

    /** Body without the BOM, split into lines (the file uses CRLF). */
    private List<String> csvLines(Tenant t, String query) throws Exception {
        MvcResult r = rawGet(t, "/api/reports/export.csv" + query);
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        String body = new String(r.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(body.startsWith("﻿"), "UTF-8 BOM so Excel reads the taka sign and Bangla names");
        assertTrue(body.endsWith("\r\n"));
        return List.of(body.substring(1).split("\r\n"));
    }

    @Test
    void downloadHasCsvHeadersAndOneRowPerOrderLineOldestFirst() throws Exception {
        MvcResult r = rawGet(tenant, "/api/reports/export.csv");
        assertEquals(200, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentType().startsWith("text/csv"));
        assertTrue(r.getResponse().getHeader("Content-Disposition").startsWith("attachment"));
        assertEquals("no-store", r.getResponse().getHeader("Cache-Control"));

        List<String> lines = csvLines(tenant, "");
        assertEquals(HEADER, lines.get(0));
        assertEquals(5, lines.size(), "header + 2 lines (Aug 30) + Sep 1 + Sep 2");

        assertEquals("\"2026-08-30 09:00:00\",%d,\"Daraz\",\"Buyer\",\"PAID\",\"Rice, Premium \"\"A\"\"\",1,120.00,100.00,120.00,100.00,20.00,60.00"
                .formatted(order3), lines.get(1));
        assertEquals("\"2026-08-30 09:00:00\",%d,\"Daraz\",\"Buyer\",\"PAID\",\"Oil\",3,40.00,50.00,120.00,150.00,-30.00,"
                .formatted(order3), lines.get(2));
        assertEquals("\"2026-09-01 10:00:00\",%d,\"Facebook Page\",\"Buyer\",\"PAID\",\"Rice, Premium \"\"A\"\"\",2,150.00,100.00,300.00,200.00,100.00,0.00"
                .formatted(order1), lines.get(3));
        assertTrue(lines.get(4).contains("\"PENDING\""), "unpaid orders are exported too, marked by Status");
        assertTrue(lines.get(4).endsWith(",1,90.00,100.00,90.00,100.00,-10.00,0.00"));
    }

    @Test
    void dateAndPlatformFiltersApply() throws Exception {
        List<String> september = csvLines(tenant, "?from=2026-09-01&to=2026-09-30");
        assertEquals(3, september.size(), "header + Sep 1 + Sep 2");

        List<String> daraz = csvLines(tenant, "?platformId=" + this.daraz);
        assertEquals(4, daraz.size(), "header + 2 lines of Aug 30 + Sep 2");

        List<String> darazSep = csvLines(tenant, "?from=2026-09-01&to=2026-09-30&platformId=" + this.daraz);
        assertEquals(2, darazSep.size());
        assertTrue(darazSep.get(1).contains("\"PENDING\""));

        assertEquals(1, csvLines(tenant, "?from=2030-01-01&to=2030-12-31").size(), "an empty range still yields the header");
    }

    @Test
    void formulaTriggersInNamesAreNeutralised() throws Exception {
        String pending = csvLines(tenant, "?from=2026-09-02&to=2026-09-02").get(1);
        assertTrue(pending.contains("\"'=HYPERLINK(\"\"http://evil.example\"\")\""),
                "a customer name starting with = must not reach Excel as a live formula: " + pending);
        assertFalse(pending.contains(",\"=HYPERLINK"));
        assertEquals("\"'=1+1\"", ReportExportService.text("=1+1"));
        assertEquals("\"'+cmd\"", ReportExportService.text("+cmd"));
        assertEquals("\"'-2\"", ReportExportService.text("-2"));
        assertEquals("\"'@SUM(A1)\"", ReportExportService.text("@SUM(A1)"));
        assertEquals("\"plain\"", ReportExportService.text("plain"));
        assertEquals("\"a,b\"\"c\"", ReportExportService.text("a,b\"c"));
        assertEquals("\"\"", ReportExportService.text(null));
    }

    @Test
    void largeExportsStreamAcrossPagesWithoutLosingOrDuplicatingRows() throws Exception {
        // page size is 2 in tests: 7 more single-line orders means several page boundaries
        for (int i = 1; i <= 7; i++) {
            order(facebook, buyer, "PAID", "2026-10-0%dT12:00".formatted(i), item(oil, i, 60));
        }
        List<String> lines = csvLines(tenant, "");
        assertEquals(1 + 4 + 7, lines.size());
        List<String> dates = lines.stream().skip(1).map(l -> l.substring(1, 20)).toList();
        assertEquals(dates.stream().sorted().toList(), dates, "still oldest first across pages");
        assertEquals(dates.size(), dates.stream().distinct().count() + 1, "only the two-line Aug 30 order repeats a timestamp");
    }

    @Test
    void otherBusinessesGetOnlyTheirOwnDataAndBadInputIsRejected() throws Exception {
        Tenant other = registerTenant();
        assertEquals(List.of(HEADER), csvLines(other, ""));

        MvcResult foreignPlatform = rawGet(other, "/api/reports/export.csv?platformId=" + daraz);
        assertEquals(400, foreignPlatform.getResponse().getStatus());
        assertFalse(foreignPlatform.getResponse().getContentType().startsWith("text/csv"),
                "an error must not masquerade as a CSV download");

        assertEquals(400, rawGet(tenant, "/api/reports/export.csv?from=2026-09-30&to=2026-09-01").getResponse().getStatus());
        assertEquals(400, rawGet(tenant, "/api/reports/export.csv?from=nope").getResponse().getStatus());
        assertEquals(401, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/reports/export.csv")).andReturn().getResponse().getStatus());
    }
}
