package com.sourav.bookstore.network;

import java.io.IOException;

/**
 * The one network call every metadata source makes: GET a URL and hand back the body as a
 * string. {@link BookMetadataLookupService} takes one of these so tests can substitute canned
 * responses for the real APIs; production code uses {@link UrlHttpFetcher}.
 */
public interface HttpFetcher {

    /**
     * @throws IOException on any connection failure or non-2xx response -- sources treat that
     *                     the same as a miss and let the chain move on.
     */
    String get(String url) throws IOException;
}
