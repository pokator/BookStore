package com.sourav.bookstore.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test double for {@link HttpFetcher}: answers each request from canned responses keyed by a URL
 * fragment (first registered fragment the URL contains wins), and records every URL requested so
 * tests can assert which sources were asked, in what order. A URL nothing was registered for
 * fails with an IOException -- i.e. "unroutable" behaves like the network being down, so a test
 * never silently hits a real API.
 */
public final class FakeHttpFetcher implements HttpFetcher {

    private static final Object FAILURE = new Object();

    private final Map<String, Object> routes = new LinkedHashMap<>();
    public final List<String> requests = new ArrayList<>();

    public FakeHttpFetcher respond(String urlFragment, String body) {
        routes.put(urlFragment, body);
        return this;
    }

    /** The request throws, as an HTTP error status or a dropped connection would. */
    public FakeHttpFetcher fail(String urlFragment) {
        routes.put(urlFragment, FAILURE);
        return this;
    }

    @Override
    public String get(String url) throws IOException {
        requests.add(url);
        for (Map.Entry<String, Object> route : routes.entrySet()) {
            if (url.contains(route.getKey())) {
                if (route.getValue() == FAILURE) {
                    throw new IOException("simulated failure for " + url);
                }
                return (String) route.getValue();
            }
        }
        throw new IOException("no canned response for " + url);
    }

    /** How many requests went to URLs containing {@code urlFragment}. */
    public int countRequests(String urlFragment) {
        int count = 0;
        for (String url : requests) {
            if (url.contains(urlFragment)) {
                count++;
            }
        }
        return count;
    }
}
