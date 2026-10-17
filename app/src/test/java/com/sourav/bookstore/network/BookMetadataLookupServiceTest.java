package com.sourav.bookstore.network;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure-parsing coverage for {@link BookMetadataLookupService#parseIsbnList} and {@link
 * BookMetadataLookupService#parseLocMods} -- no network involved, so this runs as a plain JUnit
 * test (no Robolectric needed).
 */
public class BookMetadataLookupServiceTest {

    private final BookMetadataLookupService service = new BookMetadataLookupService();

    // Trimmed from a real Library of Congress SRU/MODS response for ISBN 0312289901, keeping the
    // <subject>/<relatedItem> entries that themselves contain <name>/<title> elements -- those
    // must NOT be picked up as the book's own author/title.
    private static final String LOC_SUCCESS_RESPONSE =
            "<zs:searchRetrieveResponse xmlns:zs=\"http://www.loc.gov/zing/srw/\">"
                    + "<zs:version>1.1</zs:version>"
                    + "<zs:numberOfRecords>1</zs:numberOfRecords>"
                    + "<zs:records><zs:record>"
                    + "<zs:recordSchema>mods</zs:recordSchema>"
                    + "<zs:recordPacking>xml</zs:recordPacking>"
                    + "<zs:recordData>"
                    + "<mods xmlns=\"http://www.loc.gov/mods/v3\" version=\"3.8\">"
                    + "<titleInfo><title>Robert Ludlum's The Altman code</title></titleInfo>"
                    + "<name type=\"personal\" usage=\"primary\"><namePart>Lynds, Gayle.</namePart></name>"
                    + "<typeOfResource>text</typeOfResource>"
                    + "<subject authority=\"lcsh\">"
                    + "<name type=\"personal\"><namePart>Smith, Jon</namePart><namePart type=\"termsOfAddress\">(Fictitious character)</namePart></name>"
                    + "<genre>Fiction</genre>"
                    + "</subject>"
                    + "<relatedItem>"
                    + "<titleInfo><title>Altman code</title></titleInfo>"
                    + "<name type=\"personal\"><namePart>Ludlum, Robert,</namePart><namePart type=\"date\">1927-2001</namePart></name>"
                    + "</relatedItem>"
                    + "<identifier type=\"isbn\">0312289901</identifier>"
                    + "<identifier type=\"isbn\">9780312289904</identifier>"
                    + "</mods>"
                    + "</zs:recordData>"
                    + "<zs:recordPosition>1</zs:recordPosition>"
                    + "</zs:record></zs:records>"
                    + "</zs:searchRetrieveResponse>";

    private static final String LOC_MISS_RESPONSE =
            "<zs:searchRetrieveResponse xmlns:zs=\"http://www.loc.gov/zing/srw/\">"
                    + "<zs:version>1.1</zs:version>"
                    + "<zs:numberOfRecords>0</zs:numberOfRecords>"
                    + "<zs:echoedSearchRetrieveRequest>"
                    + "<zs:version>1.1</zs:version>"
                    + "<zs:query>bath.isbn=0752858769</zs:query>"
                    + "<zs:maximumRecords>1</zs:maximumRecords>"
                    + "<zs:recordPacking>xml</zs:recordPacking>"
                    + "<zs:recordSchema>mods</zs:recordSchema>"
                    + "</zs:echoedSearchRetrieveRequest>"
                    + "</zs:searchRetrieveResponse>";

    @Test
    public void parseLocMods_extractsTitleAndAuthorFromTheRecord_notFromNestedSubjectOrRelatedItem() {
        BookMetadataLookupService.MetadataResult result = service.parseLocMods(LOC_SUCCESS_RESPONSE, "0312289901");

        assertEquals(BookMetadataLookupService.Source.LIBRARY_OF_CONGRESS, result.source);
        assertEquals("0312289901", result.resolvedIsbn);
        assertEquals("Robert Ludlum's The Altman code", result.metadata.title);
        assertEquals("Lynds, Gayle.", result.metadata.author);
        assertNull(result.metadata.thumbnailUrl);
    }

    @Test
    public void parseLocMods_keepsTheLeadingArticleLocFilesUnderNonSort() {
        BookMetadataLookupService.MetadataResult result =
                service.parseLocMods(Fixtures.read("loc_gatsby.xml"), "9780743273565");

        assertEquals("The great Gatsby", result.metadata.title);
        assertEquals("Fitzgerald, F. Scott (Francis Scott),", result.metadata.author);
    }

    @Test
    public void parseLocMods_joinsAnElidedArticleWithoutASpace() {
        String xml = "<zs:searchRetrieveResponse xmlns:zs=\"http://www.loc.gov/zing/srw/\">"
                + "<zs:numberOfRecords>1</zs:numberOfRecords><zs:records><zs:record><zs:recordData>"
                + "<mods xmlns=\"http://www.loc.gov/mods/v3\"><titleInfo><nonSort>L'</nonSort>"
                + "<title>\u00e9tranger</title></titleInfo></mods>"
                + "</zs:recordData></zs:record></zs:records></zs:searchRetrieveResponse>";

        assertEquals("L'\u00e9tranger", service.parseLocMods(xml, "2070360024").metadata.title);
    }

    @Test
    public void normalizeIsbn_stripsSeparators_andUpperCasesTheCheckDigit() {
        assertEquals("014023750X", BookMetadataLookupService.normalizeIsbn(" 0-14-023750-x "));
        assertEquals("9780743273565", BookMetadataLookupService.normalizeIsbn("978-0-7432-7356-5"));
    }

    @Test
    public void parseLocMods_returnsNull_whenNumberOfRecordsIsZero() {
        BookMetadataLookupService.MetadataResult result = service.parseLocMods(LOC_MISS_RESPONSE, "0752858769");

        assertNull(result);
    }

    @Test
    public void parseLocMods_returnsNull_whenLocReportsItselfUnavailable() {
        // A real response: an SRU diagnostic with no <zs:numberOfRecords> at all.
        BookMetadataLookupService.MetadataResult result =
                service.parseLocMods(Fixtures.read("loc_unavailable.xml"), "9780743273565");

        assertNull(result);
    }

    @Test
    public void parseLocMods_returnsNull_forMalformedXml() {
        BookMetadataLookupService.MetadataResult result = service.parseLocMods("not xml at all", "0000000000");

        assertNull(result);
    }

    @Test
    public void parseIsbnList_extractsEveryIsbnFromALibraryThingIdlistResponse() {
        String xml = "<idlist>\n"
                + "<isbn>0312995458</isbn>\n"
                + "<isbn>0312289901</isbn>\n"
                + "<isbn>0752858769</isbn>\n"
                + "<isbn>075285836X</isbn>\n"
                + "<isbn>9780312289904</isbn>\n"
                + "</idlist>";

        List<String> isbns = service.parseIsbnList(xml);

        assertEquals(5, isbns.size());
        assertEquals("0312995458", isbns.get(0));
        assertEquals("0312289901", isbns.get(1));
        assertEquals("0752858769", isbns.get(2));
        assertEquals("075285836X", isbns.get(3));
        assertEquals("9780312289904", isbns.get(4));
    }

    @Test
    public void parseIsbnList_returnsEmptyList_forAnEmptyIdlist() {
        List<String> isbns = service.parseIsbnList("<idlist></idlist>");

        assertTrue(isbns.isEmpty());
    }

    @Test
    public void parseIsbnList_returnsEmptyList_forMalformedXml() {
        List<String> isbns = service.parseIsbnList("not xml at all");

        assertTrue(isbns.isEmpty());
    }

    @Test
    public void parseGoogleBooksPageVolumeId_readsTheCanonicalLinksId() {
        // The real books.google.com/books?vid=ISBN9798291908983 page, captured 2026-10-17.
        String html = Fixtures.read("googlepage_tambuti.html");

        assertEquals("zBSL0QEACAAJ", BookMetadataLookupService.parseGoogleBooksPageVolumeId(html));
    }

    @Test
    public void parseGoogleBooksPageVolumeId_isNull_whenThePageHasNoCanonicalLink() {
        assertNull(BookMetadataLookupService.parseGoogleBooksPageVolumeId(
                "<html><head><title>Google Books</title></head><a href=\"/books?id=zBSL0QEACAAJ\"></a></html>"));
    }
}
