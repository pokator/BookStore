package com.sourav.bookstore.data;

import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Runs against a real SQLite database via Robolectric, so it exercises {@link DatabaseHelper}'s
 * schema and {@link DBManager}'s queries without needing a device/emulator.
 */
@RunWith(RobolectricTestRunner.class)
public class DBManagerTest {

    private DBManager dbManager;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        // DatabaseHelper is a process-wide singleton, so wipe any rows left by a previous test
        // in this class before each test runs.
        DatabaseHelper.getInstance(context).getWritableDatabase()
                .delete(DatabaseHelper.TABLE_NAME, null, null);

        dbManager = new DBManager(context);
        dbManager.open();
    }

    @After
    public void tearDown() {
        dbManager.close();
    }

    private long idOf(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper._ID));
    }

    @Test
    public void fetch_returnsNoRows_whenDatabaseIsEmpty() {
        Cursor cursor = dbManager.fetch();

        assertEquals(0, cursor.getCount());
        cursor.close();
    }

    @Test
    public void insert_thenFetch_returnsTheInsertedBook() {
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");

        Cursor cursor = dbManager.fetch();

        assertEquals(1, cursor.getCount());
        assertEquals("Dune", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.TITLE)));
        assertEquals("Frank Herbert", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.AUTHOR)));
        assertEquals("9780441013593", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.ISBN)));
        cursor.close();
    }

    @Test
    public void existenceCheck_isTrueForAnExistingTitleAndAuthor_falseOtherwise() {
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");

        assertTrue(dbManager.existenceCheck("Dune", "Frank Herbert"));
        assertFalse(dbManager.existenceCheck("Dune Messiah", "Frank Herbert"));
    }

    @Test
    public void update_changesTheStoredFields() {
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");
        long id = idOf(dbManager.fetch());

        int rowsUpdated = dbManager.update(id, "Dune Messiah", "Frank Herbert", "9780441104819");

        assertEquals(1, rowsUpdated);
        Cursor cursor = dbManager.fetch();
        assertEquals("Dune Messiah", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.TITLE)));
        assertEquals("9780441104819", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.ISBN)));
        cursor.close();
    }

    @Test
    public void delete_removesTheRow() {
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");
        long id = idOf(dbManager.fetch());

        dbManager.delete(id);

        Cursor cursor = dbManager.fetch();
        assertEquals(0, cursor.getCount());
        cursor.close();
    }

    @Test
    public void search_matchesByTitle() {
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");
        dbManager.insert("Neuromancer", "William Gibson", "9780441569595");

        Cursor cursor = dbManager.search("Dune", DatabaseHelper.TITLE, true);

        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Dune", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.TITLE)));
        cursor.close();
    }

    @Test
    public void search_matchesByAuthor() {
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");
        dbManager.insert("Dune Messiah", "Frank Herbert", "9780441104819");
        dbManager.insert("Neuromancer", "William Gibson", "9780441569595");

        Cursor cursor = dbManager.search("Herbert", DatabaseHelper.TITLE, true);

        assertEquals(2, cursor.getCount());
        cursor.close();
    }

    @Test
    public void search_matchesByIsbn() {
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");
        dbManager.insert("Neuromancer", "William Gibson", "9780441569595");

        Cursor cursor = dbManager.search("9780441013593", DatabaseHelper.TITLE, true);

        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        assertEquals("Dune", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.TITLE)));
        cursor.close();
    }

    @Test
    public void search_withEmptyKeyword_returnsEveryRow() {
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");
        dbManager.insert("Neuromancer", "William Gibson", "9780441569595");

        Cursor cursor = dbManager.search("", DatabaseHelper.TITLE, true);

        assertEquals(2, cursor.getCount());
        cursor.close();
    }

    @Test
    public void search_returnsNoRows_whenNothingMatches() {
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");

        Cursor cursor = dbManager.search("Tolkien", DatabaseHelper.TITLE, true);

        assertEquals(0, cursor.getCount());
        cursor.close();
    }

    @Test
    public void search_sortsByTitleAscendingAndDescending() {
        dbManager.insert("Neuromancer", "William Gibson", "9780441569595");
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");

        Cursor ascending = dbManager.search("", DatabaseHelper.TITLE, true);
        ascending.moveToFirst();
        assertEquals("Dune", ascending.getString(ascending.getColumnIndexOrThrow(DatabaseHelper.TITLE)));
        ascending.close();

        Cursor descending = dbManager.search("", DatabaseHelper.TITLE, false);
        descending.moveToFirst();
        assertEquals("Neuromancer", descending.getString(descending.getColumnIndexOrThrow(DatabaseHelper.TITLE)));
        descending.close();
    }

    @Test
    public void search_sortsByAuthorAscendingAndDescending() {
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");
        dbManager.insert("Neuromancer", "William Gibson", "9780441569595");

        Cursor ascending = dbManager.search("", DatabaseHelper.AUTHOR, true);
        ascending.moveToFirst();
        assertEquals("Frank Herbert", ascending.getString(ascending.getColumnIndexOrThrow(DatabaseHelper.AUTHOR)));
        ascending.close();

        Cursor descending = dbManager.search("", DatabaseHelper.AUTHOR, false);
        descending.moveToFirst();
        assertEquals("William Gibson", descending.getString(descending.getColumnIndexOrThrow(DatabaseHelper.AUTHOR)));
        descending.close();
    }

    @Test
    public void search_sortsByDateAddedNewestAndOldest() {
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");
        dbManager.insert("Neuromancer", "William Gibson", "9780441569595");

        Cursor newestFirst = dbManager.search("", DatabaseHelper._ID, false);
        newestFirst.moveToFirst();
        assertEquals("Neuromancer", newestFirst.getString(newestFirst.getColumnIndexOrThrow(DatabaseHelper.TITLE)));
        newestFirst.close();

        Cursor oldestFirst = dbManager.search("", DatabaseHelper._ID, true);
        oldestFirst.moveToFirst();
        assertEquals("Dune", oldestFirst.getString(oldestFirst.getColumnIndexOrThrow(DatabaseHelper.TITLE)));
        oldestFirst.close();
    }

    // findOwnedMatch: the matching rules themselves live in OwnershipMatcherTest; these cover the
    // scan-the-table-and-pick-a-winner part.

    @Test
    public void findOwnedMatch_returnsNull_whenDatabaseIsEmpty() {
        assertNull(dbManager.findOwnedMatch("Dune", "Frank Herbert", "9780441013593"));
    }

    @Test
    public void findOwnedMatch_returnsNull_whenNothingStoredResembles() {
        dbManager.insert("Neuromancer", "William Gibson", "9780441569595");

        assertNull(dbManager.findOwnedMatch("Dune", "Frank Herbert", "9780441013593"));
    }

    @Test
    public void findOwnedMatch_findsTheStoredCopy_byIsbn() {
        dbManager.insert("Neuromancer", "William Gibson", "9780441569595");
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");

        OwnershipMatcher.Match match = dbManager.findOwnedMatch("Dune", "Frank Herbert", "9780441013593");

        assertNotNull(match);
        assertEquals(OwnershipMatcher.MatchType.ISBN, match.type);
        assertEquals("Dune", match.title);
    }

    @Test
    public void findOwnedMatch_findsADifferentEdition_byTitleAndAuthor() {
        dbManager.insert("Dune", "Herbert, Frank", "9780441013593");

        OwnershipMatcher.Match match = dbManager.findOwnedMatch("Dune: A Novel", "Frank Herbert",
                "9780593099322");

        assertNotNull(match);
        assertEquals(OwnershipMatcher.MatchType.TITLE_AUTHOR, match.type);
        assertEquals("Dune", match.title);
    }

    @Test
    public void findOwnedMatch_prefersTheIsbnMatch_overAnEarlierFuzzyOne() {
        // Same book stored twice: an untitled-ISBN fuzzy candidate first, the exact ISBN second.
        dbManager.insert("Dune", "Frank Herbert", null);
        dbManager.insert("Dune (Deluxe Edition)", "Frank Herbert", "9780441013593");

        OwnershipMatcher.Match match = dbManager.findOwnedMatch("Dune", "Frank Herbert", "9780441013593");

        assertNotNull(match);
        assertEquals(OwnershipMatcher.MatchType.ISBN, match.type);
        assertEquals("Dune (Deluxe Edition)", match.title);
    }

    @Test
    public void findOwnedMatch_toleratesRowsWithNoIsbn() {
        dbManager.insert("Dune", "Frank Herbert", null);

        assertNotNull(dbManager.findOwnedMatch("Dune", "Frank Herbert", "9780441013593"));
    }
}
