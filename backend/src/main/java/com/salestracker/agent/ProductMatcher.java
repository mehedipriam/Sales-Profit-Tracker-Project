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
        int shared = sharedWords(a, b);
        if (shared == 0) return 0;
        double jaccard = (double) shared / (a.size() + b.size() - shared);
        boolean subset = shared == a.size() || shared == b.size();
        return subset ? Math.max(jaccard, 0.6) : jaccard;
    }

    /** How many words the two names share: equal words first, then words one typo apart ("beanes" and "beans"). */
    private static int sharedWords(Set<String> a, Set<String> b) {
        Set<String> left = new LinkedHashSet<>(a);
        Set<String> right = new LinkedHashSet<>(b);
        left.removeIf(right::remove);
        int exact = a.size() - left.size();
        // The equal words are now gone from both sides; what is left may still be a typo apart.
        int typos = 0;
        for (String w : left) {
            String hit = right.stream().filter(v -> oneTypoApart(w, v)).findFirst().orElse(null);
            if (hit != null) {
                right.remove(hit);
                typos++;
            }
        }
        return exact + typos;
    }

    /**
     * One letter wrong, missing or extra, in words of at least four characters with no digits: typing mistakes in a
     * name. Words with digits are left exact, so 1000ml never matches 1500ml, and short words so red never matches bed.
     */
    static boolean oneTypoApart(String x, String y) {
        if (x.equals(y) || x.length() < 4 || y.length() < 4 || Math.abs(x.length() - y.length()) > 1) return false;
        if (x.chars().anyMatch(Character::isDigit) || y.chars().anyMatch(Character::isDigit)) return false;
        int i = 0;
        while (i < x.length() && i < y.length() && x.charAt(i) == y.charAt(i)) i++;
        if (x.length() == y.length()) return x.substring(i + 1).equals(y.substring(i + 1)); // one letter changed
        String longer = x.length() > y.length() ? x : y;
        String shorter = x.length() > y.length() ? y : x;
        return longer.substring(i + 1).equals(shorter.substring(i)); // one letter extra
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
