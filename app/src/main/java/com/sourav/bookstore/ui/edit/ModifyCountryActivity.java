package com.sourav.bookstore.ui.edit;

import android.view.View;
import android.view.MenuItem;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import com.sourav.bookstore.R;
import com.sourav.bookstore.data.DBManager;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class ModifyCountryActivity extends AppCompatActivity implements View.OnClickListener {
    private EditText titleText;
    private Button updateBtn, deleteBtn;
    private EditText authorText;
    private EditText isbnText;

    private long _id;

    private DBManager dbManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle("Modify Record");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

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

        updateBtn.setOnClickListener(this);
        deleteBtn.setOnClickListener(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
                confirmDelete();
                break;
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirmation_title)
                .setMessage(R.string.delete_confirmation_message)
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> {
                    dbManager.delete(_id);
                    returnHome();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public void returnHome() {
        finish();
    }
}
