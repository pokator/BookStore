package com.sourav.bookstore.ui.main;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.sourav.bookstore.AuthorSearchActivity;
import com.sourav.bookstore.DBManager;
import com.sourav.bookstore.DatabaseHelper;
import com.sourav.bookstore.ModifyCountryActivity;
import com.sourav.bookstore.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;

public class DatabaseFragment extends Fragment implements View.OnClickListener{

    private DatabaseViewModel mViewModel;

    private DBManager dbManager;

    private ListView listView;

    private SimpleCursorAdapter adapter;

    final String[] from = new String[] { DatabaseHelper._ID,
            DatabaseHelper.TITLE, DatabaseHelper.AUTHOR, DatabaseHelper.ISBN};

    final int[] to = new int[] { R.id.id, R.id.title, R.id.desc, R.id.isbn};

    private FloatingActionButton fab;
    private View v;

    private boolean shouldRefreshOnResume = false;

    public static DatabaseFragment newInstance() {
        return new DatabaseFragment();
    }



    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        v = inflater.inflate(R.layout.fragment_emp_list, container, false);

        dbManager = new DBManager(this.getContext());
        dbManager.open();
        Cursor cursor = dbManager.fetch();

        listView = (ListView) v.findViewById(R.id.list_view);
        listView.setEmptyView(v.findViewById(R.id.empty));

        adapter = new SimpleCursorAdapter(this.getContext(), R.layout.activity_view_record, cursor, from, to, 0);
        adapter.notifyDataSetChanged();

        listView.setAdapter(adapter);

        fab = v.findViewById(R.id.fab_test);
        fab.setOnClickListener(this);

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

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = ViewModelProviders.of(this).get(DatabaseViewModel.class);
        // TODO: Use the ViewModel
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()){
            case R.id.fab_test:
                Intent add_mem = new Intent(this.getContext(), AuthorSearchActivity.class);
                startActivity(add_mem);

        }
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
