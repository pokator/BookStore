package com.sourav.bookstore.ui.home;

import com.sourav.bookstore.network.BookMetadataLookupService;
import com.sourav.bookstore.network.FakeHttpFetcher;
import com.sourav.bookstore.network.Fixtures;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * The hand-off between a lookup result and {@link BookLookupTask.Callback}: which callbacks fire,
 * in what order, with what. Calls doInBackground/onPostExecute directly so nothing is threaded.
 */
@RunWith(RobolectricTestRunner.class)
public class BookLookupTaskTest {

    private static final String GATSBY = "9780743273565";

    private static final class RecordingCallback implements BookLookupTask.Callback {
        final List<String> calls = new ArrayList<>();

        @Override
        public void onIsbnResolved(String isbn) {
            calls.add("isbn:" + isbn);
        }

        @Override
        public void onBookFound(String title, String author, String thumbnailUrl, BookMetadataLookupService.Source source) {
            calls.add("found:" + title + "|" + author + "|" + (thumbnailUrl != null) + "|" + source);
        }

        @Override
        public void onLookupError() {
            calls.add("error");
        }
    }

    private List<String> run(FakeHttpFetcher http) {
        RecordingCallback callback = new RecordingCallback();
        BookLookupTask task = new BookLookupTask(callback, new BookMetadataLookupService(http));
        task.onPostExecute(task.doInBackground(GATSBY));
        return callback.calls;
    }

    @Test
    public void aResolvedBook_reportsTheResolvedIsbnFirst_thenTheBook() {
        List<String> calls = run(new FakeHttpFetcher()
                .respond("q=isbn:" + GATSBY, Fixtures.read("google_hit.json")));

        assertEquals(2, calls.size());
        assertEquals("isbn:" + GATSBY, calls.get(0));
        assertEquals("found:F. Scott Fitzgerald: The Great Gatsby|F. Scott Fitzgerald|true|GOOGLE_BOOKS", calls.get(1));
    }

    @Test
    public void aLookupThatResolvesNothing_reportsOnlyAnError() {
        List<String> calls = run(new FakeHttpFetcher());

        assertEquals(1, calls.size());
        assertEquals("error", calls.get(0));
    }

    @Test
    public void aNullResult_reportsAnError() {
        RecordingCallback callback = new RecordingCallback();

        new BookLookupTask(callback, new BookMetadataLookupService(new FakeHttpFetcher())).onPostExecute(null);

        assertEquals(1, callback.calls.size());
        assertEquals("error", callback.calls.get(0));
    }
}
