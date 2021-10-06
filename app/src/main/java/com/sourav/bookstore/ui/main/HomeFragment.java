package com.sourav.bookstore.ui.main;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.sourav.bookstore.DBManager;
import com.sourav.bookstore.DuplicateEntryException;
import com.sourav.bookstore.R;
import com.sourav.bookstore.ScanBarcodeActivity;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.ui.AppBarConfiguration;

/**
 * A placeholder fragment containing a simple view.
 */
public class HomeFragment extends Fragment implements View.OnClickListener {

    private static final String ARG_SECTION_NUMBER = "section_number";
    private FloatingActionButton fab_main, fab_manual, fab_barcode, fab_isbn;
    private Animation fab_open, fab_close, fab_clock, fab_anticlock;
    private TextView textview_manual, textview_barcode, textView_isbn;
    private AppBarConfiguration mAppBarConfiguration;
    private TextView barcodeResult, titleText, authorText;
    private Bitmap thumbImg;
    private ImageView thumb;
    private Button addButton;

    private static String isbn;
    private String searchString;
    private int move = 0;
    private String line;
    private String m_Text = "";
    private int counter = 0;

    private String activeTitle;
    private String activeAuthors;
    private Boolean isOpen = false;
    private PageViewModel pageViewModel;
    private SQLiteDatabase database;
    final private String createCommand = "CREATE TABLE IF NOT EXISTS Books(id INT, Title VARCHAR NOT NULL, Author VARCHAR NOT NULL, PRIMARY KEY(id));";

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
        System.out.println("LOL NOPE YOU'RE HERE");

    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        System.out.println("You are here!");
        View view = inflater.inflate(R.layout.fragment_main, container, false);

        addButton = (Button) view.findViewById(R.id.add_button);
        barcodeResult = (TextView) view.findViewById(R.id.barcode_result);
        authorText = (TextView) view.findViewById(R.id.book_author);
        titleText = (TextView) view.findViewById(R.id.book_title);
        thumb = (ImageView) view.findViewById(R.id.thumb);

        if (savedInstanceState != null){
            authorText.setText(savedInstanceState.getString("author"));
            titleText.setText(savedInstanceState.getString("title"));

            thumbImg = (Bitmap)savedInstanceState.getParcelable("thumbPic");
            thumb.setImageBitmap(thumbImg);
        }

        addButton.setVisibility(View.INVISIBLE);
        addButton.setClickable(false);
        addButton.setOnClickListener(this);

        fab_main = view.findViewById(R.id.fab_original);
        fab_manual = view.findViewById(R.id.fab_manual);
        fab_barcode = view.findViewById(R.id.fab_barcode);
        fab_isbn = view.findViewById(R.id.fab_isbn);
        fab_close = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.fab_close);
        fab_open = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.fab_open);
        fab_clock = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.fab_rotate_clock);
        fab_anticlock = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), R.anim.fab_rotate_anticlock);

        textview_manual = (TextView) view.findViewById(R.id.textview_manual);
        textview_barcode = (TextView) view.findViewById(R.id.textview_barcode);
        textView_isbn = (TextView) view.findViewById(R.id.textview_isbn);

        fab_main.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                System.out.println("you got here...");
                if (isOpen) {
                    System.out.println("yay!");
                    fab_manual.setVisibility(View.INVISIBLE);
                    fab_barcode.setVisibility(View.INVISIBLE);
                    fab_isbn.setVisibility(View.INVISIBLE);
                    textview_manual.startAnimation(fab_close);
                    textview_barcode.startAnimation(fab_close);;
                    textView_isbn.startAnimation(fab_close);

                    fab_main.startAnimation(fab_anticlock);
                    fab_manual.setClickable(false);
                    fab_barcode.setClickable(false);
                    fab_isbn.setClickable(false);

                    isOpen = false;
                } else {
                    System.out.println("yay?");
                    fab_manual.setVisibility(View.VISIBLE);
                    fab_barcode.setVisibility(View.VISIBLE);
                    fab_isbn.setVisibility(View.VISIBLE);
                    textview_manual.startAnimation(fab_open);
                    textview_barcode.startAnimation(fab_open);;
                    textView_isbn.startAnimation(fab_open);
                    fab_main.startAnimation(fab_clock);
                    fab_manual.setClickable(true);
                    fab_barcode.setClickable(true);
                    fab_isbn.setClickable(true);

                    isOpen = true;
                }

            }
        });

        fab_manual.setOnClickListener(this);

        fab_barcode.setOnClickListener(this);

        fab_isbn.setOnClickListener(this);


        return view;
    }

    @Override
    public void onClick(View v){

        switch(v.getId()){
            case R.id.fab_manual:

                fab_manual.setVisibility(View.INVISIBLE);
                fab_barcode.setVisibility(View.INVISIBLE);
                fab_isbn.setVisibility(View.INVISIBLE);
                textview_manual.startAnimation(fab_close);
                textview_barcode.startAnimation(fab_close);;
                textView_isbn.startAnimation(fab_close);

                fab_main.startAnimation(fab_anticlock);
                fab_manual.setClickable(false);
                fab_barcode.setClickable(false);
                fab_isbn.setClickable(false);


                textEntry(getContext());
                break;
            case R.id.fab_barcode:
                fab_main.startAnimation(fab_anticlock);
                fab_manual.setVisibility(View.INVISIBLE);
                fab_barcode.setVisibility(View.INVISIBLE);
                fab_isbn.setVisibility(View.INVISIBLE);
                textview_manual.startAnimation(fab_close);
                textview_barcode.startAnimation(fab_close);;
                textView_isbn.startAnimation(fab_close);

                fab_manual.setClickable(false);
                fab_barcode.setClickable(false);
                fab_isbn.setClickable(false);
                isOpen = false;
                scanBarcode();

                break;
            case R.id.fab_isbn:
                fab_manual.setVisibility(View.INVISIBLE);
                fab_barcode.setVisibility(View.INVISIBLE);
                fab_isbn.setVisibility(View.INVISIBLE);
                textview_manual.startAnimation(fab_close);
                textview_barcode.startAnimation(fab_close);;
                textView_isbn.startAnimation(fab_close);

                fab_main.startAnimation(fab_anticlock);
                fab_manual.setClickable(false);
                fab_barcode.setClickable(false);
                fab_isbn.setClickable(false);
                isOpen = false;
                numberEntry(getContext());

                break;
            case R.id.add_button:
                if(isOpen){
                    fab_manual.setVisibility(View.INVISIBLE);
                    fab_barcode.setVisibility(View.INVISIBLE);
                    fab_isbn.setVisibility(View.INVISIBLE);
                    textview_manual.startAnimation(fab_close);
                    textview_barcode.startAnimation(fab_close);;
                    textView_isbn.startAnimation(fab_close);

                    fab_main.startAnimation(fab_anticlock);
                    fab_manual.setClickable(false);
                    fab_barcode.setClickable(false);
                    fab_isbn.setClickable(false);
                    isOpen = false;
                    addEntry();
                }
                else
                    addEntry();
                break;
        }
    }

    public void scanBarcode(){
        Intent intent = new Intent(this.getContext(), ScanBarcodeActivity.class);
        startActivityForResult(intent, 0);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == 0){
            if(requestCode == CommonStatusCodes.SUCCESS){
                if(data !=null) {
                    barcodeResult.setText("");
                    authorText.setText("");
                    titleText.setText("Book not found yet.");
                    thumb.setImageBitmap(null);
                    addButton.setVisibility(View.INVISIBLE);
                    addButton.setClickable(false);

                    Barcode barcode = data.getParcelableExtra("barcode");

                    if(barcode.displayValue != null && barcode.format == Barcode.EAN_13){
                        searchString = barcode.displayValue;
                        new GetBookInfo().execute(searchString);

                    } else {
                        Toast toast = Toast.makeText(getActivity().getApplicationContext(),
                                "Not a valid scan! Retry your scan.", Toast.LENGTH_SHORT);
                        toast.show();
                    }

                    barcodeResult.setText("ISBN: " + barcode.displayValue);
                    System.err.println(barcode.displayValue);
                }else {
                    barcodeResult.setText("No Barcode Found");
                }
            }
        }
        else{

        }
        super.onActivityResult(requestCode, resultCode, data);
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
        layout.setPadding(48,10,10,10);



        AlertDialog dialog = new AlertDialog.Builder(c)
                .setTitle("Text Entry")
                .setMessage("Enter the Author (separated by commas, if multiple) and Book Title")
                .setView(layout)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        barcodeResult.setText("");
                        authorText.setText("");
                        titleText.setText("Book not found yet.");
                        thumb.setImageBitmap(null);
                        addButton.setVisibility(View.INVISIBLE);
                        addButton.setClickable(false);

                        activeAuthors = String.valueOf(writerText.getText());
                        activeTitle = String.valueOf(bookText.getText());
                        if(activeAuthors !=null && activeTitle !=null) {
                            titleText.setText("Title: " + activeTitle);
                            authorText.setText("Author(s): " + activeAuthors);
                            addButton.setVisibility(View.VISIBLE);
                            addButton.setClickable(true);
                        }else {
                            barcodeResult.setText("Manual Entry Used. Retry Entry.");
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
        layout.setPadding(48,10,48,10);
        layout.addView(taskEditText);



        AlertDialog dialog = new AlertDialog.Builder(c)
                .setTitle("ISBN Entry")
                .setMessage("Enter the ISBN (No Dashes!)")
                .setView(layout)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        barcodeResult.setText("");
                        authorText.setText("");
                        titleText.setText("Book not found yet.");
                        thumb.setImageBitmap(null);
                        addButton.setVisibility(View.INVISIBLE);
                        addButton.setClickable(false);

                        String task = String.valueOf(taskEditText.getText());
                        if(task !=null) {
                            searchString = "http://www.librarything.com/api/thingISBN/"+ task;
                            new GetBookInfo().execute(searchString);

                            barcodeResult.setText("ISBN: " + task);
                        }else {
                            barcodeResult.setText("ISBN Entry Invalid");
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
    }

    public void addEntry()  {

//        database = getActivity().openOrCreateDatabase("Books", MODE_PRIVATE, null);
//        database.execSQL(createCommand);
        dbManager = new DBManager(this.getContext());
        dbManager.open();
        try{
            if(dbManager.existenceCheck(activeTitle, activeAuthors)){
                throw new DuplicateEntryException();
            } else {
                dbManager.insert(activeTitle, activeAuthors, isbn);
                Toast toast = Toast.makeText(getActivity().getApplicationContext(),
                        "Added!", Toast.LENGTH_SHORT);
                toast.show();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Error adding. Check your values.");
            Toast toast = Toast.makeText(getActivity().getApplicationContext(),
                    "Error adding to the database.", Toast.LENGTH_SHORT);
            toast.show();
        } catch (DuplicateEntryException e) {
            Toast toast = Toast.makeText(getActivity().getApplicationContext(),
                    "Entry already exists!", Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    private class GetBookInfo extends AsyncTask<String, Void, String> {

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        protected String doInBackground(String... isbns) {
            StringBuilder bookBuilder = new StringBuilder();

            //https://www.googleapis.com/books/v1/volumes?q=isbn:


            for (String  originalIsbn: isbns) {
                return getBookData(originalIsbn);
            }
            return "Error";
        }

        @SuppressLint("NewApi")
        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        protected void onPostExecute(String result){
            try{
                //parse results
                JSONObject resultObject = new JSONObject(result);
                JSONArray bookArray = resultObject.getJSONArray("items");
                JSONObject bookObject = bookArray.getJSONObject(0);
                JSONObject volumeObject = bookObject.getJSONObject("volumeInfo");
                try{
                    titleText.setText("TITLE: "+volumeObject.getString("title"));
                    activeTitle = volumeObject.getString("title");}
                catch(JSONException jse){
                    titleText.setText("");
                    activeTitle = "";
                    jse.printStackTrace();
                }

                StringBuilder authorBuild = new StringBuilder("");
                try{
                    JSONArray authorArray = volumeObject.getJSONArray("authors");
                    for(int a=0; a<authorArray.length(); a++){
                        if(a>0) authorBuild.append(", ");
                        authorBuild.append(authorArray.getString(a));
                    }
                    authorText.setText("AUTHOR(S): "+authorBuild.toString());
                    activeAuthors = authorBuild.toString();
                }
                catch(JSONException jse){
                    authorText.setText("");
                    activeAuthors = "";
                    jse.printStackTrace();
                }

                try{
                    JSONObject imageInfo = volumeObject.getJSONObject("imageLinks");
                    new GetBookThumb().execute(imageInfo.getString("smallThumbnail"));
                }
                catch(JSONException jse){
                    thumb.setImageBitmap(null);
                    jse.printStackTrace();
                }

                addButton.setVisibility(View.VISIBLE);
                addButton.setClickable(true);
            }
            catch (Exception e) {

                try {

                    String outputString = StringUtils.substringBetween(line, "<title>", "</title>");
                    System.out.println(outputString);

                    String[] splitter = outputString.split(" - ");

                    outputString = splitter[1];

                    String[] resplitter = outputString.split("by ");


                    authorText.setText("Author: " + resplitter[resplitter.length - 1]);
                    activeAuthors = resplitter[resplitter.length - 1];

                    String[] title = Arrays.copyOf(resplitter, resplitter.length - 1);

                    titleText.setText("Title: " + String.join(" ", title));
                    activeTitle = String.join(" ", title);

                    //URL thumbURL = new URL(StringUtils.substringBetween(line, "class=\"bookcover\"><a href=\"", "\" >"));
                    //thumb.setImageBitmap(BitmapFactory.decodeStream(thumbURL.openStream()));
                    System.out.println("4");

                    addButton.setVisibility(View.VISIBLE);
                    addButton.setClickable(true);

                    if(titleText.getText() == ""){
                        e.printStackTrace(System.err);
                        titleText.setText("Book not found.");
                        authorText.setText(":(");
                        thumb.setImageBitmap(null);
                        System.out.println("Shit");
                        addButton.setVisibility(View.INVISIBLE);
                        addButton.setClickable(false);
                    }

                }catch (Exception ex){
                    e.printStackTrace(System.err);
                    titleText.setText("Something went wrong. Retry (You sure you're scanning a book?");
                    authorText.setText(":(");
                    thumb.setImageBitmap(null);
                    System.out.println("Shit");
                    addButton.setVisibility(View.INVISIBLE);
                    addButton.setClickable(false);
                }
            }
        }




        @RequiresApi(api = Build.VERSION_CODES.N)
        protected String getBookData(String isbn){
            URL url;
            InputStream is = null;
            BufferedReader br;
            line = "\"totalItems\": 0";
            String libraryLine;
            String activeIsbn = isbn;
            final String libraryThingLink = "http://www.librarything.com/api/thingISBN/";
            final String googleApiLink = "https://www.googleapis.com/books/v1/volumes?q=isbn:";
            final String googleBooksLink = "https://books.google.com/books?vid=ISBN";
            final String biblioLink = "https://www.biblio.com/";
            int movement = 0;
            int counter = 0;

            try{
                url = new URL(googleApiLink + activeIsbn);
                is = url.openStream();  // throws an IOException
                br = new BufferedReader(new InputStreamReader(is));
                line = br.lines().collect(Collectors.joining());
                br.close();

                if(!line.contains("\"totalItems\": 0")) {
                    JSONObject resultObject = new JSONObject(line);
                    HomeFragment.isbn = activeIsbn;
                    return line;
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                url = new URL(libraryThingLink + activeIsbn);
                is = url.openStream();  // throws an IOException
                br = new BufferedReader(new InputStreamReader(is));
                line = br.lines().collect(Collectors.joining());
                libraryLine = line;
                br.close();

                System.out.println("LINE!! " + line);
                try{
                    for (int i = 0; i < 13; i++) {
                        if(line.charAt(53+i) != '<')
                            activeIsbn = activeIsbn + line.charAt(53 + i);
                        else throw new Exception();
                    }
                } catch(Exception e){
                    activeIsbn = "";
                    try{
                        for (int i = 0; i < 10; i++) {
                            if(line.charAt(53+i) != '<')
                                activeIsbn = activeIsbn + line.charAt(53 + i);
                            else throw new Exception();
                        }
                    } catch(Exception ex){

                    }
                } finally {
                    try {
                        url = new URL(googleApiLink + activeIsbn);
                        is = url.openStream();  // throws an IOException
                        br = new BufferedReader(new InputStreamReader(is));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            line = br.lines().collect(Collectors.joining());
                        }
                        br.close();
                        if (line.contains("\"totalItems\": 0"))
                            movement = movement + 23;
                        else {
                            HomeFragment.isbn = activeIsbn;
                            return line;
                        }
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            } catch (MalformedURLException mue) {
                mue.printStackTrace();
                return "URL Error";
            } catch (IOException ioe) {
                ioe.printStackTrace();
                return "IO Error";
            }

            line =  libraryLine;

            while (!line.contains("\"totalItems\": 1") && counter <= 30){
                    line = libraryLine;
                    activeIsbn = "";
                    if(52 + movement <= line.length()) {
                        if (line.charAt(51 + movement) == '>') {
                            for (int i = 0; i < 10; i++) {
                                activeIsbn = activeIsbn + line.charAt(52 + movement + i);
                            }
                        } else if (line.charAt(56) == '>' && movement == 0) {
                            for (int i = 0; i < 13; i++) {
                                activeIsbn = activeIsbn + line.charAt(57 + i);
                            }
                        } else break;
                    } else break;

                    System.out.println("Active ISBN!!!!! " + activeIsbn);


                try {
                    url = new URL(googleApiLink + activeIsbn);
                    is = url.openStream();  // throws an IOException
                    br = new BufferedReader(new InputStreamReader(is));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        line = br.lines().collect(Collectors.joining());
                    }
                    br.close();
                    if(line.contains("\"totalItems\": 0"))
                        movement = movement + 23;
                    else {
                        HomeFragment.isbn = activeIsbn;
                        return line;
                    }
                } catch (MalformedURLException mue) {
                    mue.printStackTrace();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                } finally {
                    try {
                        if (is != null) is.close();
                    } catch (IOException ioe) {
                        // nothing to see here
                    }
                }

                counter++;
            }

            counter = 0;
            movement = 0;

            while (counter < 30) {
                line = libraryLine;
                activeIsbn = "";
                if(52 + movement <= line.length()) {
                    if (line.charAt(51 + movement) == '>') {
                        for (int i = 0; i < 10; i++) {
                            activeIsbn = activeIsbn + line.charAt(52 + movement + i);
                        }
                    } else if (line.charAt(56) == '>' && movement == 0) {
                        for (int i = 0; i < 13; i++) {
                            activeIsbn = activeIsbn + line.charAt(57 + i);
                        }
                    } else break;
                } else break;

                System.out.println("Active ISBN!!!!! " + activeIsbn);
                try {
                    url = new URL(libraryThingLink + activeIsbn);
                    is = url.openStream();  // throws an IOException
                    br = new BufferedReader(new InputStreamReader(is));
                    line = br.lines().collect(Collectors.joining());
                    br.close();
                    if(line.charAt(51 + movement) == '>'){
                        for(int i = 0; i < 10; i++){
                            activeIsbn = activeIsbn + line.charAt(52 + movement + i);
                        }
                    } else if(line.charAt(56) == '>' && movement == 0) {
                        for(int i = 0; i < 13; i++){
                            activeIsbn = activeIsbn + line.charAt(57 + i);
                        }
                    } else break;

                    System.out.println(activeIsbn);
                } catch (MalformedURLException mue) {
                    mue.printStackTrace();
                    return "URL Error";
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    return "IO Error";
                } finally {
                    try {
                        if (is != null) is.close();
                    } catch (IOException ioe) {
                        // nothing to see here
                    }
                }

                try {
                    System.out.println("????????");

                    url = new URL(biblioLink + isbn);
                    System.out.println("12523465   " + biblioLink + isbn);
                    is = url.openStream();  // throws an IOException
                    br = new BufferedReader(new InputStreamReader(is));
                    line = br.lines().collect(Collectors.joining());
                    br.close();

                    //System.out.println(line);

                    if(line.contains("Page not Found"))
                        movement = movement + 23;
                    else {
                        HomeFragment.isbn = activeIsbn;
                        return line;
                    }
                } catch (IOException e) {
                    System.out.println("how did we get here?");
                }

                counter ++;
            }

            return null;
        }
    }

    private class GetBookThumb extends AsyncTask<String, Void, String> {
        //get thumbnail

        @Override
        protected String doInBackground(String... thumbURLs) {
            try{
                //try to download
                URL thumbURL = new URL(thumbURLs[0]);
                URLConnection thumbConn = thumbURL.openConnection();
                thumbConn.connect();

                InputStream thumbIn = thumbConn.getInputStream();
                BufferedInputStream thumbBuff = new BufferedInputStream(thumbIn);

                thumbImg = BitmapFactory.decodeStream(thumbBuff);
                thumbBuff.close();
                thumbIn.close();
            }
            catch(Exception e) {
                e.printStackTrace();
            }

            return "";
        }

        @Override
        protected void onPostExecute(String s) {
            thumb.setImageBitmap(thumbImg);
        }
    }
}