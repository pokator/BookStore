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

public class AuthorBooksActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener {

    private DBManager dbManager;

    private ListView listView;

    private SimpleCursorAdapter adapter;

    final String[] from = new String[] {DatabaseHelper._ID, DatabaseHelper.TITLE, DatabaseHelper.ISBN};

    final int[] to = new int[] { R.id.id_result, R.id.books_result, R.id.isbn_result};

    FloatingActionButton fab;
    Intent intent;
    String author;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        intent = getIntent();
        author = intent.getStringExtra("author");

        System.out.println(author);

        setTitle("Books by " + author);

        setContentView(R.layout.fragment_author_books);

        fab = (FloatingActionButton) findViewById(R.id.fab_result);
        fab.setOnClickListener(this);


        dbManager = new DBManager(this);
        dbManager.open();
        Cursor cursor = dbManager.authorSearch(author);

        listView = (ListView) findViewById(R.id.list_view_result);
        listView.setEmptyView(findViewById(R.id.empty_result));
        listView.setOnItemClickListener(this);

        adapter = new SimpleCursorAdapter(this, R.layout.activity_author_books, cursor, from, to, 0);
        adapter.notifyDataSetChanged();

        listView.setAdapter(adapter);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fab_result:
                finish();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        TextView idTextView = (TextView) view.findViewById(R.id.id_result);
        TextView titleTextView = (TextView) view.findViewById(R.id.books_result);
        TextView isbnTextView = (TextView) view.findViewById((R.id.isbn_result));

        String _id = idTextView.getText().toString();
        String title = titleTextView.getText().toString();
        String isbn = isbnTextView.getText().toString();

        Intent modify_intent = new Intent(getApplicationContext(), ModifyCountryActivity.class);
        modify_intent.putExtra("title", title);
        modify_intent.putExtra("author", author);
        modify_intent.putExtra("isbn", isbn);
        modify_intent.putExtra("id", _id);

        startActivity(modify_intent);
    }
}
