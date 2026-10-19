package com.salestracker.agent;

import com.salestracker.agent.Intent.PeriodName;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * A stretch of days a question is about, inclusive at both ends. A null bound means "no limit", which is how the
 * reports treat all time. Weeks run Monday to Sunday.
 */
public record Period(String label, LocalDate from, LocalDate to) {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    /** The period named, counted from today (in the shop's time zone). Custom dates fall back to all time if absent. */
    public static Period of(PeriodName name, LocalDate customFrom, LocalDate customTo, LocalDate today) {
        PeriodName n = name == null ? PeriodName.ALL_TIME : name;
        switch (n) {
            case TODAY:
                return new Period("Today", today, today);
            case YESTERDAY:
                return new Period("Yesterday", today.minusDays(1), today.minusDays(1));
            case THIS_WEEK: {
                LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                return new Period("This week", monday, monday.plusDays(6));
            }
            case LAST_WEEK: {
                LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
                return new Period("Last week", monday, monday.plusDays(6));
            }
            case THIS_MONTH:
                return month("This month", today);
            case LAST_MONTH:
                return month("Last month", today.minusMonths(1));
            case THIS_YEAR:
                return new Period("This year", LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31));
            case CUSTOM: {
                if (customFrom == null && customTo == null) break;
                LocalDate from = customFrom, to = customTo;
                if (from != null && to != null && from.isAfter(to)) { from = customTo; to = customFrom; }
                return new Period(range(from, to), from, to);
            }
            default:
                break;
        }
        return new Period("All time", null, null);
    }

    private static Period month(String label, LocalDate inMonth) {
        LocalDate first = inMonth.withDayOfMonth(1);
        return new Period(label + " (" + MONTH.format(first) + ")", first, first.with(TemporalAdjusters.lastDayOfMonth()));
    }

    private static String range(LocalDate from, LocalDate to) {
        if (from != null && to != null) return from.equals(to) ? DAY.format(from) : DAY.format(from) + " to " + DAY.format(to);
        return from != null ? "From " + DAY.format(from) : "Up to " + DAY.format(to);
    }
}
