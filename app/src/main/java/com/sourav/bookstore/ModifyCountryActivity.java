package com.sourav.bookstore;

import android.view.View;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class ModifyCountryActivity extends AppCompatActivity implements View.OnClickListener {
    private EditText titleText;
    private Button updateBtn, deleteBtn;
    private EditText authorText;
    private EditText isbnText;

    private long _id;

    private DBManager dbManager;

    private FloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle("Modify Record");

        setContentView(R.layout.activity_modify_record);

        dbManager = new DBManager(this);
        dbManager.open();

        titleText = (EditText) findViewById(R.id.subject_edittext);
        authorText = (EditText) findViewById(R.id.author_edittext);
        isbnText = (EditText) findViewById(R.id.isbn_edittext);

        updateBtn = (Button) findViewById(R.id.btn_update);
        deleteBtn = (Button) findViewById(R.id.btn_delete);

        Intent intent = getIntent();
        String id = intent.getStringExtra("id");
        String name = intent.getStringExtra("title");
        String author = intent.getStringExtra("author");
        String isbn = intent.getStringExtra("isbn");

        _id = Long.parseLong(id);

        titleText.setText(name);
        authorText.setText(author);
        isbnText.setText(isbn);

        fab = (FloatingActionButton) findViewById(R.id.fab_modify);
        fab.setOnClickListener(this);

        updateBtn.setOnClickListener(this);
        deleteBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_update:
                String title = titleText.getText().toString();
                String desc = authorText.getText().toString();
                String isbn = isbnText.getText().toString();

                dbManager.update(_id, title, desc, isbn);
                this.returnHome();
                break;
            case R.id.btn_delete:
                dbManager.delete(_id);
                this.returnHome();
                break;
            case R.id.fab_modify:
                finish();
        }
    }

    public void returnHome() {
        finish();
    }
}
