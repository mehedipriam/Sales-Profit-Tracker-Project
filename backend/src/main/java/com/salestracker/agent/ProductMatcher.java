package com.salestracker.agent;

import com.salestracker.agent.DraftOrder.MatchStatus;
import com.salestracker.product.Product;

import java.util.*;

/**
 * Matches a product name written in a message to the shop's products. The comparison ignores case, punctuation and
 * word order, and a trailing plural s, so "blue shirts" finds "Blue Shirt". It never guesses between two close
 * products: those come back as AMBIGUOUS for a person to choose.
 */
final class ProductMatcher {
    private ProductMatcher() {}

    /** At or above this a product counts as a match. */
    static final double MIN_MATCH = 0.5;
    /** Products below this are not even suggested. */
    static final double MIN_SUGGEST = 0.25;
    /** A runner-up this close to the best score makes the match ambiguous. */
    static final double AMBIGUITY_GAP = 0.15;
    private static final int MAX_CANDIDATES = 4;

    record Match(MatchStatus status, Product product, List<Product> candidates) {}

    static Match match(String name, List<Product> products) {
        Set<String> wanted = tokens(name);
        if (wanted.isEmpty()) return new Match(MatchStatus.NOT_FOUND, null, List.of());

        record Scored(Product product, double score) {}
        List<Scored> scored = new ArrayList<>();
        for (Product p : products) {
            double s = score(wanted, tokens(p.getName()));
            if (p.getSku() != null && tokens(p.getSku()).equals(wanted)) s = 1.0;
            if (s >= MIN_SUGGEST) scored.add(new Scored(p, s));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparing(s -> s.product().getName()));

        if (scored.isEmpty() || scored.get(0).score() < MIN_MATCH) {
            return new Match(MatchStatus.NOT_FOUND, null,
                    scored.stream().limit(MAX_CANDIDATES).map(Scored::product).toList());
        }
        double best = scored.get(0).score();
        List<Product> close = scored.stream().filter(s -> best - s.score() < AMBIGUITY_GAP || s.score() == best)
                .limit(MAX_CANDIDATES).map(Scored::product).toList();
        // Two products tied at a perfect score are duplicates by name: still not ours to pick.
        boolean ambiguous = close.size() > 1 && (best < 1.0 || scored.get(1).score() == 1.0);
        return ambiguous ? new Match(MatchStatus.AMBIGUOUS, null, close)
                         : new Match(MatchStatus.MATCHED, scored.get(0).product(), List.of());
    }

    /** Word overlap (Jaccard); a name that holds all the words of the other counts for at least 0.6. */
    static double score(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        if (inter.isEmpty()) return 0;
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        double jaccard = (double) inter.size() / union.size();
        boolean subset = inter.size() == a.size() || inter.size() == b.size();
        return subset ? Math.max(jaccard, 0.6) : jaccard;
    }

    /** Lower-case words (letters of any script, with their marks, and digits), a trailing plural s removed. */
    static Set<String> tokens(String s) {
        Set<String> out = new LinkedHashSet<>();
        if (s == null) return out;
        for (String w : s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{M}\\p{N}]+")) {
            if (w.isEmpty()) continue;
            out.add(w.length() > 3 && w.endsWith("s") ? w.substring(0, w.length() - 1) : w);
        }
        return out;
    }
}
