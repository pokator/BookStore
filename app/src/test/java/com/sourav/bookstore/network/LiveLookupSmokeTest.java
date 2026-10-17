package com.sourav.bookstore.network;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Hits the real metadata APIs, with the real keys from local.properties, so it is opt-in:
 * <pre>gradlew testDebugUnitTest -PliveLookup --tests "*LiveLookupSmokeTest"</pre>
 * Without -PliveLookup every test here is skipped (the API quotas and outages would otherwise
 * make the default suite flaky). One test per source, so a failure names the API that broke,
 * plus end-to-end checks that well-known books resolve to the right title.
 *
 * <p>Note: from this machine, Google Books' unkeyed endpoint is often HTTP 429, and LOC
 * intermittently answers "System temporarily unavailable" -- re-run before blaming the code.
 */
@RunWith(RobolectricTestRunner.class)
public class LiveLookupSmokeTest {

    private BookMetadataLookupService service;

    @Before
    public void setUp() {
        assumeTrue("live API checks run only with -PliveLookup", Boolean.getBoolean("liveLookup"));
        service = new BookMetadataLookupService(new UrlHttpFetcher());
    }

    private static void assertTitleContains(String expected, BookMetadataLookupService.MetadataResult result) {
        assertNotNull("no result", result);
        assertFalse("lookup failed", result.isError());
        assertTrue("got \"" + result.metadata.title + "\" from " + result.source,
                result.metadata.title.toLowerCase().contains(expected));
    }

    @Test
    public void openLibrary_resolvesAnIsbn() {
        assertTitleContains("gatsby", service.tryOpenLibrary("9780743273565"));
    }

    @Test
    public void googleBooks_resolvesAnIsbn() {
        assertTitleContains("gatsby", service.tryGoogleBooks("9780743273565"));
    }

    @Test
    public void googleBooksPage_resolvesASelfPublishedIsbn() {
        assertTitleContains("tambuti", service.tryGoogleBooksPage("9798291908983"));
    }

    @Test
    public void fullChain_resolvesASelfPublishedBook() {
        assertTitleContains("tambuti", service.resolveMetadata("9798291908983"));
    }

    @Test
    public void libraryThing_listsRelatedEditions() {
        List<String> related = service.fetchRelatedIsbns("9780743273565");

        assertTrue("only " + related.size() + " related ISBNs", related.size() > 5);
    }

    @Test
    public void libraryOfCongress_resolvesAnIsbn() {
        assertTitleContains("gatsby", service.tryLibraryOfCongress("9780743273565"));
    }

    @Test
    public void fullChain_resolvesTheGreatGatsby() {
        assertTitleContains("gatsby", service.resolveMetadata("9780743273565"));
    }

    @Test
    public void fullChain_resolvesTheCatcherInTheRye() {
        assertTitleContains("catcher", service.resolveMetadata("9780316769488"));
    }

    @Test
    public void fullChain_resolvesHarryPotter() {
        assertTitleContains("sorcerer", service.resolveMetadata("9780439708180"));
    }

    @Test
    public void fullChain_resolvesProjectHailMary() {
        assertTitleContains("hail mary", service.resolveMetadata("9780593135204"));
    }
}
