package com.sourav.bookstore.ui.home;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import com.sourav.bookstore.R;
import com.sourav.bookstore.data.DBManager;
import com.sourav.bookstore.data.DatabaseHelper;
import com.sourav.bookstore.network.BookMetadataLookupService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Covers the "do I already own this?" banner on the scan screen, end to end against the real
 * layout and a real SQLite database: the lookup callbacks are invoked directly (as
 * {@link BookLookupTask} would after a scan) so no network is involved, and only the resulting
 * banner state is asserted.
 */
@RunWith(RobolectricTestRunner.class)
public class HomeFragmentTest {

    private Context context;
    private DBManager dbManager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        DatabaseHelper.getInstance(context).getWritableDatabase()
                .delete(DatabaseHelper.TABLE_NAME, null, null);
        dbManager = new DBManager(context).open();
    }

    @After
    public void tearDown() {
        // DatabaseHelper is a process-wide singleton; close it so the next Robolectric test
        // (running in a fresh native sandbox) doesn't inherit a stale connection handle.
        dbManager.close();
    }

    private HomeFragment attachFragment() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class)
                .create().start().resume().get();
        // fragment_main inflates a Material FAB and uses ?attr/selectableItemBackground, so the
        // host needs the app's own theme rather than the platform default.
        activity.setTheme(R.style.AppTheme);
        HomeFragment fragment = HomeFragment.newInstance(1);
        activity.getSupportFragmentManager().beginTransaction()
                .add(fragment, "home-fragment")
                .commitNow();
        return fragment;
    }

    private String bannerText(HomeFragment fragment) {
        View fragmentView = fragment.getView();
        assertNotNull(fragmentView);
        TextView banner = fragmentView.findViewById(R.id.ownership_banner);
        assertEquals(View.VISIBLE, banner.getVisibility());
        return banner.getText().toString();
    }

    /** Drives the fragment exactly as BookLookupTask.onPostExecute does after a successful scan. */
    private void deliverLookupResult(HomeFragment fragment, String isbn, String title, String author) {
        fragment.onIsbnResolved(isbn);
        fragment.onBookFound(title, author, null, BookMetadataLookupService.Source.GOOGLE_BOOKS);
    }

    @Test
    public void bannerIsHidden_beforeAnythingIsScanned() {
        HomeFragment fragment = attachFragment();

        View fragmentView = fragment.getView();
        assertNotNull(fragmentView);
        assertEquals(View.GONE, fragmentView.findViewById(R.id.ownership_banner).getVisibility());
    }

    @Test
    public void scanningABookNotInTheLibrary_saysSo() {
        HomeFragment fragment = attachFragment();

        deliverLookupResult(fragment, "9780441013593", "Dune", "Frank Herbert");

        assertEquals(context.getString(R.string.status_not_owned), bannerText(fragment));
    }

    @Test
    public void scanningAStoredIsbn_reportsItAsOwned() {
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");
        HomeFragment fragment = attachFragment();

        deliverLookupResult(fragment, "9780441013593", "Dune", "Frank Herbert");

        assertEquals(context.getString(R.string.status_owned_isbn), bannerText(fragment));
    }

    @Test
    public void scanningADifferentEditionOfAStoredBook_reportsTheLikelyMatch() {
        dbManager.insert("Dune", "Herbert, Frank", "9780441013593");
        HomeFragment fragment = attachFragment();

        // A different edition's ISBN, and the title carrying a subtitle the stored row lacks.
        deliverLookupResult(fragment, "9780593099322", "Dune: A Novel", "Frank Herbert");

        assertEquals(context.getString(R.string.status_owned_similar, "Dune"), bannerText(fragment));
    }

    @Test
    public void addingTheBookOnScreen_flipsTheBannerToOwned() {
        HomeFragment fragment = attachFragment();
        deliverLookupResult(fragment, "9780441013593", "Dune", "Frank Herbert");
        assertEquals(context.getString(R.string.status_not_owned), bannerText(fragment));

        fragment.getView().findViewById(R.id.add_button).performClick();

        assertEquals(context.getString(R.string.status_owned_isbn), bannerText(fragment));
    }

    @Test
    public void aFailedLookup_hidesTheBanner() {
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");
        HomeFragment fragment = attachFragment();
        deliverLookupResult(fragment, "9780441013593", "Dune", "Frank Herbert");
        assertTrue(bannerText(fragment).length() > 0);

        fragment.onLookupError();

        assertEquals(View.GONE,
                fragment.getView().findViewById(R.id.ownership_banner).getVisibility());
    }
}
