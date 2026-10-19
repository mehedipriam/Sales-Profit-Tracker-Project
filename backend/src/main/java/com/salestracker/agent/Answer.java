package com.salestracker.agent;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the assistant shows for a question: labelled numbers, optionally a small table, and one sentence. Every figure
 * comes from the app's own reports; the AI only words the sentence, and only from these figures.
 */
public record Answer(String title, String period, List<Stat> stats, Table table, String sentence) {

    public enum Kind { MONEY, COUNT, PERCENT, TEXT }

    /** good and bad colour the number green or red; neither is plain. */
    public enum Tone { GOOD, BAD, NEUTRAL }

    public record Stat(String label, Object value, Kind kind, Tone tone) {
        static Stat money(String label, BigDecimal value, Tone tone) { return new Stat(label, value, Kind.MONEY, tone); }
        static Stat money(String label, BigDecimal value) { return new Stat(label, value, Kind.MONEY, Tone.NEUTRAL); }
        static Stat count(String label, long value) { return new Stat(label, value, Kind.COUNT, Tone.NEUTRAL); }
    }

    public record Column(String label, Kind kind) {}

    /** Each row has one cell per column: a string, or a number matching the column's kind. */
    public record Table(List<Column> columns, List<List<Object>> rows) {}

    Answer withSentence(String s) { return new Answer(title, period, stats, table, s); }
}
