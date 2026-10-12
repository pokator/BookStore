package com.sourav.bookstore.network;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * {@link BookMetadataLookupService#parseOpenLibraryJson} against real Open Library Books API
 * responses. Robolectric only for Android's org.json.
 */
@RunWith(RobolectricTestRunner.class)
public class OpenLibraryParserTest {

    private final BookMetadataLookupService service = new BookMetadataLookupService(new FakeHttpFetcher());

    @Test
    public void extractsTitle_everyAuthor_andTheMediumCover() {
        BookMetadataLookupService.MetadataResult result =
                service.parseOpenLibraryJson(Fixtures.read("openlibrary_catcher.json"), "9780316769488");

        assertEquals(BookMetadataLookupService.Source.OPEN_LIBRARY, result.source);
        assertEquals("9780316769488", result.resolvedIsbn);
        assertEquals("The Catcher in the Rye", result.metadata.title);
        assertEquals("J. D. Salinger, Shao jia yun", result.metadata.author);
        assertEquals("https://covers.openlibrary.org/b/id/15172531-M.jpg", result.metadata.thumbnailUrl);
    }

    @Test
    public void aRecordWithoutACover_hasNoThumbnail() {
        BookMetadataLookupService.MetadataResult result =
                service.parseOpenLibraryJson(Fixtures.read("openlibrary_nocover.json"), "9781529100778");

        assertEquals("Juniper and Thorn", result.metadata.title);
        assertEquals("Ava Reid", result.metadata.author);
        assertNull(result.metadata.thumbnailUrl);
    }

    @Test
    public void anEmptyResponse_isAMiss() {
        assertNull(service.parseOpenLibraryJson(Fixtures.read("openlibrary_miss.json"), "9789999999999"));
    }

    @Test
    public void aRecordForADifferentIsbn_isAMiss() {
        assertNull(service.parseOpenLibraryJson(Fixtures.read("openlibrary_catcher.json"), "9780743273565"));
    }

    @Test
    public void aRecordWithNoTitle_isAMiss() {
        assertNull(service.parseOpenLibraryJson("{\"ISBN:1\": {\"authors\": [{\"name\": \"Someone\"}]}}", "1"));
    }

    @Test
    public void aJsonNullTitle_isAMiss_notTheTitleNull() {
        assertNull(service.parseOpenLibraryJson("{\"ISBN:1\": {\"title\": null}}", "1"));
    }

    @Test
    public void aJsonNullCover_meansNoThumbnail_notTheUrlNull() {
        BookMetadataLookupService.MetadataResult result = service.parseOpenLibraryJson(
                "{\"ISBN:1\": {\"title\": \"Dune\", \"cover\": {\"medium\": null}}}", "1");

        assertNull(result.metadata.thumbnailUrl);
    }

    @Test
    public void aMalformedAuthorEntry_isSkipped_keepingTheRest() {
        BookMetadataLookupService.MetadataResult result = service.parseOpenLibraryJson(
                "{\"ISBN:1\": {\"title\": \"Dune\", \"authors\": [\"oops\", {\"name\": null}, {\"name\": \"Frank Herbert\"}]}}", "1");

        assertEquals("Dune", result.metadata.title);
        assertEquals("Frank Herbert", result.metadata.author);
    }

    @Test
    public void malformedJson_isAMiss() {
        assertNull(service.parseOpenLibraryJson("<html>rate limited</html>", "9780316769488"));
    }
}
