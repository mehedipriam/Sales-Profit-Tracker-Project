package com.salestracker.agent;

import com.salestracker.agent.CustomerReplies.CustomerView;
import com.salestracker.agent.CustomerReplies.Proposal;

/**
 * The assistant's answer to a typed message. One of draft (an order to check), answer (a card of figures), customer (a
 * customer's details), proposal (a change waiting for Confirm) or message (plain text) is set, as type says.
 */
public record AgentReply(Type type, DraftOrder draft, Answer answer, String message, CustomerView customer, Proposal proposal) {

    public enum Type { DRAFT, ANSWER, MESSAGE, CUSTOMER, PROPOSAL }

    static AgentReply draft(DraftOrder d) { return new AgentReply(Type.DRAFT, d, null, null, null, null); }
    static AgentReply answer(Answer a) { return new AgentReply(Type.ANSWER, null, a, null, null, null); }
    static AgentReply message(String m) { return new AgentReply(Type.MESSAGE, null, null, m, null, null); }
    static AgentReply customer(CustomerView c) { return new AgentReply(Type.CUSTOMER, null, null, null, c, null); }
    static AgentReply proposal(Proposal p) { return new AgentReply(Type.PROPOSAL, null, null, null, null, p); }
}
