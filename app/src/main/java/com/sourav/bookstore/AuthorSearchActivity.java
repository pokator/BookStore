package com.sourav.bookstore;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cursoradapter.widget.SimpleCursorAdapter;

public class AuthorSearchActivity extends AppCompatActivity implements TextWatcher, View.OnClickListener {

    private EditText authorEditText;
    private DBManager dbManager;

    private ListView listView;

    private SimpleCursorAdapter adapter;

    final String[] from = new String[] { DatabaseHelper._ID, DatabaseHelper.AUTHOR,};

    final int[] to = new int[] { R.id.id_search, R.id.author_search};

    private FloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle("Search");

        setContentView(R.layout.fragment_author_search);

        fab = (FloatingActionButton) findViewById(R.id.fab_search);
        fab.setOnClickListener(this);

        authorEditText = (EditText) findViewById(R.id.author_edittext);
        authorEditText.addTextChangedListener(this);

        dbManager = new DBManager(this);
        dbManager.open();
        Cursor cursor = dbManager.search(authorEditText.getText().toString());

        listView = (ListView) findViewById(R.id.list_view_search);
        listView.setEmptyView(findViewById(R.id.empty_search));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                TextView authorTextView = (TextView) view.findViewById(R.id.author_search);

                String author = authorTextView.getText().toString();

                Intent booksIntent = new Intent(getApplicationContext(), AuthorBooksActivity.class);
                booksIntent.putExtra("author", author);

                startActivity(booksIntent);
            }
        });

        adapter = new SimpleCursorAdapter(this, R.layout.activity_search, cursor, from, to, 0);
        adapter.notifyDataSetChanged();

        listView.setAdapter(adapter);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fab_search:
                finish();
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        Cursor cursor = dbManager.search(authorEditText.getText().toString());
        listView = (ListView) findViewById(R.id.list_view_search);
        listView.setEmptyView(findViewById(R.id.empty_search));
        adapter = new SimpleCursorAdapter(this, R.layout.activity_search, cursor, from, to, 0);
        adapter.notifyDataSetChanged();
        listView.setAdapter(adapter);
    }

    @Override
    public void afterTextChanged(Editable s) {

    }
}
