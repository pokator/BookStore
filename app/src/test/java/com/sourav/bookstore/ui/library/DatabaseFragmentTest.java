package com.sourav.bookstore.ui.library;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import com.sourav.bookstore.R;
import com.sourav.bookstore.data.DBManager;
import com.sourav.bookstore.data.DatabaseHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowPopupMenu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Regression coverage for the ui.library package move and the inline search/sort feature:
 * confirms DatabaseFragment reads through data.DBManager/DatabaseHelper, lists the stored rows,
 * live-filters via the search box, and re-sorts via the sort button's popup menu.
 */
@RunWith(RobolectricTestRunner.class)
public class DatabaseFragmentTest {

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

    private DatabaseFragment attachFragment() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class)
                .create().start().resume().get();
        DatabaseFragment fragment = DatabaseFragment.newInstance();
        activity.getSupportFragmentManager().beginTransaction()
                .add(fragment, "database-fragment")
                .commitNow();
        return fragment;
    }

    @Test
    public void onCreateView_listsBooksAlreadyInTheDatabase() {
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");
        dbManager.insert("Neuromancer", "William Gibson", "9780441569595");

        DatabaseFragment fragment = attachFragment();

        View fragmentView = fragment.getView();
        assertNotNull(fragmentView);
        ListView listView = fragmentView.findViewById(R.id.list_view);
        assertEquals(2, listView.getAdapter().getCount());
    }

    @Test
    public void typingInTheSearchBox_filtersTheListLive() {
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");
        dbManager.insert("Neuromancer", "William Gibson", "9780441569595");

        DatabaseFragment fragment = attachFragment();
        View fragmentView = fragment.getView();
        ListView listView = fragmentView.findViewById(R.id.list_view);
        EditText searchEditText = fragmentView.findViewById(R.id.search_edittext);

        searchEditText.setText("Gibson");

        assertEquals(1, listView.getAdapter().getCount());
        TextView authorTextView = (TextView) listView.getAdapter().getView(0, null, listView).findViewById(R.id.desc);
        assertEquals("William Gibson", authorTextView.getText().toString());
    }

    @Test
    public void searchBox_matchesByIsbnToo() {
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");
        dbManager.insert("Neuromancer", "William Gibson", "9780441569595");

        DatabaseFragment fragment = attachFragment();
        View fragmentView = fragment.getView();
        ListView listView = fragmentView.findViewById(R.id.list_view);
        EditText searchEditText = fragmentView.findViewById(R.id.search_edittext);

        searchEditText.setText("9780441013593");

        assertEquals(1, listView.getAdapter().getCount());
    }

    private void chooseSortOption(ImageButton sortButton, int menuItemId) {
        sortButton.performClick();
        PopupMenu popupMenu = ShadowPopupMenu.getLatestPopupMenu();
        popupMenu.getMenu().performIdentifierAction(menuItemId, 0);
    }

    @Test
    public void changingTheSortOption_changesRowOrder() {
        dbManager.insert("Neuromancer", "William Gibson", "9780441569595");
        dbManager.insert("Dune", "Frank Herbert", "9780441013593");

        DatabaseFragment fragment = attachFragment();
        View fragmentView = fragment.getView();
        ListView listView = fragmentView.findViewById(R.id.list_view);
        ImageButton sortButton = fragmentView.findViewById(R.id.sort_button);

        // Default on load is "Title (A-Z)".
        TextView firstTitleAscending = (TextView) listView.getAdapter().getView(0, null, listView).findViewById(R.id.title);
        assertEquals("Dune", firstTitleAscending.getText().toString());

        chooseSortOption(sortButton, R.id.sort_title_desc);
        TextView firstTitleDescending = (TextView) listView.getAdapter().getView(0, null, listView).findViewById(R.id.title);
        assertEquals("Neuromancer", firstTitleDescending.getText().toString());
    }
}
