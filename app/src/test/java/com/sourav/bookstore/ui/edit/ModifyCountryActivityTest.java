package com.sourav.bookstore.ui.edit;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Looper;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
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
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowDialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression coverage for the ui.edit package move: confirms ModifyCountryActivity still reads
 * the same title/author/isbn/id Intent-extra contract, and still updates/deletes through
 * data.DBManager correctly.
 */
@RunWith(RobolectricTestRunner.class)
public class ModifyCountryActivityTest {

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

    private long insertBook(String title, String author, String isbn) {
        dbManager.insert(title, author, isbn);
        Cursor cursor = dbManager.fetch();
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper._ID));
        cursor.close();
        return id;
    }

    private Intent intentFor(long id, String title, String author, String isbn) {
        Intent intent = new Intent(context, ModifyCountryActivity.class);
        intent.putExtra("id", String.valueOf(id));
        intent.putExtra("title", title);
        intent.putExtra("author", author);
        intent.putExtra("isbn", isbn);
        return intent;
    }

    @Test
    public void onCreate_populatesFieldsFromIntentExtras() {
        long id = insertBook("Dune", "Frank Herbert", "9780441013593");

        ModifyCountryActivity activity = Robolectric.buildActivity(
                        ModifyCountryActivity.class, intentFor(id, "Dune", "Frank Herbert", "9780441013593"))
                .create().get();

        EditText titleText = activity.findViewById(R.id.subject_edittext);
        EditText authorText = activity.findViewById(R.id.author_edittext);
        EditText isbnText = activity.findViewById(R.id.isbn_edittext);

        assertEquals("Dune", titleText.getText().toString());
        assertEquals("Frank Herbert", authorText.getText().toString());
        assertEquals("9780441013593", isbnText.getText().toString());
    }

    @Test
    public void clickingUpdate_persistsEditedFieldsAndFinishes() {
        long id = insertBook("Dune", "Frank Herbert", "9780441013593");

        ModifyCountryActivity activity = Robolectric.buildActivity(
                        ModifyCountryActivity.class, intentFor(id, "Dune", "Frank Herbert", "9780441013593"))
                .create().get();

        EditText titleText = activity.findViewById(R.id.subject_edittext);
        titleText.setText("Dune Messiah");
        activity.findViewById(R.id.btn_update).performClick();

        Cursor cursor = dbManager.fetch();
        assertEquals("Dune Messiah", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.TITLE)));
        cursor.close();
        assertTrue(activity.isFinishing());
    }

    @Test
    public void clickingDelete_asksForConfirmationBeforeRemovingTheRow() {
        long id = insertBook("Dune", "Frank Herbert", "9780441013593");

        ModifyCountryActivity activity = Robolectric.buildActivity(
                        ModifyCountryActivity.class, intentFor(id, "Dune", "Frank Herbert", "9780441013593"))
                .create().get();

        activity.findViewById(R.id.btn_delete).performClick();

        // The row must still be there until the confirmation dialog is accepted.
        Cursor beforeConfirm = dbManager.fetch();
        assertEquals(1, beforeConfirm.getCount());
        beforeConfirm.close();

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Cursor cursor = dbManager.fetch();
        assertEquals(0, cursor.getCount());
        cursor.close();
        assertTrue(activity.isFinishing());
    }

    @Test
    public void clickingDelete_thenCancel_keepsTheRow() {
        long id = insertBook("Dune", "Frank Herbert", "9780441013593");

        ModifyCountryActivity activity = Robolectric.buildActivity(
                        ModifyCountryActivity.class, intentFor(id, "Dune", "Frank Herbert", "9780441013593"))
                .create().get();

        activity.findViewById(R.id.btn_delete).performClick();

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Cursor cursor = dbManager.fetch();
        assertEquals(1, cursor.getCount());
        cursor.close();
        assertTrue(!activity.isFinishing());
    }
}
