package com.sourav.bookstore.network;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * Downloads and decodes a book cover thumbnail. Extracted verbatim from
 * HomeFragment.GetBookThumb.doInBackground.
 */
public class ThumbnailFetcher {

    public Bitmap fetch(String url) throws IOException {
        URL thumbURL = new URL(url);
        URLConnection thumbConn = thumbURL.openConnection();
        thumbConn.connect();

        InputStream thumbIn = thumbConn.getInputStream();
        BufferedInputStream thumbBuff = new BufferedInputStream(thumbIn);

        Bitmap bitmap = BitmapFactory.decodeStream(thumbBuff);
        thumbBuff.close();
        thumbIn.close();
        return bitmap;
    }
}
