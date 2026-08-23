package com.sourav.bookstore.ui.library;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.sourav.bookstore.R;
import com.sourav.bookstore.data.DBManager;
import com.sourav.bookstore.data.DatabaseHelper;
import com.sourav.bookstore.ui.edit.ModifyCountryActivity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

/**
 * Lists stored books with an inline search box (matches title, author, or ISBN) and a compact
 * sort button (title/author/date-added, each ascending/descending, via a popup menu) -- replaces
 * the old flow of navigating out to a separate author-search screen and then a separate
 * author's-books screen.
 */
public class DatabaseFragment extends Fragment {

    private DatabaseViewModel mViewModel;

    private DBManager dbManager;

    private ListView listView;
    private EditText searchEditText;
    private ImageButton sortButton;

    final String[] from = new String[] { DatabaseHelper._ID,
            DatabaseHelper.TITLE, DatabaseHelper.AUTHOR, DatabaseHelper.ISBN};

    final int[] to = new int[] { R.id.id, R.id.title, R.id.desc, R.id.isbn};

    private String currentSortColumn = DatabaseHelper.TITLE;
    private boolean currentSortAscending = true;

    public static DatabaseFragment newInstance() {
        return new DatabaseFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_emp_list, container, false);

        dbManager = new DBManager(this.getContext());
        dbManager.open();

        listView = v.findViewById(R.id.list_view);
        listView.setEmptyView(v.findViewById(R.id.empty));

        searchEditText = v.findViewById(R.id.search_edittext);
        sortButton = v.findViewById(R.id.sort_button);

        refreshList();

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshList();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        sortButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PopupMenu popupMenu = new PopupMenu(getContext(), sortButton);
                popupMenu.inflate(R.menu.sort_options);
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        int id = item.getItemId();
                        if (id == R.id.sort_title_asc) {
                            currentSortColumn = DatabaseHelper.TITLE;
                            currentSortAscending = true;
                        } else if (id == R.id.sort_title_desc) {
                            currentSortColumn = DatabaseHelper.TITLE;
                            currentSortAscending = false;
                        } else if (id == R.id.sort_author_asc) {
                            currentSortColumn = DatabaseHelper.AUTHOR;
                            currentSortAscending = true;
                        } else if (id == R.id.sort_author_desc) {
                            currentSortColumn = DatabaseHelper.AUTHOR;
                            currentSortAscending = false;
                        } else if (id == R.id.sort_date_newest) {
                            currentSortColumn = DatabaseHelper._ID;
                            currentSortAscending = false;
                        } else if (id == R.id.sort_date_oldest) {
                            currentSortColumn = DatabaseHelper._ID;
                            currentSortAscending = true;
                        } else {
                            return false;
                        }
                        refreshList();
                        return true;
                    }
                });
                popupMenu.show();
            }
        });

        // OnCLickListiner For List Items
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long viewId) {
                TextView idTextView = (TextView) view.findViewById(R.id.id);
                TextView titleTextView = (TextView) view.findViewById(R.id.title);
                TextView authorTextView = (TextView) view.findViewById(R.id.desc);
                TextView isbnTextView = (TextView) view.findViewById((R.id.isbn));

                String id = idTextView.getText().toString();
                String title = titleTextView.getText().toString();
                String author = authorTextView.getText().toString();
                String isbn = isbnTextView.getText().toString();

                Intent modify_intent = new Intent(getActivity().getApplicationContext(), ModifyCountryActivity.class);
                modify_intent.putExtra("title", title);
                modify_intent.putExtra("author", author);
                modify_intent.putExtra("isbn", isbn);
                modify_intent.putExtra("id", id);

                startActivity(modify_intent);
            }
        });

        return v;
    }

    private void refreshList() {
        String keyword = searchEditText.getText().toString();

        Cursor cursor = dbManager.search(keyword, currentSortColumn, currentSortAscending);
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this.getContext(), R.layout.activity_view_record, cursor, from, to, 0);
        listView.setAdapter(adapter);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = ViewModelProviders.of(this).get(DatabaseViewModel.class);
        // TODO: Use the ViewModel
    }

//    //Deprecated Method, need to find a future fix
    @Deprecated
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser){
        super.setUserVisibleHint(isVisibleToUser);
        if(isVisibleToUser){
            getActivity().getSupportFragmentManager().beginTransaction().detach(this).attach(this).commit();
        }
    }
}
