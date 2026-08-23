package com.sourav.bookstore.ui.main;

import androidx.fragment.app.FragmentActivity;

import com.sourav.bookstore.ui.home.HomeFragment;
import com.sourav.bookstore.ui.library.DatabaseFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression coverage for the ui.main package move: confirms SectionsPagerAdapter still
 * resolves to the (now relocated) HomeFragment/DatabaseFragment with the right tab titles.
 * Uses a plain FragmentActivity rather than MainActivity itself to stay independent of
 * MainActivity's AppCompat theme resolution, which Robolectric doesn't exercise here.
 */
@RunWith(RobolectricTestRunner.class)
public class SectionsPagerAdapterTest {

    @Test
    public void adapter_resolvesToTheExpectedFragmentsAndTitles() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).create().get();
        SectionsPagerAdapter adapter = new SectionsPagerAdapter(activity, activity.getSupportFragmentManager());

        assertEquals(2, adapter.getCount());
        assertTrue(adapter.getItem(0) instanceof HomeFragment);
        assertTrue(adapter.getItem(1) instanceof DatabaseFragment);
        assertEquals("Scan", adapter.getPageTitle(0));
        assertEquals("Books Stored", adapter.getPageTitle(1));
    }
}
