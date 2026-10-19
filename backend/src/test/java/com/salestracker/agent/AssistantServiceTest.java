package com.salestracker.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssistantServiceTest {
    private static final String FACTS = "Profit or loss | This month (October 2026)\nNet profit: 12500.50\nPaid orders: 3\n";

    @Test
    void aSentenceWithOnlyNumbersFromTheFactsIsAccepted() {
        assertTrue(AssistantService.usesOnly("Net profit is 12,500.5 from 3 orders in October 2026.", FACTS));
        assertTrue(AssistantService.usesOnly("নিট লাভ ১২,৫০০.৫ টাকা, ৩টি অর্ডার।", FACTS));
        assertTrue(AssistantService.usesOnly("No figures at all.", FACTS));
    }

    @Test
    void aNumberThatIsNotInTheFactsIsRejected() {
        assertFalse(AssistantService.usesOnly("Net profit is about 12,000.", FACTS));
        assertFalse(AssistantService.usesOnly("That is 5 orders.", FACTS));
        assertFalse(AssistantService.usesOnly("Up 15% on last month.", FACTS));
    }
}
