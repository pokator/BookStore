package com.sourav.bookstore.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * {@link HttpFetcher} backed by {@link HttpURLConnection}. Lines are joined with no separator,
 * exactly as each source's own fetch code used to do before this was extracted.
 *
 * <p>Any non-2xx status is an IOException -- including a 3xx, since HttpURLConnection won't
 * follow a redirect that changes protocol (http to https) and would otherwise hand back the
 * redirect page as if it were the response. Timeouts are bounded so one hung source (LOC's port
 * 210 is sometimes unreachable) can't stall the whole lookup chain.
 */
public class UrlHttpFetcher implements HttpFetcher {

    static final int CONNECT_TIMEOUT_MS = 10_000;
    static final int READ_TIMEOUT_MS = 15_000;

    @Override
    public String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " from " + connection.getURL().getHost());
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    content.append(line);
                }
                return content.toString();
            }
        } finally {
            connection.disconnect();
        }
    }
}
