package com.sourav.bookstore.data;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a book that was just scanned/looked up is one the user already has stored.
 *
 * <p>A plain equality check on title+author (what {@link DBManager#existenceCheck} does, to block
 * literal duplicate rows) is far too strict for this: the same book comes back from the metadata
 * sources with a different edition's ISBN, with or without its subtitle, with "The" on the front,
 * with the author written "Herbert, Frank" instead of "Frank Herbert". So matching here is
 * deliberately fuzzy, in two tiers:
 *
 * <ul>
 *   <li>{@link MatchType#ISBN} -- both sides normalize to the same ISBN-13 (ISBN-10s are
 *       converted, so the 10- and 13-digit printings of one edition match each other). Treated
 *       as certain.</li>
 *   <li>{@link MatchType#TITLE_AUTHOR} -- titles are near-identical after normalization and the
 *       authors share a surname. A likely, not certain, match, so the UI phrases it as such.</li>
 * </ul>
 *
 * <p>Two <em>different</em> ISBNs do not rule a match out: a different edition of a book the user
 * genuinely owns is exactly the case the title/author tier exists to catch.
 *
 * <p>Everything here is pure static logic over Strings -- no Android or database types -- so
 * {@code OwnershipMatcherTest} covers it with plain JUnit, no Robolectric needed.
 */
public final class OwnershipMatcher {

    public enum MatchType {
        /** Same ISBN after normalization to ISBN-13: certainly the same book. */
        ISBN,
        /** Near-identical title plus an overlapping author surname: probably the same book. */
        TITLE_AUTHOR
    }

    /**
     * How close two normalized titles must be (1.0 = identical) to match when both sides also
     * name an author. Set high on purpose: the author check is only a surname overlap, so the
     * title is what actually carries the match.
     */
    static final double TITLE_SIMILARITY_THRESHOLD = 0.85;

    /**
     * The stricter title bar used when one side has no author at all, so nothing corroborates the
     * title.
     */
    static final double TITLE_ONLY_SIMILARITY_THRESHOLD = 0.95;

    /** Fallback for authors that share no surname token, e.g. "J.R.R. Tolkien"/"JRR Tolkien". */
    static final double AUTHOR_SIMILARITY_THRESHOLD = 0.85;

    /** Author-field noise that must never count as a shared surname. */
    private static final Set<String> AUTHOR_STOP_WORDS = new HashSet<>();

    static {
        AUTHOR_STOP_WORDS.add("and");
        AUTHOR_STOP_WORDS.add("the");
        AUTHOR_STOP_WORDS.add("jr");
        AUTHOR_STOP_WORDS.add("sr");
        AUTHOR_STOP_WORDS.add("ed");
        AUTHOR_STOP_WORDS.add("eds");
        AUTHOR_STOP_WORDS.add("editor");
        AUTHOR_STOP_WORDS.add("editors");
        AUTHOR_STOP_WORDS.add("trans");
        AUTHOR_STOP_WORDS.add("translated");
        AUTHOR_STOP_WORDS.add("by");
    }

    private OwnershipMatcher() {
    }

    /** A stored book the candidate appears to be, plus how confident that is. */
    public static final class Match {
        public final MatchType type;
        public final String title;
        public final String author;

        Match(MatchType type, String title, String author) {
            this.type = type;
            this.title = title;
            this.author = author;
        }
    }

    /**
     * @return how the candidate matches this one stored book, or {@code null} if it doesn't.
     */
    public static Match match(String candidateTitle, String candidateAuthor, String candidateIsbn,
                              String storedTitle, String storedAuthor, String storedIsbn) {
        String candidateKey = normalizeIsbn(candidateIsbn);
        String storedKey = normalizeIsbn(storedIsbn);
        if (candidateKey != null && candidateKey.equals(storedKey)) {
            return new Match(MatchType.ISBN, storedTitle, storedAuthor);
        }

        double titleSimilarity = titleSimilarity(candidateTitle, storedTitle);
        boolean haveBothAuthors = !normalizeText(candidateAuthor).isEmpty()
                && !normalizeText(storedAuthor).isEmpty();

        if (haveBothAuthors) {
            if (titleSimilarity >= TITLE_SIMILARITY_THRESHOLD
                    && authorsMatch(candidateAuthor, storedAuthor)) {
                return new Match(MatchType.TITLE_AUTHOR, storedTitle, storedAuthor);
            }
        } else if (titleSimilarity >= TITLE_ONLY_SIMILARITY_THRESHOLD) {
            return new Match(MatchType.TITLE_AUTHOR, storedTitle, storedAuthor);
        }
        return null;
    }

    /**
     * Compares titles both in full and with any subtitle (everything after the first colon)
     * dropped, taking whichever reads as the closer match -- "Dune" and "Dune: A Novel" are the
     * same book, but so are "Sapiens: A Brief History of Humankind" and its full self.
     */
    static double titleSimilarity(String candidateTitle, String storedTitle) {
        double full = similarity(normalizeText(candidateTitle), normalizeText(storedTitle));
        double main = similarity(normalizeText(mainTitle(candidateTitle)),
                normalizeText(mainTitle(storedTitle)));
        return Math.max(full, main);
    }

    /** Everything before the first colon, when that leaves something substantial behind. */
    static String mainTitle(String title) {
        if (title == null) {
            return "";
        }
        int colon = title.indexOf(':');
        if (colon < 0) {
            return title;
        }
        String head = title.substring(0, colon);
        return normalizeText(head).length() >= 3 ? head : title;
    }

    /**
     * Authors match when they share a surname -- a token that is the last name on at least one
     * side ("Frank Herbert"/"Herbert") -- or when two tokens overlap regardless of order
     * ("Frank Herbert"/"Herbert, Frank"). One shared token that is nobody's surname is not
     * enough, so "John Smith" and "John Doe" stay distinct. Purely punctuational differences are
     * caught by the similarity fallback ("J.R.R. Tolkien"/"JRR Tolkien").
     */
    static boolean authorsMatch(String candidateAuthor, String storedAuthor) {
        Set<String> candidateTokens = authorTokens(candidateAuthor);
        Set<String> storedTokens = authorTokens(storedAuthor);
        if (candidateTokens.isEmpty() || storedTokens.isEmpty()) {
            return false;
        }

        Set<String> shared = new HashSet<>(candidateTokens);
        shared.retainAll(storedTokens);
        if (shared.size() >= 2) {
            return true;
        }
        if (!shared.isEmpty() && (shared.contains(lastToken(candidateAuthor))
                || shared.contains(lastToken(storedAuthor)))) {
            return true;
        }
        return similarity(normalizeText(candidateAuthor), normalizeText(storedAuthor))
                >= AUTHOR_SIMILARITY_THRESHOLD;
    }

    private static Set<String> authorTokens(String author) {
        Set<String> tokens = new HashSet<>();
        for (String token : normalizeText(author).split(" ")) {
            // Length 3 skips initials ("j", "rr"), which would otherwise collide across authors.
            if (token.length() >= 3 && !AUTHOR_STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * The last usable token of the <em>first</em> listed author -- the surname in "Frank Herbert"
     * and, because the comma splits it too, in "Herbert, Frank" as well.
     */
    private static String lastToken(String author) {
        if (author == null) {
            return "";
        }
        String firstAuthor = author.split("[,&;]|\\band\\b")[0];
        String last = "";
        for (String token : normalizeText(firstAuthor).split(" ")) {
            if (token.length() >= 3 && !AUTHOR_STOP_WORDS.contains(token)) {
                last = token;
            }
        }
        return last;
    }

    /**
     * Strips accents, case, punctuation and a leading article, leaving space-separated words --
     * so "The Hitchhiker's Guide" and "Hitchhikers Guide" normalize to the same thing.
     */
    static String normalizeText(String raw) {
        if (raw == null) {
            return "";
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.US)
                // Apostrophes are deleted rather than turned into a space by the rule below, so
                // "Hitchhiker's" and "Hitchhikers" become the same single word. Written as escapes
                // (curly quote, modifier apostrophe) so the source stays pure ASCII and does not
                // depend on javac's platform default encoding.
                .replaceAll("['\u2019\u02BC]", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (text.startsWith("the ")) {
            text = text.substring(4);
        } else if (text.startsWith("an ")) {
            text = text.substring(3);
        } else if (text.startsWith("a ")) {
            text = text.substring(2);
        }
        return text.trim();
    }

    /**
     * Reduces an ISBN to its ISBN-13 form so the 10- and 13-digit printings of one edition compare
     * equal. Returns {@code null} for anything that is not a well-formed 10- or 13-digit ISBN
     * (including absent/empty ones), which callers read as "no ISBN evidence either way" rather
     * than as a mismatch.
     */
    public static String normalizeIsbn(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (c == 'x' || c == 'X') {
                digits.append('X');
            }
        }
        String isbn = digits.toString();
        // An X is only ever an ISBN-10's check digit; anywhere else it is junk, not an ISBN.
        if (isbn.indexOf('X') >= 0 && isbn.indexOf('X') != isbn.length() - 1) {
            return null;
        }
        if (isbn.length() == 13 && isbn.indexOf('X') < 0) {
            return isbn;
        }
        if (isbn.length() == 10) {
            return isbn10To13(isbn);
        }
        return null;
    }

    private static String isbn10To13(String isbn10) {
        String body = "978" + isbn10.substring(0, 9);
        int sum = 0;
        for (int i = 0; i < body.length(); i++) {
            int digit = body.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return body + ((10 - (sum % 10)) % 10);
    }

    /** 1.0 for identical strings, scaling down to 0.0 by edit distance over the longer length. */
    static double similarity(String a, String b) {
        int longest = Math.max(a.length(), b.length());
        if (longest == 0) {
            return 1.0;
        }
        return 1.0 - ((double) levenshtein(a, b)) / longest;
    }

    static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
