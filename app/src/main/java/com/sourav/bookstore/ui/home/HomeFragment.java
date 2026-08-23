package com.sourav.bookstore.ui.home;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.sourav.bookstore.R;
import com.sourav.bookstore.data.DBManager;
import com.sourav.bookstore.data.DuplicateEntryException;
import com.sourav.bookstore.network.BookMetadataLookupService;
import com.sourav.bookstore.ui.main.PageViewModel;
import com.sourav.bookstore.ui.scan.ScanBarcodeActivity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

/**
 * Scan/entry screen: shows the most recently resolved book (or an empty state) and offers three
 * ways to add one -- barcode scan, manual ISBN entry, or manual title/author entry -- via a
 * rounded panel that shares the FAB's own view hierarchy (not a separate PopupWindow), extended
 * far enough past the FAB's top edge that the FAB visually sits docked inside it. The FAB's icon
 * rotates from "+" to "x" while the menu is open (a 45-degree turn on the same glyph, not a
 * second drawable).
 */
public class HomeFragment extends Fragment implements View.OnClickListener,
        BookLookupTask.Callback, ThumbnailTask.Callback {

    private static final String ARG_SECTION_NUMBER = "section_number";
    private static final int REQUEST_CODE_SCAN_BARCODE = 0;

    private static final ViewOutlineProvider NO_SHADOW_OUTLINE_PROVIDER = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setEmpty();
        }
    };

    private FloatingActionButton fabMain;
    private ViewOutlineProvider fabRestingOutlineProvider;
    private View addMenuPanel;
    private View addMenuScrim;
    private boolean addMenuOpen = false;
    private TextView barcodeResult, titleText, authorText;
    private Bitmap thumbImg;
    private ImageView thumb;
    private Button addButton;

    private static String isbn;
    private String searchString;

    private String activeTitle;
    private String activeAuthors;
    private PageViewModel pageViewModel;

    private DBManager dbManager;

    public static HomeFragment newInstance(int index) {
        HomeFragment fragment = new HomeFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(ARG_SECTION_NUMBER, index);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pageViewModel = ViewModelProviders.of(this).get(PageViewModel.class);
        int index = 1;
        if (getArguments() != null) {
            index = getArguments().getInt(ARG_SECTION_NUMBER);
        }
        pageViewModel.setIndex(index);
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);

        addButton = (Button) view.findViewById(R.id.add_button);
        barcodeResult = (TextView) view.findViewById(R.id.barcode_result);
        authorText = (TextView) view.findViewById(R.id.book_author);
        titleText = (TextView) view.findViewById(R.id.book_title);
        thumb = (ImageView) view.findViewById(R.id.thumb);

        if (savedInstanceState != null) {
            authorText.setText(savedInstanceState.getString("author"));
            titleText.setText(savedInstanceState.getString("title"));

            thumbImg = (Bitmap) savedInstanceState.getParcelable("thumbPic");
            thumb.setImageBitmap(thumbImg);
        }

        addButton.setVisibility(View.INVISIBLE);
        addButton.setClickable(false);
        addButton.setOnClickListener(this);

        fabMain = view.findViewById(R.id.fab_original);
        fabRestingOutlineProvider = fabMain.getOutlineProvider();
        fabMain.setOnClickListener(v -> toggleAddMenu());

        addMenuPanel = view.findViewById(R.id.add_menu_panel);
        addMenuScrim = view.findViewById(R.id.add_menu_scrim);
        addMenuScrim.setOnClickListener(v -> closeAddMenu());

        view.findViewById(R.id.menu_row_barcode).setOnClickListener(v -> {
            closeAddMenu();
            scanBarcode();
        });
        view.findViewById(R.id.menu_row_isbn).setOnClickListener(v -> {
            closeAddMenu();
            numberEntry(getContext());
        });
        view.findViewById(R.id.menu_row_manual).setOnClickListener(v -> {
            closeAddMenu();
            textEntry(getContext());
        });

        return view;
    }

    private void toggleAddMenu() {
        if (addMenuOpen) {
            closeAddMenu();
        } else {
            openAddMenu();
        }
    }

    private void openAddMenu() {
        addMenuOpen = true;
        addMenuPanel.setVisibility(View.VISIBLE);
        addMenuScrim.setVisibility(View.VISIBLE);
        fabMain.animate().rotation(45f).setDuration(150).start();
        // The FAB is docked inside add_menu_panel while open, so its own shadow -- on top of the
        // panel's -- reads as a stray floating layer rather than part of one shape. Suppress the
        // shadow via the outline (ViewOutlineProvider.NONE = nothing to cast a shadow from)
        // rather than zeroing elevation: elevation also drives Z-order among overlapping
        // siblings, and zeroing it made the panel (elevation 4dp) draw on top of and hide the
        // FAB entirely instead of just removing its shadow.
        fabMain.setOutlineProvider(NO_SHADOW_OUTLINE_PROVIDER);
    }

    private void closeAddMenu() {
        addMenuOpen = false;
        addMenuPanel.setVisibility(View.GONE);
        addMenuScrim.setVisibility(View.GONE);
        fabMain.animate().rotation(0f).setDuration(150).start();
        fabMain.setOutlineProvider(fabRestingOutlineProvider);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.add_button) {
            addEntry();
        }
    }

    public void scanBarcode() {
        Intent intent = new Intent(this.getContext(), ScanBarcodeActivity.class);
        startActivityForResult(intent, REQUEST_CODE_SCAN_BARCODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE_SCAN_BARCODE) {
            if (data != null) {
                resetResult();

                Barcode barcode = data.getParcelableExtra("barcode");

                if (barcode.displayValue != null && barcode.format == Barcode.EAN_13) {
                    searchString = barcode.displayValue;
                    new BookLookupTask(this).execute(searchString);
                    barcodeResult.setText(getString(R.string.status_isbn, barcode.displayValue));
                } else {
                    Toast.makeText(getActivity().getApplicationContext(),
                            "Not a valid scan! Retry your scan.", Toast.LENGTH_SHORT).show();
                }
            } else {
                barcodeResult.setText(R.string.status_no_barcode);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void resetResult() {
        barcodeResult.setText("");
        authorText.setText("");
        titleText.setText(R.string.scan_a_barcode);
        thumb.setImageBitmap(null);
        addButton.setVisibility(View.INVISIBLE);
        addButton.setClickable(false);
    }

    public void textEntry(Context c) {
        final EditText writerText = new EditText(c);
        final EditText bookText = new EditText(c);
        LinearLayout layout = new LinearLayout(c);

        writerText.setHint("Author");
        bookText.setHint("Book Title");
        writerText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        bookText.setGravity(View.TEXT_ALIGNMENT_CENTER);
        writerText.setWidth(400);
        bookText.setWidth(400);

        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.addView(writerText);
        layout.addView(bookText);
        layout.setPadding(48, 10, 10, 10);

        AlertDialog dialog = new AlertDialog.Builder(c)
                .setTitle("Text Entry")
                .setMessage("Enter the Author (separated by commas, if multiple) and Book Title")
                .setView(layout)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        resetResult();

                        activeAuthors = String.valueOf(writerText.getText());
                        activeTitle = String.valueOf(bookText.getText());
                        if (!activeAuthors.isEmpty() && !activeTitle.isEmpty()) {
                            titleText.setText(activeTitle);
                            authorText.setText(activeAuthors);
                            addButton.setVisibility(View.VISIBLE);
                            addButton.setClickable(true);
                        } else {
                            barcodeResult.setText(R.string.status_manual_entry_invalid);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
    }

    public void numberEntry(Context c) {
        final EditText taskEditText = new EditText(c);
        taskEditText.setHint("ISBN");
        taskEditText.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout layout = new LinearLayout(c);
        layout.setPadding(48, 10, 48, 10);
        layout.addView(taskEditText);

        AlertDialog dialog = new AlertDialog.Builder(c)
                .setTitle("ISBN Entry")
                .setMessage("Enter the ISBN (No Dashes!)")
                .setView(layout)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        resetResult();

                        String task = String.valueOf(taskEditText.getText());
                        if (!task.isEmpty()) {
                            searchString = task;
                            new BookLookupTask(HomeFragment.this).execute(searchString);
                            barcodeResult.setText(getString(R.string.status_isbn, task));
                        } else {
                            barcodeResult.setText(R.string.status_isbn_invalid);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
    }

    public void addEntry() {
        dbManager = new DBManager(this.getContext());
        dbManager.open();
        try {
            if (dbManager.existenceCheck(activeTitle, activeAuthors)) {
                throw new DuplicateEntryException();
            } else {
                dbManager.insert(activeTitle, activeAuthors, isbn);
                Toast.makeText(getActivity().getApplicationContext(), "Added!", Toast.LENGTH_SHORT).show();
            }
        } catch (SQLException e) {
            Toast.makeText(getActivity().getApplicationContext(),
                    "Error adding to the database.", Toast.LENGTH_SHORT).show();
        } catch (DuplicateEntryException e) {
            Toast.makeText(getActivity().getApplicationContext(),
                    "Entry already exists!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onIsbnResolved(String isbn) {
        HomeFragment.isbn = isbn;
    }

    @Override
    public void onBookFound(String title, String author, String thumbnailUrl, BookMetadataLookupService.Source source) {
        activeTitle = title;
        activeAuthors = author;

        titleText.setText(title);
        authorText.setText(author);

        if (source == BookMetadataLookupService.Source.GOOGLE_BOOKS) {
            if (thumbnailUrl != null) {
                new ThumbnailTask(this).execute(thumbnailUrl);
            } else {
                thumb.setImageBitmap(null);
            }
        }
        // Neither LIBRARY_OF_CONGRESS nor HTML_FALLBACK ever resolves a thumbnail -- leave
        // whatever is showing as-is.

        addButton.setVisibility(View.VISIBLE);
        addButton.setClickable(true);
    }

    @Override
    public void onLookupError() {
        titleText.setText(R.string.status_lookup_error);
        authorText.setText("");
        thumb.setImageBitmap(null);
        addButton.setVisibility(View.INVISIBLE);
        addButton.setClickable(false);
    }

    @Override
    public void onThumbnailFetchResult(boolean fetchSucceeded, Bitmap bitmap) {
        if (fetchSucceeded) {
            thumbImg = bitmap;
        }
        thumb.setImageBitmap(thumbImg);
    }
}
