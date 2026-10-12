package com.sourav.bookstore.network;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Drives {@link BookMetadataLookupService#resolveMetadata} through its whole fallback chain
 * against canned responses (see {@link FakeHttpFetcher}/{@link Fixtures}) -- which source is
 * asked, in what order, and what a failure at each step does to the outcome. Robolectric only
 * because the Google Books parser uses Android's org.json, which the plain JVM stubs out.
 */
@RunWith(RobolectricTestRunner.class)
public class BookMetadataLookupChainTest {

    static final String OPEN_LIBRARY = "openlibrary.org/api/books";
    static final String GOOGLE = "googleapis.com/books/v1/volumes?q=isbn:";
    static final String LIBRARY_THING = "/thingISBN/";
    static final String LOC = "bath.isbn=";

    private static final String GATSBY = "9780743273565";
    private static final String CATCHER = "9780316769488";
    // The first two related ISBNs LibraryThing lists for CATCHER (see librarything_catcher.xml).
    private static final String CATCHER_RELATED_1 = "0316769177";
    private static final String CATCHER_RELATED_2 = "0316769487";

    private BookMetadataLookupService.MetadataResult resolve(FakeHttpFetcher http, String isbn) {
        return new BookMetadataLookupService(http).resolveMetadata(isbn);
    }

    @Test
    public void openLibraryHit_isReturnedWithoutAskingAnyOtherSource() {
        FakeHttpFetcher http = new FakeHttpFetcher()
                .respond(OPEN_LIBRARY, Fixtures.read("openlibrary_catcher.json"));

        BookMetadataLookupService.MetadataResult result = resolve(http, CATCHER);

        assertEquals(BookMetadataLookupService.Source.OPEN_LIBRARY, result.source);
        assertEquals("The Catcher in the Rye", result.metadata.title);
        assertEquals(CATCHER, result.resolvedIsbn);
        assertEquals(1, http.requests.size());
    }

    @Test
    public void openLibraryMiss_fallsBackToGoogle_withoutAskingAnyOtherSource() {
        FakeHttpFetcher http = new FakeHttpFetcher()
                .respond(OPEN_LIBRARY, Fixtures.read("openlibrary_miss.json"))
                .respond(GOOGLE + GATSBY, Fixtures.read("google_hit.json"));

        BookMetadataLookupService.MetadataResult result = resolve(http, GATSBY);

        assertEquals(BookMetadataLookupService.Source.GOOGLE_BOOKS, result.source);
        assertEquals("F. Scott Fitzgerald: The Great Gatsby", result.metadata.title);
        assertEquals("F. Scott Fitzgerald", result.metadata.author);
        assertTrue(result.metadata.thumbnailUrl.startsWith("http://books.google.com/books/content"));
        assertEquals(GATSBY, result.resolvedIsbn);
        assertEquals(2, http.requests.size());
        assertTrue(http.requests.get(0).contains(OPEN_LIBRARY));
        assertTrue(http.requests.get(1).contains(GOOGLE + GATSBY));
    }

    @Test
    public void googleMiss_fallsBackToARelatedEditionFromLibraryThing_resolvedViaLoc() {
        FakeHttpFetcher http = new FakeHttpFetcher()
                .respond(OPEN_LIBRARY, Fixtures.read("openlibrary_miss.json"))
                .respond(GOOGLE, Fixtures.read("google_miss.json"))
                .respond(LIBRARY_THING + CATCHER, Fixtures.read("librarything_catcher.xml"))
                .respond(LOC + CATCHER_RELATED_1, Fixtures.read("loc_gatsby.xml"))
                .respond(LOC, Fixtures.read("loc_miss.xml"));

        BookMetadataLookupService.MetadataResult result = resolve(http, CATCHER);

        assertEquals(BookMetadataLookupService.Source.LIBRARY_OF_CONGRESS, result.source);
        assertEquals(CATCHER_RELATED_1, result.resolvedIsbn);
        assertEquals(6, http.requests.size());
        assertTrue(http.requests.get(0).contains(OPEN_LIBRARY) && http.requests.get(0).endsWith(CATCHER));
        assertTrue(http.requests.get(1).contains(GOOGLE + CATCHER));
        assertTrue(http.requests.get(2).contains(LOC + CATCHER));
        assertTrue(http.requests.get(3).contains(LIBRARY_THING + CATCHER));
        assertTrue(http.requests.get(4).contains(OPEN_LIBRARY) && http.requests.get(4).endsWith(CATCHER_RELATED_1));
        assertTrue(http.requests.get(5).contains(LOC + CATCHER_RELATED_1));
    }

    @Test
    public void locHitForTheScannedIsbn_isUsedBeforeAnyRelatedEditionIsConsulted() {
        FakeHttpFetcher http = new FakeHttpFetcher()
                .respond(OPEN_LIBRARY, Fixtures.read("openlibrary_miss.json"))
                .respond(GOOGLE, Fixtures.read("google_miss.json"))
                .respond(LOC + GATSBY, Fixtures.read("loc_gatsby.xml"));

        BookMetadataLookupService.MetadataResult result = resolve(http, GATSBY);

        assertEquals(BookMetadataLookupService.Source.LIBRARY_OF_CONGRESS, result.source);
        assertEquals(GATSBY, result.resolvedIsbn);
        assertEquals(0, http.countRequests(LIBRARY_THING));
    }

    @Test
    public void aRelatedEdition_isTriedOnOpenLibraryFirst() {
        FakeHttpFetcher http = new FakeHttpFetcher()
                .respond("ISBN:" + CATCHER_RELATED_1, "{\"ISBN:" + CATCHER_RELATED_1 + "\": {\"title\": \"The Catcher in the Rye\"}}")
                .respond(OPEN_LIBRARY, Fixtures.read("openlibrary_miss.json"))
                .respond(GOOGLE, Fixtures.read("google_miss.json"))
                .respond(LIBRARY_THING + CATCHER, Fixtures.read("librarything_catcher.xml"))
                .respond(LOC, Fixtures.read("loc_miss.xml"));

        BookMetadataLookupService.MetadataResult result = resolve(http, CATCHER);

        assertEquals(BookMetadataLookupService.Source.OPEN_LIBRARY, result.source);
        assertEquals(CATCHER_RELATED_1, result.resolvedIsbn);
        // Google is only asked about the scanned ISBN, never about related editions.
        assertEquals(1, http.countRequests(GOOGLE));
        assertEquals(1, http.countRequests(LOC));
    }

    @Test
    public void googleHttpError_isTreatedAsAMiss_notAFailedLookup() {
        // e.g. HTTP 429 quota exhaustion: openStream() throws, and the chain must carry on.
        FakeHttpFetcher http = new FakeHttpFetcher()
                .fail(GOOGLE)
                .respond(LIBRARY_THING + CATCHER, Fixtures.read("librarything_catcher.xml"))
                .respond(LOC + CATCHER_RELATED_1, Fixtures.read("loc_gatsby.xml"));

        BookMetadataLookupService.MetadataResult result = resolve(http, CATCHER);

        assertEquals(BookMetadataLookupService.Source.LIBRARY_OF_CONGRESS, result.source);
    }

    @Test
    public void locTemporarilyUnavailable_isTreatedAsAMiss_andTheNextRelatedEditionIsTried() {
        FakeHttpFetcher http = new FakeHttpFetcher()
                .respond(GOOGLE, Fixtures.read("google_miss.json"))
                .respond(LIBRARY_THING + CATCHER, Fixtures.read("librarything_catcher.xml"))
                .respond(LOC + CATCHER_RELATED_1, Fixtures.read("loc_unavailable.xml"))
                .respond(LOC + CATCHER_RELATED_2, Fixtures.read("loc_gatsby.xml"));

        BookMetadataLookupService.MetadataResult result = resolve(http, CATCHER);

        assertEquals(CATCHER_RELATED_2, result.resolvedIsbn);
    }

    @Test
    public void relatedEditionAttempts_areCapped() {
        StringBuilder idlist = new StringBuilder("<idlist>");
        for (int i = 0; i < BookMetadataLookupService.MAX_RELATED_ISBNS_TO_TRY + 10; i++) {
            idlist.append("<isbn>").append(String.format("%010d", i)).append("</isbn>");
        }
        idlist.append("</idlist>");
        FakeHttpFetcher http = new FakeHttpFetcher()
                .respond(GOOGLE, Fixtures.read("google_miss.json"))
                .respond(LIBRARY_THING, idlist.toString())
                .respond(LOC, Fixtures.read("loc_miss.xml"));

        BookMetadataLookupService.MetadataResult result = resolve(http, CATCHER);

        assertTrue(result.isError());
        assertEquals(1, http.countRequests(GOOGLE));
        assertEquals(1 + BookMetadataLookupService.MAX_RELATED_ISBNS_TO_TRY, http.countRequests(OPEN_LIBRARY));
        assertEquals(1 + BookMetadataLookupService.MAX_RELATED_ISBNS_TO_TRY, http.countRequests(LOC));
    }

    @Test
    public void libraryThingDown_afterEveryDirectMiss_isAnErrorResult() {
        FakeHttpFetcher http = new FakeHttpFetcher()
                .respond(OPEN_LIBRARY, Fixtures.read("openlibrary_miss.json"))
                .respond(GOOGLE, Fixtures.read("google_miss.json"))
                .respond(LOC, Fixtures.read("loc_miss.xml"))
                .fail(LIBRARY_THING);

        BookMetadataLookupService.MetadataResult result = resolve(http, GATSBY);

        assertTrue(result.isError());
        assertEquals(4, http.requests.size());
    }

    @Test
    public void aTypedIsbn_isNormalizedBeforeItIsLookedUp() {
        FakeHttpFetcher http = new FakeHttpFetcher()
                .respond("ISBN:014023750X", "{\"ISBN:014023750X\": {\"title\": \"The Catcher in the Rye\"}}");

        BookMetadataLookupService.MetadataResult result = resolve(http, "0-14-023750-x");

        assertEquals("The Catcher in the Rye", result.metadata.title);
        assertEquals("014023750X", result.resolvedIsbn);
    }

    @Test
    public void everySourceFailing_isAnErrorResult() {
        FakeHttpFetcher http = new FakeHttpFetcher();

        BookMetadataLookupService.MetadataResult result = resolve(http, GATSBY);

        assertTrue(result.isError());
        assertNull(result.metadata);
        assertNull(result.resolvedIsbn);
    }
}
