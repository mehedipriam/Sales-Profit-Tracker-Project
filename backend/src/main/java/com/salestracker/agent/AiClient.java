package com.salestracker.agent;

/** The AI behind the order agent. Gemini today; another provider only needs another implementation of this. */
public interface AiClient {
    /** Reads a pasted order message (Bengali, English or mixed) into structured details. Saves nothing. */
    ExtractedOrder extractOrder(String text);
}
