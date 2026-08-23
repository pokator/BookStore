package com.sourav.bookstore.ui.home;

import android.graphics.Bitmap;
import android.os.AsyncTask;

import com.sourav.bookstore.network.ThumbnailFetcher;

/**
 * Runs {@link ThumbnailFetcher#fetch} off the UI thread. Extracted from the private
 * HomeFragment.GetBookThumb inner class.
 *
 * <p>{@link Callback#onThumbnailFetchResult} deliberately distinguishes "fetch completed
 * (possibly with a null bitmap, e.g. a bad decode)" from "fetch threw" -- the original inline
 * code only ever overwrote its cached thumbnail field on a successful (possibly null-returning)
 * fetch, leaving it untouched on any exception so the previous thumbnail stayed on screen. That
 * distinction is preserved here via {@code fetchSucceeded} rather than collapsing both cases to
 * a single nullable {@code Bitmap}.
 */
public class ThumbnailTask extends AsyncTask<String, Void, ThumbnailTask.Result> {

    public interface Callback {
        void onThumbnailFetchResult(boolean fetchSucceeded, Bitmap bitmap);
    }

    static final class Result {
        final boolean fetchSucceeded;
        final Bitmap bitmap;

        Result(boolean fetchSucceeded, Bitmap bitmap) {
            this.fetchSucceeded = fetchSucceeded;
            this.bitmap = bitmap;
        }
    }

    private final Callback callback;

    public ThumbnailTask(Callback callback) {
        this.callback = callback;
    }

    @Override
    protected Result doInBackground(String... thumbURLs) {
        try {
            return new Result(true, new ThumbnailFetcher().fetch(thumbURLs[0]));
        } catch (Exception e) {
            e.printStackTrace();
            return new Result(false, null);
        }
    }

    @Override
    protected void onPostExecute(Result result) {
        callback.onThumbnailFetchResult(result.fetchSucceeded, result.bitmap);
    }
}
