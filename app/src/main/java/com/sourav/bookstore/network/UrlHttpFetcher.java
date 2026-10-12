package com.sourav.bookstore.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

/**
 * {@link HttpFetcher} backed by {@link URL#openStream()}. Lines are joined with no separator,
 * exactly as each source's own fetch code used to do before this was extracted.
 */
public class UrlHttpFetcher implements HttpFetcher {

    @Override
    public String get(String url) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new URL(url).openStream(), "UTF-8"))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line);
            }
            return content.toString();
        }
    }
}
