package com.sourav.bookstore.network;

import com.sourav.bookstore.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Given an ISBN, resolves book metadata in this order: the scanned ISBN itself against Open
 * Library (keyless, and the only source that still answers ISBN queries reliably since Google
 * Books' {@code isbn:} search started returning zero results in late September 2026), then
 * Google Books, then the Library of Congress's SRU/MODS catalog, then Google Books again by a
 * route that doesn't depend on the broken {@code isbn:} search (see {@link #tryGoogleBooksPage};
 * it's what finds self-published 979-8 ISBNs, which none of the other catalogs carry). Only if
 * all of those miss is LibraryThing's thingISBN API asked for the ISBNs of other editions of the same work (a simple
 * {@code <idlist><isbn>...</isbn>...</idlist>} XML document), each of which is tried against
 * Open Library, then LOC, until one hits. Related editions are a last resort on purpose:
 * LibraryThing's lists occasionally include an unrelated book (Catcher in the Rye's lists a
 * psychology text), and any hit on one is trusted as-is. (Biblio.com's page title used to be a
 * final fallback; it now sits behind a Cloudflare bot challenge, so it was removed.) {@link #resolveMetadata(String)} is the single
 * entry point. Each source is responsible for fetching *and* interpreting its own response --
 * callers only ever see structured {@link BookMetadata}, never raw JSON/XML/HTML.
 */
public class BookMetadataLookupService {

    private static final String OPEN_LIBRARY_LINK = "https://openlibrary.org/api/books?format=json&jscmd=data&bibkeys=ISBN:";
    private static final String LIBRARY_THING_LINK = "https://www.librarything.com/api/" + BuildConfig.LIBRARY_THING_API_KEY + "/thingISBN/";
    private static final String GOOGLE_API_LINK = "https://www.googleapis.com/books/v1/volumes?q=isbn:";
    private static final String GOOGLE_API_KEY_PARAM = "&key=" + BuildConfig.GOOGLE_BOOKS_API_KEY;
    private static final String LOC_LINK = "http://lx2.loc.gov:210/lcdb?version=1.1&operation=searchRetrieve&query=bath.isbn=";
    private static final String LOC_LINK_SUFFIX = "&maximumRecords=1&recordSchema=mods";
    private static final String GOOGLE_BOOKS_PAGE_LINK = "https://books.google.com/books?vid=ISBN";
    private static final String GOOGLE_VOLUME_LINK = "https://www.googleapis.com/books/v1/volumes/";

    /** The volume id in a Google Books page's {@code <link rel="canonical" href="...?id=zBSL0QEACAAJ"/>}. */
    private static final Pattern CANONICAL_VOLUME_ID =
            Pattern.compile("rel=\"canonical\"[^>]*?href=\"[^\"]*[?&]id=([A-Za-z0-9_-]+)");

    /**
     * A popular work can have hundreds of related editions in LibraryThing's idlist; bound how
     * many we're willing to try against Open Library/LOC so a lookup can't turn into hundreds of
     * sequential network round-trips.
     */
    static final int MAX_RELATED_ISBNS_TO_TRY = 30;

    private final HttpFetcher http;

    public BookMetadataLookupService() {
        this(new UrlHttpFetcher());
    }

    public BookMetadataLookupService(HttpFetcher http) {
        this.http = http;
    }

    public enum Source {
        OPEN_LIBRARY,
        GOOGLE_BOOKS,
        LIBRARY_OF_CONGRESS
    }

    public static class BookMetadata {
        public final String title;
        public final String author;
        public final String thumbnailUrl;

        BookMetadata(String title, String author, String thumbnailUrl) {
            this.title = title;
            this.author = author;
            this.thumbnailUrl = thumbnailUrl;
        }
    }

    /**
     * {@code metadata} is non-null only when a title/author were actually resolved (from any
     * source). {@code resolvedIsbn} is set independently of that -- it reflects whichever ISBN
     * the fetch chain actually landed on (which can differ from the ISBN passed in, since a
     * related edition's ISBN may be the one that resolved), and can be non-null even when {@code
     * metadata} is null.
     */
    public static class MetadataResult {
        public final Source source;
        public final BookMetadata metadata;
        public final String resolvedIsbn;

        private MetadataResult(Source source, BookMetadata metadata, String resolvedIsbn) {
            this.source = source;
            this.metadata = metadata;
            this.resolvedIsbn = resolvedIsbn;
        }

        private static MetadataResult found(Source source, BookMetadata metadata, String resolvedIsbn) {
            return new MetadataResult(source, metadata, resolvedIsbn);
        }

        private static MetadataResult error(String resolvedIsbn) {
            return new MetadataResult(null, null, resolvedIsbn);
        }

        public boolean isError() {
            return metadata == null;
        }
    }

    public MetadataResult resolveMetadata(String isbn) {
        return lookup(normalizeIsbn(isbn));
    }

    /**
     * Strips the spaces/hyphens people type and upper-cases an ISBN-10's check digit: Open
     * Library treats {@code 014023750x} as a different (unknown) key from {@code 014023750X}.
     */
    static String normalizeIsbn(String isbn) {
        return isbn.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private MetadataResult lookup(String isbn) {
        MetadataResult openLibrary = tryOpenLibrary(isbn);
        if (openLibrary != null) {
            return openLibrary;
        }

        MetadataResult google = tryGoogleBooks(isbn);
        if (google != null) {
            return google;
        }

        MetadataResult loc = tryLibraryOfCongress(isbn);
        if (loc != null) {
            return loc;
        }

        MetadataResult googlePage = tryGoogleBooksPage(isbn);
        if (googlePage != null) {
            return googlePage;
        }

        List<String> relatedIsbns = fetchRelatedIsbns(isbn);
        int attempts = Math.min(relatedIsbns.size(), MAX_RELATED_ISBNS_TO_TRY);
        for (int i = 0; i < attempts; i++) {
            String candidate = relatedIsbns.get(i);

            MetadataResult openLibraryResult = tryOpenLibrary(candidate);
            if (openLibraryResult != null) {
                return openLibraryResult;
            }

            MetadataResult locResult = tryLibraryOfCongress(candidate);
            if (locResult != null) {
                return locResult;
            }
        }

        return MetadataResult.error(null);
    }

    MetadataResult tryOpenLibrary(String isbn) {
        try {
            return parseOpenLibraryJson(http.get(OPEN_LIBRARY_LINK + isbn), isbn);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parses an Open Library Books API response ({@code jscmd=data}), e.g. (trimmed):
     * <pre>{@code
     * {"ISBN:9780316769488": {
     *   "title": "The Catcher in the Rye",
     *   "authors": [{"name": "J. D. Salinger", "url": "..."}],
     *   "cover": {"small": "https://covers.openlibrary.org/b/id/15172531-S.jpg", "medium": "...-M.jpg", ...}
     * }}
     * }</pre>
     * A miss is just {@code {}}. A record with no title is treated as a miss too, since there'd
     * be nothing to show or store.
     */
    MetadataResult parseOpenLibraryJson(String content, String isbn) {
        try {
            JSONObject book = new JSONObject(content).optJSONObject("ISBN:" + isbn);
            if (book == null) {
                return null;
            }
            String title = stringOrNull(book, "title");
            if (title == null || title.isEmpty()) {
                return null;
            }

            StringBuilder author = new StringBuilder();
            JSONArray authors = book.optJSONArray("authors");
            if (authors != null) {
                for (int a = 0; a < authors.length(); a++) {
                    // Skip a malformed entry rather than losing the whole record over it.
                    JSONObject authorObject = authors.optJSONObject(a);
                    String name = authorObject != null ? stringOrNull(authorObject, "name") : null;
                    if (name == null || name.isEmpty()) {
                        continue;
                    }
                    if (author.length() > 0) author.append(", ");
                    author.append(name);
                }
            }

            JSONObject cover = book.optJSONObject("cover");
            String thumbnailUrl = cover != null ? stringOrNull(cover, "medium") : null;

            return MetadataResult.found(Source.OPEN_LIBRARY, new BookMetadata(title, author.toString(), thumbnailUrl), isbn);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * {@code object.optString(key, null)} would return the string "null" for a JSON null on
     * Android (its org.json differs from json.org's here), so check for that explicitly.
     */
    private static String stringOrNull(JSONObject object, String key) {
        return object.isNull(key) ? null : object.optString(key, null);
    }

    MetadataResult tryGoogleBooks(String isbn) {
        try {
            String content = http.get(GOOGLE_API_LINK + isbn + GOOGLE_API_KEY_PARAM);

            if (content.contains("\"totalItems\": 0")) {
                return null;
            }
            return parseGoogleBooksJson(content, isbn);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private MetadataResult parseGoogleBooksJson(String content, String isbn) {
        try {
            JSONObject resultObject = new JSONObject(content);
            JSONArray bookArray = resultObject.getJSONArray("items");
            JSONObject bookObject = bookArray.getJSONObject(0);
            return volumeInfoToResult(bookObject.getJSONObject("volumeInfo"), isbn);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private MetadataResult volumeInfoToResult(JSONObject volumeObject, String isbn) {
        try {
            String title;
            try {
                title = volumeObject.getString("title");
            } catch (JSONException jse) {
                title = "";
                jse.printStackTrace();
            }

            String author;
            try {
                JSONArray authorArray = volumeObject.getJSONArray("authors");
                StringBuilder authorBuild = new StringBuilder("");
                for (int a = 0; a < authorArray.length(); a++) {
                    if (a > 0) authorBuild.append(", ");
                    authorBuild.append(authorArray.getString(a));
                }
                author = authorBuild.toString();
            } catch (JSONException jse) {
                author = "";
                jse.printStackTrace();
            }

            String thumbnailUrl;
            try {
                JSONObject imageInfo = volumeObject.getJSONObject("imageLinks");
                thumbnailUrl = imageInfo.getString("smallThumbnail");
            } catch (JSONException jse) {
                thumbnailUrl = null;
                jse.printStackTrace();
            }

            return MetadataResult.found(Source.GOOGLE_BOOKS, new BookMetadata(title, author, thumbnailUrl), isbn);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Fallback for Google Books' {@code isbn:} search, which has returned zero results for every
     * query since late September 2026 even though the books are still in Google's catalog. The
     * public page {@code books.google.com/books?vid=ISBN<isbn>} still resolves an ISBN (404 if
     * Google doesn't know it), and its canonical link carries the volume id, which the volumes
     * API answers normally. This reads an id out of a web page, so it's a fallback behind the
     * real APIs: if Google changes the page, {@link #parseGoogleBooksPageVolumeId} stops finding
     * an id and this quietly becomes a miss.
     */
    MetadataResult tryGoogleBooksPage(String isbn) {
        try {
            String volumeId = parseGoogleBooksPageVolumeId(http.get(GOOGLE_BOOKS_PAGE_LINK + isbn));
            if (volumeId == null) {
                return null;
            }
            String content = http.get(GOOGLE_VOLUME_LINK + volumeId + "?key=" + BuildConfig.GOOGLE_BOOKS_API_KEY);
            return parseGoogleVolumeJson(content, isbn);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    static String parseGoogleBooksPageVolumeId(String html) {
        Matcher matcher = CANONICAL_VOLUME_ID.matcher(html);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Parses a single-volume response ({@code /volumes/<id>}: the same {@code volumeInfo} as one
     * search result, without the {@code items} wrapper). The volume must list {@code isbn} among
     * its {@code industryIdentifiers} and have a title -- the id came from scraping a page, so
     * this guards against landing on some other book.
     */
    MetadataResult parseGoogleVolumeJson(String content, String isbn) {
        try {
            JSONObject volumeInfo = new JSONObject(content).getJSONObject("volumeInfo");
            String title = stringOrNull(volumeInfo, "title");
            if (title == null || title.isEmpty() || !listsIsbn(volumeInfo, isbn)) {
                return null;
            }
            return volumeInfoToResult(volumeInfo, isbn);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static boolean listsIsbn(JSONObject volumeInfo, String isbn) {
        JSONArray identifiers = volumeInfo.optJSONArray("industryIdentifiers");
        if (identifiers == null) {
            return false;
        }
        for (int i = 0; i < identifiers.length(); i++) {
            JSONObject identifier = identifiers.optJSONObject(i);
            if (identifier != null && isbn.equalsIgnoreCase(identifier.optString("identifier"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Asks LibraryThing's thingISBN API for the ISBNs of other editions/translations of the same
     * work, e.g.:
     * <pre>{@code
     * <idlist>
     * <isbn>0312995458</isbn>
     * <isbn>0312289901</isbn>
     * ...
     * </idlist>
     * }</pre>
     * Returns an empty list (never null) if the request fails or no related ISBNs are found, so
     * callers can iterate the result unconditionally.
     */
    List<String> fetchRelatedIsbns(String isbn) {
        try {
            String xml = http.get(LIBRARY_THING_LINK + isbn);
            return parseIsbnList(xml);
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    List<String> parseIsbnList(String xml) {
        List<String> isbns = new ArrayList<>();
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            NodeList isbnNodes = document.getElementsByTagName("isbn");
            for (int i = 0; i < isbnNodes.getLength(); i++) {
                isbns.add(isbnNodes.item(i).getTextContent());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isbns;
    }

    MetadataResult tryLibraryOfCongress(String isbn) {
        try {
            String xml = http.get(LOC_LINK + isbn + LOC_LINK_SUFFIX);
            return parseLocMods(xml, isbn);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parses a Library of Congress SRU response in MODS format, e.g. (trimmed):
     * <pre>{@code
     * <zs:searchRetrieveResponse xmlns:zs="http://www.loc.gov/zing/srw/">
     * <zs:numberOfRecords>1</zs:numberOfRecords>
     * <zs:records><zs:record><zs:recordData>
     * <mods xmlns="http://www.loc.gov/mods/v3">
     *   <titleInfo><title>Robert Ludlum's The Altman code</title></titleInfo>
     *   <name type="personal" usage="primary"><namePart>Lynds, Gayle.</namePart></name>
     *   ...
     *   <relatedItem><name type="personal"><namePart>Ludlum, Robert,</namePart></name></relatedItem>
     * </mods>
     * </zs:recordData></zs:record></zs:records>
     * </zs:searchRetrieveResponse>
     * }</pre>
     * A miss is {@code <zs:numberOfRecords>0</zs:numberOfRecords>} with no {@code <zs:records>}
     * at all. The parser is namespace-unaware (same as {@link #parseIsbnList}), so the SRW
     * wrapper elements keep their literal {@code zs:} prefix in {@code getNodeName()} -- that's
     * why {@code "zs:numberOfRecords"} is looked up by its full prefixed name below.
     *
     * <p>Title/author are read from {@code <mods>}'s <em>direct</em> children only, not via
     * {@code getElementsByTagName} -- that would also match the {@code <name>}/{@code <title>}
     * elements nested inside {@code <subject>}/{@code <relatedItem>} (e.g. a related work's
     * author, or a fictional character's name used as a subject heading), neither of which is
     * this book's actual title/author.
     */
    MetadataResult parseLocMods(String xml, String isbn) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));

            NodeList countNodes = document.getElementsByTagName("zs:numberOfRecords");
            if (countNodes.getLength() == 0 || "0".equals(countNodes.item(0).getTextContent())) {
                return null;
            }

            NodeList modsNodes = document.getElementsByTagName("mods");
            if (modsNodes.getLength() == 0) {
                return null;
            }
            Element mods = (Element) modsNodes.item(0);

            String title = "";
            Element titleInfo = firstDirectChildElement(mods, "titleInfo");
            if (titleInfo != null) {
                Element titleElement = firstDirectChildElement(titleInfo, "title");
                if (titleElement != null) {
                    title = titleElement.getTextContent();
                }
                // MODS files a leading article separately so catalogs can sort past it
                // (<nonSort>The  </nonSort><title>great Gatsby</title>); put it back.
                Element nonSort = firstDirectChildElement(titleInfo, "nonSort");
                if (nonSort != null && !nonSort.getTextContent().trim().isEmpty()) {
                    String article = nonSort.getTextContent();
                    String separator = Character.isWhitespace(article.charAt(article.length() - 1)) ? " " : "";
                    title = (article.trim() + separator + title).trim();
                }
            }

            String author = "";
            NodeList children = mods.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals("name")) {
                    Element nameElement = (Element) node;
                    if ("primary".equals(nameElement.getAttribute("usage"))) {
                        Element namePart = firstDirectChildElement(nameElement, "namePart");
                        if (namePart != null) {
                            author = namePart.getTextContent();
                        }
                        break;
                    }
                }
            }

            return MetadataResult.found(Source.LIBRARY_OF_CONGRESS, new BookMetadata(title, author, null), isbn);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Element firstDirectChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(tagName)) {
                return (Element) node;
            }
        }
        return null;
    }
}
