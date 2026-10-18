package com.salestracker.agent;

/** The AI behind the order agent. Gemini today; another provider only needs another implementation of this. */
public interface AiClient {
    /** A picture to read, such as a screenshot of a Messenger conversation. mimeType is image/jpeg, image/png or image/webp. */
    record Image(String mimeType, byte[] bytes) {}

    /** Reads a pasted order message (Bengali, English or mixed) into structured details. Saves nothing. */
    ExtractedOrder extractOrder(String text);

    /** The same from a screenshot, with an optional note typed beside it (text may be null). */
    ExtractedOrder extractOrder(String text, Image image);
}
