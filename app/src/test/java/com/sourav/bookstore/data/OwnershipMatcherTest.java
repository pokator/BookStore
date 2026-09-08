package com.sourav.bookstore.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure logic, no Android and no database, so this runs as a plain JUnit test (no Robolectric) --
 * same shape as {@code BookMetadataLookupServiceTest}.
 *
 * <p>The cases below are the real-world variations the fuzzy tier exists for (subtitles, articles,
 * reordered author names, other editions) paired with the near-misses it must <em>not</em> swallow
 * (different books by the same author, different authors of a same-named book).
 */
public class OwnershipMatcherTest {

    private static OwnershipMatcher.Match match(String candidateTitle, String candidateAuthor,
                                                String candidateIsbn, String storedTitle,
                                                String storedAuthor, String storedIsbn) {
        return OwnershipMatcher.match(candidateTitle, candidateAuthor, candidateIsbn,
                storedTitle, storedAuthor, storedIsbn);
    }

    @Test
    public void identicalIsbn_matchesOnIsbn() {
        OwnershipMatcher.Match result = match("Dune", "Frank Herbert", "9780441013593",
                "Dune", "Frank Herbert", "9780441013593");

        assertNotNull(result);
        assertEquals(OwnershipMatcher.MatchType.ISBN, result.type);
        assertEquals("Dune", result.title);
    }

    @Test
    public void isbn10AndIsbn13OfSameEdition_matchOnIsbn() {
        // 0441013597 is the ISBN-10 printing of 9780441013593.
        OwnershipMatcher.Match result = match("Dune", "Frank Herbert", "0441013597",
                "Totally Different Title", "Someone Else", "978-0-441-01359-3");

        assertNotNull(result);
        assertEquals(OwnershipMatcher.MatchType.ISBN, result.type);
    }

    @Test
    public void isbnMatch_survivesDashesAndSpaces() {
        assertEquals(OwnershipMatcher.normalizeIsbn("978-0-441-01359-3"),
                OwnershipMatcher.normalizeIsbn(" 9780441013593 "));
    }

    @Test
    public void normalizeIsbn_rejectsAnythingThatIsNotAnIsbn() {
        assertNull(OwnershipMatcher.normalizeIsbn(null));
        assertNull(OwnershipMatcher.normalizeIsbn(""));
        assertNull(OwnershipMatcher.normalizeIsbn("12345"));
        assertNull(OwnershipMatcher.normalizeIsbn("not an isbn"));
        // An X is only ever an ISBN-10 check digit, never mid-number.
        assertNull(OwnershipMatcher.normalizeIsbn("04410X3597"));
    }

    @Test
    public void bothIsbnsMissing_fallsBackToTitleAndAuthor() {
        OwnershipMatcher.Match result = match("Dune", "Frank Herbert", null,
                "Dune", "Frank Herbert", null);

        assertNotNull(result);
        assertEquals(OwnershipMatcher.MatchType.TITLE_AUTHOR, result.type);
    }

    @Test
    public void differentEditionOfSameBook_stillMatchesOnTitleAndAuthor() {
        OwnershipMatcher.Match result = match("Dune", "Frank Herbert", "9780593099322",
                "Dune", "Frank Herbert", "9780441013593");

        assertNotNull(result);
        assertEquals(OwnershipMatcher.MatchType.TITLE_AUTHOR, result.type);
    }

    @Test
    public void droppedSubtitle_matches() {
        OwnershipMatcher.Match result = match("Sapiens: A Brief History of Humankind",
                "Yuval Noah Harari", null, "Sapiens", "Harari, Yuval Noah", null);

        assertNotNull(result);
        assertEquals(OwnershipMatcher.MatchType.TITLE_AUTHOR, result.type);
    }

    @Test
    public void leadingArticleAndPunctuation_areIgnored() {
        OwnershipMatcher.Match result = match("The Hitchhiker's Guide to the Galaxy",
                "Douglas Adams", null, "Hitchhikers Guide to the Galaxy", "Adams, Douglas", null);

        assertNotNull(result);
        assertEquals(OwnershipMatcher.MatchType.TITLE_AUTHOR, result.type);
    }

    @Test
    public void reorderedAuthorName_matches() {
        OwnershipMatcher.Match result = match("Dune", "Frank Herbert", null,
                "Dune", "Herbert, Frank", null);

        assertNotNull(result);
    }

    @Test
    public void accentsAndCase_areIgnored() {
        OwnershipMatcher.Match result = match("LES MISERABLES", "Victor Hugo", null,
                // Built from a char code rather than a literal accent so the source stays ASCII:
                // javac here has no explicit -encoding and would otherwise read the byte as the
                // platform default charset.
                "Les Mis" + (char) 0xE9 + "rables", "Hugo, Victor", null);

        assertNotNull(result);
    }

    @Test
    public void smallTypoInTitle_stillMatches() {
        OwnershipMatcher.Match result = match("The Great Gatsby", "F. Scott Fitzgerald", null,
                "The Great Gatsy", "Fitzgerald, F. Scott", null);

        assertNotNull(result);
    }

    @Test
    public void differentBookBySameAuthor_doesNotMatch() {
        assertNull(match("Dune Messiah", "Frank Herbert", "9780593098233",
                "Dune", "Frank Herbert", "9780441013593"));
    }

    @Test
    public void sameTitleByDifferentAuthor_doesNotMatch() {
        assertNull(match("Ulysses", "James Joyce", null, "Ulysses", "Alfred Tennyson", null));
    }

    @Test
    public void sharedFirstNameAlone_isNotAnAuthorMatch() {
        assertFalse(OwnershipMatcher.authorsMatch("John Smith", "John Doe"));
    }

    @Test
    public void sharedSurname_isAnAuthorMatch() {
        assertTrue(OwnershipMatcher.authorsMatch("Frank Herbert", "Herbert"));
    }

    @Test
    public void punctuationOnlyAuthorDifference_isAnAuthorMatch() {
        assertTrue(OwnershipMatcher.authorsMatch("J.R.R. Tolkien", "JRR Tolkien"));
    }

    @Test
    public void oneAuthorAmongSeveral_isAnAuthorMatch() {
        assertTrue(OwnershipMatcher.authorsMatch("Terry Pratchett, Neil Gaiman",
                "Neil Gaiman and Terry Pratchett"));
    }

    @Test
    public void missingAuthorOnOneSide_requiresANearExactTitle() {
        assertNotNull(match("Dune", "", null, "Dune", "Frank Herbert", null));
        // Close enough to clear the with-author bar, but not the stricter title-only one.
        assertNull(match("Dune Messiah", "", null, "Dune Messiahs", "Frank Herbert", null));
    }

    @Test
    public void unrelatedBooks_doNotMatch() {
        assertNull(match("Dune", "Frank Herbert", "9780441013593",
                "The Hobbit", "J.R.R. Tolkien", "9780547928227"));
    }

    @Test
    public void normalizeText_stripsArticlesPunctuationAndCase() {
        assertEquals("hitchhikers guide", OwnershipMatcher.normalizeText("The Hitchhiker's Guide"));
        assertEquals("brief history of time", OwnershipMatcher.normalizeText("A Brief History of Time"));
        assertEquals("", OwnershipMatcher.normalizeText(null));
    }
}
