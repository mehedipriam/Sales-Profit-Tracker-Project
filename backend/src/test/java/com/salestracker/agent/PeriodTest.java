package com.salestracker.agent;

import com.salestracker.agent.Intent.PeriodName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PeriodTest {
    private static Period of(PeriodName n, LocalDate today) {
        return Period.of(n, null, null, today);
    }

    private static void assertRange(Period p, String from, String to) {
        assertEquals(LocalDate.parse(from), p.from());
        assertEquals(LocalDate.parse(to), p.to());
    }

    @Test
    void todayAndYesterday() {
        LocalDate today = LocalDate.of(2026, 3, 1);
        assertRange(of(PeriodName.TODAY, today), "2026-03-01", "2026-03-01");
        assertRange(of(PeriodName.YESTERDAY, today), "2026-02-28", "2026-02-28");
    }

    @Test
    void weeksRunMondayToSunday() {
        LocalDate wednesday = LocalDate.of(2026, 10, 21);
        assertRange(of(PeriodName.THIS_WEEK, wednesday), "2026-10-19", "2026-10-25");
        assertRange(of(PeriodName.LAST_WEEK, wednesday), "2026-10-12", "2026-10-18");
        LocalDate monday = LocalDate.of(2026, 10, 19);
        assertRange(of(PeriodName.THIS_WEEK, monday), "2026-10-19", "2026-10-25");
        LocalDate sunday = LocalDate.of(2026, 10, 25);
        assertRange(of(PeriodName.THIS_WEEK, sunday), "2026-10-19", "2026-10-25");
    }

    @Test
    void monthsIncludingLeapDaysAndYearEnds() {
        assertRange(of(PeriodName.THIS_MONTH, LocalDate.of(2028, 2, 10)), "2028-02-01", "2028-02-29");
        assertRange(of(PeriodName.LAST_MONTH, LocalDate.of(2028, 3, 31)), "2028-02-01", "2028-02-29");
        assertRange(of(PeriodName.LAST_MONTH, LocalDate.of(2027, 3, 31)), "2027-02-01", "2027-02-28");
        assertRange(of(PeriodName.LAST_MONTH, LocalDate.of(2026, 1, 15)), "2025-12-01", "2025-12-31");
        assertRange(of(PeriodName.THIS_MONTH, LocalDate.of(2026, 10, 31)), "2026-10-01", "2026-10-31");
        assertRange(of(PeriodName.THIS_YEAR, LocalDate.of(2028, 6, 1)), "2028-01-01", "2028-12-31");
        assertEquals("This month (October 2026)", of(PeriodName.THIS_MONTH, LocalDate.of(2026, 10, 20)).label());
    }

    @Test
    void allTimeHasNoBounds() {
        Period p = of(PeriodName.ALL_TIME, LocalDate.of(2026, 10, 20));
        assertNull(p.from());
        assertNull(p.to());
        assertEquals(p, of(null, LocalDate.of(2026, 10, 20)));
    }

    @Test
    void twoDatesAreUsedAsGivenOrSwappedIfBackwards() {
        LocalDate a = LocalDate.of(2026, 9, 1), b = LocalDate.of(2026, 9, 15);
        assertRange(Period.of(PeriodName.CUSTOM, a, b, LocalDate.of(2026, 10, 20)), "2026-09-01", "2026-09-15");
        assertRange(Period.of(PeriodName.CUSTOM, b, a, LocalDate.of(2026, 10, 20)), "2026-09-01", "2026-09-15");
        assertRange(Period.of(PeriodName.CUSTOM, a, a, LocalDate.of(2026, 10, 20)), "2026-09-01", "2026-09-01");
        assertNull(Period.of(PeriodName.CUSTOM, null, null, LocalDate.of(2026, 10, 20)).from());
    }
}
