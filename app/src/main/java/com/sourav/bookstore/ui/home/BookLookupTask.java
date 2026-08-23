package com.sourav.bookstore.ui.home;

import android.os.AsyncTask;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.sourav.bookstore.network.BookMetadataLookupService;

/**
 * Runs {@link BookMetadataLookupService#resolveMetadata} off the UI thread and reports the
 * outcome back through {@link Callback}, so HomeFragment never has to know about the
 * network/parsing details -- only what to show. Extracted from the private
 * HomeFragment.GetBookInfo inner class; the branching in {@link #onPostExecute} reproduces
 * exactly what used to live directly in that class's onPostExecute, just driven off the
 * structured {@link BookMetadataLookupService.MetadataResult} instead of a raw response string.
 */
public class BookLookupTask extends AsyncTask<String, Void, BookMetadataLookupService.MetadataResult> {

    public interface Callback {
        void onIsbnResolved(String isbn);

        /**
         * @param source selects both the display prefix ("TITLE: "/"AUTHOR(S): " for the
         *               structured sources vs "Title: "/"Author: " for the scraped HTML
         *               fallback) and whether a missing thumbnailUrl should clear the current
         *               thumbnail (Google Books only) or leave it untouched (the other two
         *               sources never resolve one).
         */
        void onBookFound(String title, String author, String thumbnailUrl, BookMetadataLookupService.Source source);

        void onLookupError();
    }

    private final Callback callback;

    public BookLookupTask(Callback callback) {
        this.callback = callback;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected BookMetadataLookupService.MetadataResult doInBackground(String... isbns) {
        for (String originalIsbn : isbns) {
            return new BookMetadataLookupService().resolveMetadata(originalIsbn);
        }
        return null;
    }

    @Override
    protected void onPostExecute(BookMetadataLookupService.MetadataResult result) {
        if (result == null || result.isError()) {
            callback.onLookupError();
            return;
        }

        if (result.resolvedIsbn != null) {
            callback.onIsbnResolved(result.resolvedIsbn);
        }

        callback.onBookFound(result.metadata.title, result.metadata.author, result.metadata.thumbnailUrl, result.source);
    }
}
