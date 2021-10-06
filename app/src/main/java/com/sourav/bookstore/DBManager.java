package com.sourav.bookstore;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.provider.ContactsContract;

import com.sourav.bookstore.ui.main.DatabaseFragment;

import java.sql.PreparedStatement;

public class DBManager {
    private DatabaseHelper dbHelper;

    private Context context;

    private SQLiteDatabase database;

    public DBManager(Context c) {
        context = c;
    }

    public DBManager open() throws SQLException {
        dbHelper = DatabaseHelper.getInstance(context);
        database = dbHelper.getWritableDatabase();
        return this;
    }

    public void close() {
        dbHelper.close();
    }

    public void insert(String title, String author, String isbn) {
        ContentValues contentValue = new ContentValues();
        contentValue.put(DatabaseHelper.TITLE, title);
        contentValue.put(DatabaseHelper.AUTHOR, author);
        contentValue.put(DatabaseHelper.ISBN, isbn);
        database.insert(DatabaseHelper.TABLE_NAME, null, contentValue);
    }

    public Cursor fetch() {
        String[] columns = new String[] { DatabaseHelper._ID, DatabaseHelper.TITLE, DatabaseHelper.AUTHOR, DatabaseHelper.ISBN};
        Cursor cursor = database.query(DatabaseHelper.TABLE_NAME, columns, null, null, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
        }
        return cursor;
    }

    public Cursor search(String keyword){
        keyword = keyword.replaceAll("'","''");
        String query = "SELECT " + DatabaseHelper._ID + ", " + DatabaseHelper.AUTHOR + " FROM " + DatabaseHelper.TABLE_NAME + " WHERE " + DatabaseHelper.AUTHOR + " LIKE '%" + keyword + "%' GROUP BY " + DatabaseHelper.AUTHOR +";";
        Cursor cursor = database.rawQuery(query, null);

        if(cursor.getCount() <= 0){
            cursor.close();
            return null;
        }else{
            return cursor;
        }
    }

    public Cursor authorSearch(String keyword){
        keyword = keyword.replaceAll("'","''");
        String query = "SELECT " + DatabaseHelper._ID + ", " + DatabaseHelper.TITLE + ", " + DatabaseHelper.ISBN + " FROM " + DatabaseHelper.TABLE_NAME + " WHERE " + DatabaseHelper.AUTHOR + " LIKE '%" + keyword + "%'";
        Cursor cursor = database.rawQuery(query, null);
        if(cursor.getCount() <= 0){
            cursor.close();
            return null;
        }else{
            return cursor;
        }
    }

    public int update(long _id, String title, String author, String isbn) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DatabaseHelper.TITLE, title);
        contentValues.put(DatabaseHelper.AUTHOR, author);
        contentValues.put(DatabaseHelper.ISBN, isbn);
        int i = database.update(DatabaseHelper.TABLE_NAME, contentValues, DatabaseHelper._ID + " = " + _id, null);
        return i;
    }

    public void delete(long _id) {
        database.delete(DatabaseHelper.TABLE_NAME, DatabaseHelper._ID + "=" + _id, null);
    }

    public boolean existenceCheck(String title, String author) {

        title = title.replaceAll("'","''");
        author = author.replaceAll("'","''");

        String Query = "SELECT * FROM " + DatabaseHelper.TABLE_NAME + " WHERE " + DatabaseHelper.TITLE + " = '" + title + "' AND " + DatabaseHelper.AUTHOR + " = '" + author + "';";
        Cursor cursor = database.rawQuery(Query, null);
        if(cursor.getCount() <= 0){
            cursor.close();
            return false;
        }
        cursor.close();
        return true;
    }
}
