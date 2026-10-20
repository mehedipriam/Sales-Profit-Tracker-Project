package com.salestracker.agent;

import com.salestracker.agent.Answer.Column;
import com.salestracker.auth.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one entry point for typed messages. The AI first works out what is wanted (an order, a question, or neither),
 * then the app does it: an order becomes a draft, a question is answered from the database. The AI never supplies a
 * figure; it may only word one sentence from the figures the app calculated.
 */
@Service
public class AssistantService {
    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final String NOT_UNDERSTOOD = "I did not understand that. You can paste an order message, add, find or "
            + "change a customer, or ask about profit, best-selling products, top customers, stock, order counts or expenses.";
    private static final String BENGALI_DIGITS = "০১২৩৪৫৬৭৮৯";
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

    private final AiClient ai;
    private final DraftService drafts;
    private final QuestionService questions;
    private final CustomerAssistant customers;
    private final OrderStatusAssistant orderStatus;

    public AssistantService(AiClient ai, DraftService drafts, QuestionService questions, CustomerAssistant customers,
                            OrderStatusAssistant orderStatus) {
        this.orderStatus = orderStatus;
        this.customers = customers;
        this.ai = ai;
        this.drafts = drafts;
        this.questions = questions;
    }

    public AgentReply handle(AuthUser user, String text) {
        Intent intent = ai.understand(text, questions.today());
        if (intent.kind() == Intent.Kind.ORDER) return AgentReply.draft(drafts.prepare(user, text));
        if (intent.kind() == Intent.Kind.CUSTOMER_ADD || intent.kind() == Intent.Kind.CUSTOMER_FIND
                || intent.kind() == Intent.Kind.CUSTOMER_UPDATE) {
            return customers.handle(user, intent);
        }
        if (intent.kind() == Intent.Kind.ORDER_STATUS) return orderStatus.handle(user, intent);
        if (intent.kind() == Intent.Kind.QUESTION && intent.question() != null) {
            AgentReply reply = questions.answer(user, intent);
            return reply.type() == AgentReply.Type.ANSWER ? AgentReply.answer(worded(reply.answer(), text)) : reply;
        }
        return AgentReply.message(intent.reply() != null ? intent.reply() : NOT_UNDERSTOOD);
    }

    /**
     * The card with the AI's sentence in place of the app's plain one, when the AI wrote one that uses only figures from
     * the card. Any failure (limit used up, timeout, a number that is not on the card) keeps the plain sentence.
     */
    private Answer worded(Answer answer, String question) {
        String facts = facts(answer);
        try {
            String sentence = ai.wordAnswer(question, facts);
            if (sentence != null && usesOnly(sentence, facts)) return answer.withSentence(sentence.trim());
        } catch (RuntimeException e) {
            log.warn("The AI could not word an answer; using the plain sentence");
        }
        return answer;
    }

    /** Every figure on the card as plain text: the only material the AI may word a sentence from. */
    static String facts(Answer a) {
        StringBuilder sb = new StringBuilder(a.title()).append(" | ").append(a.period()).append('\n');
        a.stats().forEach(s -> sb.append(s.label()).append(": ").append(plain(s.value())).append('\n'));
        if (a.table() != null) {
            List<Column> cols = a.table().columns();
            a.table().rows().stream().limit(5).forEach(row -> {
                for (int i = 0; i < cols.size(); i++) sb.append(cols.get(i).label()).append(": ").append(plain(row.get(i))).append("; ");
                sb.append('\n');
            });
        }
        return sb.toString();
    }

    private static String plain(Object v) {
        return v instanceof BigDecimal b ? b.toPlainString() : String.valueOf(v);
    }

    /** True when every number in the sentence also appears in the facts (commas, Bengali digits and trailing zeros aside). */
    static boolean usesOnly(String sentence, String facts) {
        Set<String> allowed = new HashSet<>(numbers(facts));
        return allowed.containsAll(numbers(sentence));
    }

    private static List<String> numbers(String text) {
        StringBuilder ascii = new StringBuilder();
        for (char c : text.toCharArray()) {
            int bn = BENGALI_DIGITS.indexOf(c);
            ascii.append(bn >= 0 ? (char) ('0' + bn) : c);
        }
        List<String> out = new java.util.ArrayList<>();
        Matcher m = NUMBER.matcher(ascii);
        while (m.find()) {
            String n = m.group().replace(",", "");
            try {
                out.add(new BigDecimal(n).stripTrailingZeros().toPlainString());
            } catch (NumberFormatException e) {
                out.add(n);
            }
        }
        return out;
    }
}
