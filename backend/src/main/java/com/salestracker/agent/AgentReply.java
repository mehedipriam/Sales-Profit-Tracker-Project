package com.salestracker.agent;

/** The assistant's answer to a typed message: an order draft, an answer card, or a plain message. One of them is set. */
public record AgentReply(Type type, DraftOrder draft, Answer answer, String message) {

    public enum Type { DRAFT, ANSWER, MESSAGE }

    static AgentReply draft(DraftOrder d) { return new AgentReply(Type.DRAFT, d, null, null); }
    static AgentReply answer(Answer a) { return new AgentReply(Type.ANSWER, null, a, null); }
    static AgentReply message(String m) { return new AgentReply(Type.MESSAGE, null, null, m); }
}
