package com.salestracker.agent;

import com.salestracker.agent.DraftOrder.MatchStatus;
import com.salestracker.product.Product;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductMatcherTest {

    private static Product product(long id, String name, String sku) {
        Product p = new Product(1L);
        p.apply(name, sku, null, BigDecimal.TEN, BigDecimal.TEN, null, null);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private final List<Product> catalog = List.of(
            product(1, "Blue Shirt", "BS-1"),
            product(2, "Red Shirt", null),
            product(3, "Rice 5kg", null),
            product(4, "Rice 25kg", null));

    @Test
    void ignoresCaseOrderPunctuationAndPlurals() {
        assertEquals(1L, ProductMatcher.match("blue shirts", catalog).product().getId());
        assertEquals(1L, ProductMatcher.match("Shirt, BLUE", catalog).product().getId());
    }

    @Test
    void matchesBySku() {
        ProductMatcher.Match m = ProductMatcher.match("bs-1", catalog);
        assertEquals(MatchStatus.MATCHED, m.status());
        assertEquals(1L, m.product().getId());
    }

    @Test
    void aVagueNameThatFitsTwoProductsIsAmbiguous() {
        ProductMatcher.Match m = ProductMatcher.match("shirt", catalog);
        assertEquals(MatchStatus.AMBIGUOUS, m.status());
        assertNull(m.product());
        assertEquals(List.of(1L, 2L), m.candidates().stream().map(Product::getId).toList());
    }

    @Test
    void numbersInANameKeepSimilarProductsApart() {
        assertEquals(4L, ProductMatcher.match("rice 25kg", catalog).product().getId());
        assertEquals(3L, ProductMatcher.match("Rice 5kg", catalog).product().getId());
    }

    @Test
    void unknownProductsAreNotFoundButMayBeSuggested() {
        ProductMatcher.Match none = ProductMatcher.match("laptop", catalog);
        assertEquals(MatchStatus.NOT_FOUND, none.status());
        assertTrue(none.candidates().isEmpty());

        ProductMatcher.Match weak = ProductMatcher.match("shirt xl", catalog);
        assertEquals(MatchStatus.NOT_FOUND, weak.status());
        assertFalse(weak.candidates().isEmpty());
    }

    @Test
    void duplicateProductNamesAreNotGuessed() {
        List<Product> twins = List.of(product(1, "Rice", null), product(2, "Rice", null));
        assertEquals(MatchStatus.AMBIGUOUS, ProductMatcher.match("rice", twins).status());
    }

    @Test
    void worksWithBengaliNames() {
        List<Product> bn = List.of(product(1, "নীল শার্ট", null), product(2, "লাল শার্ট", null));
        assertEquals(1L, ProductMatcher.match("নীল শার্ট", bn).product().getId());
        assertEquals(MatchStatus.AMBIGUOUS, ProductMatcher.match("শার্ট", bn).status());
    }

    @Test
    void emptyNamesMatchNothing() {
        assertEquals(MatchStatus.NOT_FOUND, ProductMatcher.match("  ", catalog).status());
        assertEquals(MatchStatus.NOT_FOUND, ProductMatcher.match(null, catalog).status());
    }

    @Test
    void phoneSuffixIgnoresFormatting() {
        assertEquals("1711222333", DraftService.phoneSuffix("+880 1711-222333"));
        assertEquals("1711222333", DraftService.phoneSuffix("01711222333"));
        assertNull(DraftService.phoneSuffix("12345"));
        assertNull(DraftService.phoneSuffix(null));
    }
}
