package com.salestracker.agent;

import java.time.LocalDate;

/** The AI behind the order agent. Gemini today; another provider only needs another implementation of this. */
public interface AiClient {
    /** A picture to read, such as a screenshot of a Messenger conversation. mimeType is image/jpeg, image/png or image/webp. */
    record Image(String mimeType, byte[] bytes) {}

    /** Reads a pasted order message (Bengali, English or mixed) into structured details. Saves nothing. */
    ExtractedOrder extractOrder(String text);

    /** The same from a screenshot, with an optional note typed beside it (text may be null). */
    ExtractedOrder extractOrder(String text, Image image);

    /**
     * Understands an instruction about a draft ("make it 3", "remove the red shirt"). draftSummary is the current draft
     * as JSON with numbered lines. Returns only the changes asked for; saves nothing and changes nothing itself.
     */
    DraftRevision reviseDraft(String draftSummary, String instruction);

    /**
     * Works out what a typed message wants: an order, a question about the business (which one, and for which period),
     * or neither. today is the shop's current date, so "last month" and "this week" can be told apart. Only chooses;
     * never calculates anything and saves nothing.
     */
    Intent understand(String text, LocalDate today);

    /**
     * One sentence, in the language of the question, that words the given figures. facts holds every figure the
     * sentence may use. Returns null when nothing usable came back.
     */
    String wordAnswer(String question, String facts);
}
