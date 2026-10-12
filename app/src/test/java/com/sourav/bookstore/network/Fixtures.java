package com.sourav.bookstore.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads the canned API responses under {@code src/test/resources/lookup/}. Most were captured
 * from the live APIs on 2026-10-11 (see each file); joined into one line the same way
 * {@link UrlHttpFetcher} delivers them.
 */
public final class Fixtures {

    private Fixtures() {
    }

    public static String read(String name) {
        try (InputStream in = Fixtures.class.getClassLoader().getResourceAsStream("lookup/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("missing fixture lookup/" + name);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toString("UTF-8").replace("\r", "").replace("\n", "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
