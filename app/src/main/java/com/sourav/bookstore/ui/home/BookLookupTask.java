package com.sourav.bookstore.ui.home;

import android.os.AsyncTask;

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
         * @param thumbnailUrl a cover image to fetch, or null if the source didn't resolve one.
         * @param source       which metadata source answered.
         */
        void onBookFound(String title, String author, String thumbnailUrl, BookMetadataLookupService.Source source);

        void onLookupError();
    }

    private final Callback callback;
    private final BookMetadataLookupService lookupService;

    public BookLookupTask(Callback callback, BookMetadataLookupService lookupService) {
        this.callback = callback;
        this.lookupService = lookupService;
    }

    @Override
    protected BookMetadataLookupService.MetadataResult doInBackground(String... isbns) {
        for (String originalIsbn : isbns) {
            return lookupService.resolveMetadata(originalIsbn);
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
