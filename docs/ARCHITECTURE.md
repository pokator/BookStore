# Architecture

BookStore is a single-module Android app (Java, no Room/Retrofit/DI framework) built directly
on the Android SDK. Code is split by responsibility into three top-level packages:
`com.sourav.bookstore.data` (persistence), `com.sourav.bookstore.network` (outbound HTTP +
response parsing), and `com.sourav.bookstore.ui.*` (one subpackage per screen). There's still no
repository/service abstraction layer beyond that split — Activities and Fragments call into
`data`/`network` classes directly, and screens still pass data to each other as loose `String`
`Intent` extras rather than a shared `Book` model.

## Screen map

```
MainActivity
 └─ ViewPager (SectionsPagerAdapter, 2 tabs)
     ├─ Tab 0 "Scan"          → HomeFragment
     └─ Tab 1 "Books Stored"  → DatabaseFragment
                                   └─ tap a row → ModifyCountryActivity (edit/delete)

HomeFragment
 └─ FAB → rounded add-menu panel (fragment_main.xml's add_menu_panel), toggled by visibility
      (GONE/VISIBLE), not a separate PopupWindow -- it lives in the same ConstraintLayout as the
      FAB so the two can share one physical shape: the panel's paddingBottom extends its rounded
      card well past the FAB's top edge, and the FAB (drawn on top via elevation) visually sits
      docked inside the bottom of it, all under one shadow. A transparent full-screen `View`
      (add_menu_scrim), shown/hidden together with the panel, closes the menu when tapped so
      "tap outside to dismiss" doesn't need Dialog/PopupWindow window-level machinery. The FAB's
      "+" icon rotates 45° to read as "x" while open, and rotates back on close -- whether that
      came from tapping a row, tapping the scrim, or tapping the FAB again (all three funnel
      through `closeAddMenu()`). Three rows, each closing the menu first:
      ├─ Barcode Scanner → ScanBarcodeActivity (returns scanned ISBN via onActivityResult)
      ├─ ISBN Entry      → inline dialog for manual ISBN entry
      └─ Manual Entry    → inline dialog for manual title/author entry
      (any of the above populate a book preview card + "Add" button, which inserts into SQLite)
      The preview card also carries an ownership banner between the ISBN line and the Add
      button: amber "Already in your library" / "Looks like you already own this: <title>" or
      green "Not in your library yet", decided by `DBManager.findOwnedMatch` (see the data-layer
      section). It's informational only — Add still does its own exact `existenceCheck`.

DatabaseFragment
 ├─ inline search box (title/author/ISBN, live-filters as you type)
 ├─ inline sort icon → popup menu (title/author/date-added, each ascending/descending)
 └─ tap a row → ModifyCountryActivity (edit/delete)
```

`MainActivity` (`ui.main`) just wires the `ViewPager`/`TabLayout` to `SectionsPagerAdapter`,
which constructs `HomeFragment` (`ui.home`) and `DatabaseFragment` (`ui.library`). There's no
shared navigation/back-stack handling beyond what each `Activity`/`Fragment` does on its own —
screens pass data via `Intent` extras (`title`, `author`, `isbn`, `id`) as plain strings, not a
shared model class.

## Visual design

The app theme (`values/styles.xml`) is `Theme.MaterialComponents.Light.DarkActionBar` — plain
`Button`/`AlertDialog`/`PopupMenu` widgets get Material's rounded/ripple styling automatically
(via AppCompat's Material view inflater) without needing `MaterialButton`/etc. explicitly in
layouts. Colors (`values/colors.xml`) are a deep-navy/warm-gold palette (`colorPrimary`/
`colorAccent`) plus a warm cream `colorBackground` and white `colorSurface` for cards — chosen to
read as "library/literary" rather than a generic Material demo palette. `values/styles.xml` also
defines a small `TextAppearance.BookStore.*` type scale: `serif` (bold) for book-facing headings
and titles (the "Welcome!" header, book titles in the Scan-tab preview card and the Books Stored
rows), `sans-serif`/`sans-serif-medium` for UI chrome and body text. Both are system font-family
aliases (no bundled font files), so this works offline with no APK-size cost.

Because `TabLayout` in `activity_main.xml` sets its own `android:background="?attr/colorPrimary"`
rather than relying on `AppBarLayout`'s automatic Material tinting, it needs explicit
`app:tabTextColor`/`app:tabSelectedTextColor` — without them, unselected tab labels render in a
color that's illegible against the navy bar (this was caught live after the palette change, not
obvious from the XML alone).

## Data layer (`com.sourav.bookstore.data`)

- `DatabaseHelper` (`SQLiteOpenHelper`) defines a single table, `Books(_id, Title, Author,
  ISBN)`, and exposes itself as an application-context-scoped singleton via `getInstance()`.
- `DBManager` wraps a `DatabaseHelper` connection with `open()`/`close()` lifecycle methods and
  provides `insert`, `fetch` (all rows, unsorted), `search(keyword, sortColumn, ascending)`
  (title/author/ISBN matched via `LIKE`, OR'd, sorted by whichever of `TITLE`/`AUTHOR`/`_ID` the
  caller passes — backs `DatabaseFragment`'s inline search+sort), `update`, `delete`, and
  `existenceCheck` (used to avoid duplicate title+author inserts, signaled via
  `DuplicateEntryException`). Unlike the rest of this file, `search` is parameterized
  (`selectionArgs`, not string concatenation) — a deliberate choice made when it was added,
  since `orderBy` is built from a fixed, non-user-supplied column name.
- `OwnershipMatcher` + `DBManager.findOwnedMatch(title, author, isbn)` answer "does the user
  already own this book?" for the scan screen's ownership banner. `findOwnedMatch` scans the
  whole `Books` table (edit distance isn't expressible in SQL, and a personal library is small)
  and asks `OwnershipMatcher` about each row; `OwnershipMatcher` itself is pure static String
  logic with no Android/DB types, so it's unit-testable without Robolectric. Two tiers: an
  `ISBN` match (both sides normalized to ISBN-13, so a book's 10- and 13-digit printings match)
  is treated as certain; a `TITLE_AUTHOR` match (normalized titles within an edit-distance
  threshold, plus an overlapping author surname) is reported as a likely match. Differing ISBNs
  don't veto a title/author match — a different edition of a book you own is the case the fuzzy
  tier exists for. Distinct from `existenceCheck`, which stays exact because it *blocks* inserts;
  the fuzzy result only informs the UI.
- Every screen that touches the database creates its own `DBManager` instance and calls
  `open()` in `onCreate`/`onCreateView`; there's no shared/pooled connection or lifecycle-aware
  wrapper, and `close()` is rarely called explicitly.
- Queries in `DBManager` are built by string concatenation (with ad hoc `'` escaping on
  `search`/`authorSearch`/`existenceCheck`) rather than parameterized `selectionArgs`/bind
  arguments — functionally works for the app's own inputs but is fragile compared to
  parameterized queries.

## Book metadata lookup (`com.sourav.bookstore.network` + `ui.home`)

Three classes split this three ways:

- `network.BookMetadataLookupService` does the network fetch **and** interprets the response,
  returning structured `MetadataResult`/`BookMetadata` (title/author/thumbnail URL/source) —
  callers never see a raw JSON or HTML string.
- `ui.home.BookLookupTask` is a thin `AsyncTask` wrapper: it calls
  `BookMetadataLookupService.resolveMetadata` on a background thread and reports the outcome to
  a `Callback` interface (`onIsbnResolved`/`onBookFound`/`onLookupError`).
- `ui.home.ThumbnailTask` is the equivalent wrapper around `network.ThumbnailFetcher`.
- `HomeFragment` implements both `Callback` interfaces and owns all the actual UI mutation
  (setting text, showing/hiding the Add button, caching the last-fetched thumbnail) — it has no
  network or parsing code left in it at all.

Given an ISBN, `resolveMetadata` resolves book metadata in this order:

1. **Open Library Books API** — `GET https://openlibrary.org/api/books?format=json&jscmd=data&bibkeys=ISBN:{isbn}`
   (keyless). A miss is `{}`; a hit is parsed for `title`, every author's `name`, and
   `cover.medium` (`parseOpenLibraryJson`). This is first because, since late September 2026,
   Google Books returns `totalItems: 0` for every field-qualified query (`isbn:`, `intitle:`...)
   — plain-text queries still work, but a bare ISBN as plain text matches unrelated books.
2. **Google Books API** — `GET https://www.googleapis.com/books/v1/volumes?q=isbn:{isbn}&key={key}`.
   If this returns items, the JSON is parsed for `title`, `authors`, and a thumbnail URL. Kept so
   lookups recover on their own if Google fixes `isbn:` search.
3. **Library of Congress SRU/MODS catalog** for the scanned ISBN (details under step 6).
4. **Google Books, via its website** (`tryGoogleBooksPage`) — the route around the broken
   `isbn:` search. `GET https://books.google.com/books?vid=ISBN{isbn}` still resolves an ISBN
   (HTTP 404 if Google doesn't know it); the page's `<link rel="canonical" href="...?id={volumeId}">`
   gives the volume id (`parseGoogleBooksPageVolumeId`), and
   `GET https://www.googleapis.com/books/v1/volumes/{volumeId}?key={key}` returns its record
   (`parseGoogleVolumeJson`, same `volumeInfo` fields as step 2). The record is accepted only if
   its `industryIdentifiers` list the scanned ISBN and it has a title, since the id was scraped
   from HTML. This is what finds self-published (979-8 / Amazon KDP) ISBNs, which Open Library,
   LOC and LibraryThing generally don't carry. It sits behind the real APIs because it depends
   on Google's page markup: if that changes, the id stops matching and this step quietly misses.
5. **LibraryThing `thingISBN`** — only if steps 1–4 all missed. — `GET https://www.librarything.com/api/{key}/thingISBN/{isbn}`
   (**must be `https://`** — LibraryThing 301-redirects plain `http://` requests via Cloudflare,
   and `URL#openStream()` does not follow cross-protocol redirects, so an `http://` link here
   silently gets the redirect page instead of API data). Returns an
   `<idlist><isbn>...</isbn>...</idlist>` XML document listing ISBNs of other editions/
   translations of the same work, parsed with `DocumentBuilder` (`parseIsbnList`). Each of up to
   `MAX_RELATED_ISBNS_TO_TRY` (30) of those ISBNs is tried against **Open Library, then the
   Library of Congress**, before moving to the next candidate. This is deliberately last:
   LibraryThing's lists occasionally include an unrelated book (Catcher in the Rye's includes a
   psychology text), and a hit on any listed ISBN is trusted as-is.
6. **Library of Congress SRU/MODS catalog** — `GET http://lx2.loc.gov:210/lcdb?version=1.1&operation=searchRetrieve&query=bath.isbn={isbn}&maximumRecords=1&recordSchema=mods`
   (plain `http://`, confirmed not to redirect — unlike LibraryThing, don't "fix" this to
   `https://`). A miss is `<zs:numberOfRecords>0</zs:numberOfRecords>` with no `<zs:records>` at
   all; when LOC's backend is down it instead answers with an SRU diagnostic ("System
   temporarily unavailable") and no `<zs:numberOfRecords>`, which is also treated as a miss. On
   a hit, title/author are read from the `<mods>` record's **direct children only**
   (`parseLocMods`) — not via `getElementsByTagName`, which would also match `<name>`/`<title>`
   elements nested inside `<subject>`/`<relatedItem>` (e.g. a related work's author, or a
   fictional character used as a subject heading) that are not this book's actual author/title.
   MODS files a leading article separately (`<nonSort>The </nonSort><title>great
   Gatsby</title>`); `parseLocMods` rejoins them. This path never resolves a thumbnail — the
   MODS schema doesn't carry a cover-image URL.

(A Biblio.com HTML-title scrape used to follow as a last resort; Biblio.com now answers every
request with a Cloudflare bot challenge, so it was removed.)

All network I/O runs synchronously inside `doInBackground`-triggered calls (via
`BookLookupTask`), with blocking reads (10s connect / 15s read timeouts), through a single
`HttpFetcher.get(url)` (`UrlHttpFetcher` in production, which treats any non-2xx status as a
failure). Tests substitute `FakeHttpFetcher`, which serves responses
captured from the real APIs and records every URL requested. Each source is
fetch-and-parse-in-one (a `tryXxx`/`parseXxx` method pair returning `MetadataResult` or `null`)
rather than a shared content-sniffing parser.

Both API keys (Google Books, LibraryThing) are read from `local.properties` (gitignored) into
`BuildConfig.GOOGLE_BOOKS_API_KEY`/`LIBRARY_THING_API_KEY` at build time — see
[API keys](../README.md#api-keys) in the README. Neither is hardcoded in source.

`MetadataResult.resolvedIsbn` is set independently of whether metadata parsing succeeded — it
reflects whichever ISBN the fetch chain actually landed on (which can differ from the ISBN the
user entered/scanned, since step 4 re-resolves it), and is what ends up persisted with the
book. `ThumbnailTask.Result.fetchSucceeded` similarly distinguishes "fetch completed with a null
bitmap" from "fetch threw" — `HomeFragment` only overwrites its cached thumbnail on the former,
leaving the previous thumbnail on screen on the latter. Both classes' Javadoc explain why these
extra fields exist; read them before touching this code.

## Barcode scanning (`ui.scan.ScanBarcodeActivity`)

Uses `com.google.android.gms.vision.barcode.BarcodeDetector` + `CameraSource` to render a live
camera preview on a `SurfaceView` and detect EAN-13 barcodes. On the first detected barcode, it
returns the `Barcode` object to the caller via `setResult`/`Intent` extra and finishes.

## Known technical debt

- **Deprecated APIs**: `com.google.android.gms:play-services-vision` (barcode scanning) is
  deprecated in favor of ML Kit; `AsyncTask`, `ViewModelProviders`
  (→ `ViewModelProvider`/`by viewModels()`), and `FragmentPagerAdapter`
  (→ `FragmentStateAdapter` + `ViewPager2`) are all deprecated Android APIs still in use.
- **Unused ViewModels**: `PageViewModel` and `DatabaseViewModel` are instantiated but not
  actually used to hold UI state — `DatabaseViewModel` is an empty stub (`// TODO: Implement`).
- **String-concatenated SQL** in most of `DBManager` (`insert`/`update`/`delete`/`existenceCheck`)
  instead of parameterized queries — `search` is the one exception (added parameterized from the
  start).
- **No structured data model**: books are passed between screens as loose `String` `Intent`
  extras rather than a `Book` model/Parcelable.
- **Camera barcode detection has no automated tests** — everything from the detected barcode
  onwards (lookup chain, screen, storage) does, against captured API responses (see
  [Testing](../README.md#testing) in the README); the real APIs are only checked by the opt-in
  `LiveLookupSmokeTest`.
- **Duplicate/redundant Gradle dependencies** in `app/build.gradle` (e.g. `material`,
  `appcompat`, `legacy-support-v4`, and both `androidx.lifecycle:lifecycle-extensions` and the
  older `android.arch.lifecycle:*` artifacts are each declared more than once).
- **`android.nonFinalResIds=false`** is set in `gradle.properties` to keep `R.id`/`R.layout`
  fields as compile-time constants, because the codebase relies on `switch (view.getId())`
  throughout. Modern Android tooling defaults to non-final resource IDs for app modules; this
  flag is a deliberate compatibility shim, not something to remove without first converting
  those `switch` statements to `if`/`else` chains.
