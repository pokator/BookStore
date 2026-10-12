package com.sourav.bookstore.ui.home;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;
import com.sourav.bookstore.R;
import com.sourav.bookstore.data.DBManager;
import com.sourav.bookstore.data.DatabaseHelper;
import com.sourav.bookstore.network.BookMetadataLookupService;
import com.sourav.bookstore.network.FakeHttpFetcher;
import com.sourav.bookstore.network.Fixtures;
import com.sourav.bookstore.network.ThumbnailFetcher;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.util.concurrent.InlineExecutorService;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowPausedAsyncTask;
import org.robolectric.shadows.ShadowToast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The whole scan-to-result pipeline on the real HomeFragment and layout: a barcode coming back
 * from ScanBarcodeActivity (or an ISBN typed into the entry dialog) -> BookLookupTask ->
 * BookMetadataLookupService's fallback chain -> what the screen shows, which cover is loaded,
 * and what Add stores. Only the network is faked (canned API responses via
 * {@link FakeHttpFetcher}); AsyncTasks run inline so each step completes before the next line.
 */
@RunWith(RobolectricTestRunner.class)
public class ScanToResultFlowTest {

    private static final String GATSBY = "9780743273565";
    private static final String CATCHER = "9780316769488";

    private Context context;
    private DBManager dbManager;
    private FakeHttpFetcher http;
    private final List<String> thumbnailRequests = new ArrayList<>();
    private boolean coverFetchFails;
    private final Bitmap cover = Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888);

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        DatabaseHelper.getInstance(context).getWritableDatabase()
                .delete(DatabaseHelper.TABLE_NAME, null, null);
        dbManager = new DBManager(context).open();
        http = new FakeHttpFetcher();
        ShadowPausedAsyncTask.overrideExecutor(new InlineExecutorService());
    }

    @After
    public void tearDown() {
        dbManager.close();
    }

    private HomeFragment attachFragment() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class)
                .create().start().resume().get();
        activity.setTheme(R.style.AppTheme);
        HomeFragment fragment = HomeFragment.newInstance(1);
        fragment.lookupService = new BookMetadataLookupService(http);
        fragment.thumbnailFetcher = new ThumbnailFetcher() {
            @Override
            public Bitmap fetch(String url) throws IOException {
                thumbnailRequests.add(url);
                if (coverFetchFails) {
                    throw new IOException("simulated cover download failure");
                }
                return cover;
            }
        };
        activity.getSupportFragmentManager().beginTransaction()
                .add(fragment, "home-fragment")
                .commitNow();
        return fragment;
    }

    private void scan(HomeFragment fragment, int format, String value) {
        Barcode barcode = new Barcode();
        barcode.format = format;
        barcode.displayValue = value;
        barcode.rawValue = value;
        Intent data = new Intent();
        data.putExtra("barcode", barcode);
        fragment.onActivityResult(0, CommonStatusCodes.SUCCESS, data);
        shadowOf(Looper.getMainLooper()).idle();
    }

    private String text(HomeFragment fragment, int id) {
        return ((TextView) fragment.requireView().findViewById(id)).getText().toString();
    }

    private int visibility(HomeFragment fragment, int id) {
        return fragment.requireView().findViewById(id).getVisibility();
    }

    /** The bitmap in the cover ImageView; setImageBitmap(null) leaves a drawable wrapping null. */
    private Bitmap shownCover(HomeFragment fragment) {
        ImageView thumb = fragment.requireView().findViewById(R.id.thumb);
        return thumb.getDrawable() == null ? null : ((BitmapDrawable) thumb.getDrawable()).getBitmap();
    }

    private static EditText findEditText(View view) {
        if (view instanceof EditText) {
            return (EditText) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findEditText(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void storedIsbns(List<String> out) {
        Cursor cursor = dbManager.fetch();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                out.add(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.ISBN)));
            } while (cursor.moveToNext());
        }
    }

    @Test
    public void scanningAnIsbnBarcode_looksItUp_andShowsTheBookReadyToAdd() {
        http.respond("q=isbn:" + GATSBY, Fixtures.read("google_hit.json"));
        HomeFragment fragment = attachFragment();

        scan(fragment, Barcode.EAN_13, GATSBY);

        assertEquals(context.getString(R.string.status_isbn, GATSBY), text(fragment, R.id.barcode_result));
        assertEquals("F. Scott Fitzgerald: The Great Gatsby", text(fragment, R.id.book_title));
        assertEquals("F. Scott Fitzgerald", text(fragment, R.id.book_author));
        assertEquals(context.getString(R.string.status_not_owned), text(fragment, R.id.ownership_banner));
        assertEquals(View.VISIBLE, visibility(fragment, R.id.add_button));
        assertTrue(http.countRequests(GATSBY) > 0);
    }

    @Test
    public void aResolvedCover_isFetchedAndShown() {
        http.respond("q=isbn:" + GATSBY, Fixtures.read("google_hit.json"));
        HomeFragment fragment = attachFragment();

        scan(fragment, Barcode.EAN_13, GATSBY);

        assertEquals(1, thumbnailRequests.size());
        assertTrue(thumbnailRequests.get(0).startsWith("http://books.google.com/books/content"));
        assertSame(cover, shownCover(fragment));
    }

    @Test
    public void anOpenLibraryResult_showsItsCover() {
        http.respond("openlibrary.org/api/books", Fixtures.read("openlibrary_catcher.json"));
        HomeFragment fragment = attachFragment();

        scan(fragment, Barcode.EAN_13, CATCHER);

        assertEquals("The Catcher in the Rye", text(fragment, R.id.book_title));
        assertEquals("J. D. Salinger, Shao jia yun", text(fragment, R.id.book_author));
        assertEquals(1, thumbnailRequests.size());
        assertEquals("https://covers.openlibrary.org/b/id/15172531-M.jpg", thumbnailRequests.get(0));
    }

    @Test
    public void aResultWithNoCover_clearsThePreviousBooksCover_andFetchesNothing() {
        http.respond("ISBN:" + CATCHER, Fixtures.read("openlibrary_catcher.json"))
                .respond("ISBN:9781529100778", Fixtures.read("openlibrary_nocover.json"));
        HomeFragment fragment = attachFragment();
        scan(fragment, Barcode.EAN_13, CATCHER);
        assertSame(cover, shownCover(fragment));

        scan(fragment, Barcode.EAN_13, "9781529100778");

        assertEquals("Juniper and Thorn", text(fragment, R.id.book_title));
        assertEquals(1, thumbnailRequests.size());
        assertNull(shownCover(fragment));
    }

    @Test
    public void aFailedCoverDownload_showsNoCover_notThePreviousBooks() {
        http.respond("q=isbn:" + GATSBY, Fixtures.read("google_hit.json"))
                .respond("ISBN:" + CATCHER, Fixtures.read("openlibrary_catcher.json"));
        HomeFragment fragment = attachFragment();
        scan(fragment, Barcode.EAN_13, GATSBY);
        assertSame(cover, shownCover(fragment));

        coverFetchFails = true;
        scan(fragment, Barcode.EAN_13, CATCHER);

        assertEquals("The Catcher in the Rye", text(fragment, R.id.book_title));
        assertNull(shownCover(fragment));
    }

    @Test
    public void addingAScannedBook_storesItWithTheIsbnTheLookupResolved() {
        // Google misses, so the chain lands on a related edition's ISBN via LibraryThing + LOC;
        // that ISBN (not the scanned one) is what gets stored.
        http.respond("q=isbn:", Fixtures.read("google_miss.json"))
                .respond("/thingISBN/" + CATCHER, Fixtures.read("librarything_catcher.xml"))
                .respond("bath.isbn=0316769177", Fixtures.read("loc_gatsby.xml"));
        HomeFragment fragment = attachFragment();
        scan(fragment, Barcode.EAN_13, CATCHER);

        fragment.requireView().findViewById(R.id.add_button).performClick();

        List<String> isbns = new ArrayList<>();
        storedIsbns(isbns);
        assertEquals(1, isbns.size());
        assertEquals("0316769177", isbns.get(0));
        assertEquals(context.getString(R.string.status_owned_isbn), text(fragment, R.id.ownership_banner));
    }

    @Test
    public void aLookupThatResolvesNothing_showsTheErrorAndNoAddButton() {
        HomeFragment fragment = attachFragment();

        scan(fragment, Barcode.EAN_13, GATSBY);

        assertEquals(context.getString(R.string.status_lookup_error), text(fragment, R.id.book_title));
        assertEquals("", text(fragment, R.id.book_author));
        assertEquals(View.GONE, visibility(fragment, R.id.ownership_banner));
        assertEquals(View.INVISIBLE, visibility(fragment, R.id.add_button));
    }

    @Test
    public void aNonIsbnBarcode_isRejected_withoutAnyLookup() {
        HomeFragment fragment = attachFragment();

        scan(fragment, Barcode.QR_CODE, "https://example.com");

        assertEquals("Not a valid scan! Retry your scan.", ShadowToast.getTextOfLatestToast());
        assertTrue(http.requests.isEmpty());
    }

    @Test
    public void backingOutOfTheScanner_saysNoBarcodeWasCaptured() {
        HomeFragment fragment = attachFragment();

        fragment.onActivityResult(0, CommonStatusCodes.CANCELED, null);

        assertEquals(context.getString(R.string.status_no_barcode), text(fragment, R.id.barcode_result));
        assertTrue(http.requests.isEmpty());
    }

    @Test
    public void aNewScan_replacesThePreviousResult_includingTheIsbnAddStores() {
        // The first book resolves via a related edition (a different ISBN from the one scanned);
        // Add after the second scan must store the second book's own ISBN, not that one.
        http.respond("q=isbn:", Fixtures.read("google_miss.json"))
                .respond("/thingISBN/" + CATCHER, Fixtures.read("librarything_catcher.xml"))
                .respond("bath.isbn=0316769177", Fixtures.read("loc_gatsby.xml"))
                .respond("bath.isbn=" + GATSBY, Fixtures.read("loc_gatsby.xml"));
        HomeFragment fragment = attachFragment();
        scan(fragment, Barcode.EAN_13, CATCHER);

        scan(fragment, Barcode.EAN_13, GATSBY);
        fragment.requireView().findViewById(R.id.add_button).performClick();

        assertEquals(context.getString(R.string.status_isbn, GATSBY), text(fragment, R.id.barcode_result));
        List<String> isbns = new ArrayList<>();
        storedIsbns(isbns);
        assertEquals(1, isbns.size());
        assertEquals(GATSBY, isbns.get(0));
    }

    @Test
    public void typingAnIsbn_runsTheSameLookup() {
        http.respond("q=isbn:" + GATSBY, Fixtures.read("google_hit.json"));
        HomeFragment fragment = attachFragment();

        fragment.numberEntry(fragment.requireContext());
        Dialog dialog = ShadowDialog.getLatestDialog();
        EditText input = findEditText(dialog.getWindow().getDecorView());
        assertNotNull(input);
        input.setText(GATSBY);
        ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals("F. Scott Fitzgerald: The Great Gatsby", text(fragment, R.id.book_title));
        assertEquals(View.VISIBLE, visibility(fragment, R.id.add_button));
    }

    @Test
    public void submittingAnEmptyIsbn_promptsAgain_withoutAnyLookup() {
        HomeFragment fragment = attachFragment();

        fragment.numberEntry(fragment.requireContext());
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(context.getString(R.string.status_isbn_invalid), text(fragment, R.id.barcode_result));
        assertTrue(http.requests.isEmpty());
    }
}
