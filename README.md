# BookStore

An Android app (Java) for cataloging a personal book collection. Add books by scanning a
barcode, typing an ISBN, or entering the title/author by hand; the app looks up the book's
metadata online and stores it locally in SQLite.

## Features

- **Barcode scan** — uses the device camera (Google Play Services Vision `BarcodeDetector`) to
  read an EAN-13 barcode and extract the ISBN.
- **Manual ISBN entry** — type an ISBN directly instead of scanning.
- **Manual text entry** — type a title/author pair directly, bypassing lookup.
- **Automatic metadata lookup** — given an ISBN, the app asks Open Library for title,
  author(s), and a cover, then Google Books, then the Library of Congress's SRU/MODS catalog,
  then Google Books again via its website (which finds self-published books); if all of those
  miss, it asks LibraryThing's `thingISBN` API for ISBNs of other editions of the same
  work and retries each against Open Library, then the Library of Congress.
- **"Do I already own this?"** — as soon as a scanned or entered book loads, the preview card
  says whether it's already in your library. Matching is fuzzy, so a different edition, a
  dropped subtitle or an author written back-to-front still counts: an ISBN hit reads "Already
  in your library", a close title/author hit reads "Looks like you already own this: <title>",
  and anything else reads "Not in your library yet".
- **Local library** — all saved books live in a local SQLite database (title, author, ISBN).
- **Browse, search & sort** — the "Books Stored" tab lists every stored book with an inline
  search box (matches title, author, or ISBN, filtering live as you type) and a compact sort
  icon (opens a popup menu: title/author/date-added, each ascending/descending); tapping a row
  opens an edit/delete screen.

## Tech stack

- Java, Android SDK (`minSdkVersion` 21, `compileSdkVersion`/`targetSdkVersion` 34)
- Gradle 8.9 with Android Gradle Plugin 8.7.3 (single `:app` module)
- AndroidX (AppCompat, Material Components, ConstraintLayout, Navigation, Lifecycle/ViewModel)
- Google Play Services Vision (barcode scanning)
- Raw `SQLiteOpenHelper` for persistence (no Room)
- Robolectric for JVM-level unit tests of the database layer (see [Testing](#testing))

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for how these pieces fit together, and
[`CLAUDE.md`](CLAUDE.md) for build commands and known rough edges when working in this repo.

## Project structure

The app is organized by responsibility into three top-level packages — `data` (persistence),
`network` (outbound HTTP + response parsing), and `ui` (one subpackage per screen) — rather than
one flat package. This keeps, for example, the fragile ISBN-lookup fallback chain isolated in
`network/` instead of tangled into the fragment that triggers it.

```
app/src/main/java/com/sourav/bookstore/
├── data/
│   ├── DatabaseHelper.java            # SQLiteOpenHelper, schema, singleton accessor
│   ├── DBManager.java                 # CRUD wrapper around the SQLite database
│   └── DuplicateEntryException.java   # Thrown when inserting an existing (title, author) pair
├── network/
│   ├── BookMetadataLookupService.java # Open Library / Google Books / LOC / LibraryThing fallback chain
│   ├── HttpFetcher.java               # The one HTTP GET every source makes (faked in tests)
│   ├── UrlHttpFetcher.java            # Production HttpFetcher (URL#openStream)
│   └── ThumbnailFetcher.java          # Downloads/decodes a book cover thumbnail
└── ui/
    ├── main/
    │   ├── MainActivity.java          # Hosts the two-tab ViewPager (Scan / Books Stored)
    │   ├── SectionsPagerAdapter.java  # Feeds HomeFragment / DatabaseFragment to the ViewPager
    │   └── PageViewModel.java         # Unused placeholder ViewModel
    ├── home/
    │   ├── HomeFragment.java          # Scan/entry UI; implements the task callbacks below
    │   ├── BookLookupTask.java        # AsyncTask wrapper around BookMetadataLookupService
    │   └── ThumbnailTask.java         # AsyncTask wrapper around ThumbnailFetcher
    ├── library/
    │   ├── DatabaseFragment.java      # Lists/searches/sorts stored books
    │   └── DatabaseViewModel.java     # Unused placeholder ViewModel (empty stub)
    ├── edit/
    │   └── ModifyCountryActivity.java # Edit/delete a stored book
    └── scan/
        └── ScanBarcodeActivity.java   # Camera preview + barcode detection
```

Screens still hand off data to each other as plain `String` `Intent` extras (`title`, `author`,
`isbn`, `id`) rather than a `Book` model/Parcelable — that's unchanged by this reorganization
and remains a known follow-up (see [Known limitations](#known-limitations)).

## Building & running

Requires a JDK 11+ (JDK 17/21 both work) and an Android SDK with API 34 and build-tools
installed. The Gradle wrapper (8.9) downloads Gradle itself automatically.

```sh
# Windows
gradlew.bat assembleDebug
gradlew.bat installDebug     # install on a connected device/emulator
gradlew.bat test             # local unit tests
gradlew.bat connectedAndroidTest  # instrumented tests (needs a device/emulator)

# macOS/Linux
./gradlew assembleDebug
```

Or open the project root in Android Studio and let it sync/run.

The app requires camera, internet, network-state, and (legacy) external-storage permissions —
see `app/src/main/AndroidManifest.xml`.

### API keys

The metadata lookup needs two API keys, read from `local.properties` (gitignored — never
committed) into generated `BuildConfig` fields at build time. Add these two lines alongside the
existing `sdk.dir` entry:

```properties
googleBooksApiKey=<your Google Books API key>
libraryThingApiKey=<your LibraryThing API key>
```

Without them, the build still succeeds (the fields default to an empty string) but every lookup
that needs a key fails at runtime with an authentication error from that API.

## Testing

`app/src/test/java/com/sourav/bookstore/data/DBManagerTest.java` covers the database layer
(insert, fetch, update, delete, author search, existence check) using
[Robolectric](http://robolectric.org/), which runs a real SQLite database on the JVM — no
emulator/device required. Robolectric also backs a set of regression tests under
`app/src/test/java/com/sourav/bookstore/ui/` (one per screen package: `main`, `library`, `edit`)
that exercise each Activity/Fragment against a real SQLite database and its actual
layout, covering the Intent-extra contracts between screens and the DB read/write paths:

```sh
gradlew.bat test              # or ./gradlew test
```

These JVM tests need `android.testOptions.unitTests.includeAndroidResources = true` (set in
`app/build.gradle`) so Robolectric can resolve the app's compiled theme/resources when inflating
layouts — without it, Activity/Fragment tests fail with theme- or resource-resolution errors even
though the code itself is correct.

There are no instrumented (`androidTest`) or UI tests yet; `connectedAndroidTest` currently only
runs the default template's package-name check.

The scan-to-result pipeline is covered offline: `network/BookMetadataLookupChainTest` drives the
whole fallback chain, and `ui/home/ScanToResultFlowTest` drives the real `HomeFragment` from a
scanned barcode or typed ISBN to what's shown and stored, both against API responses captured
into `app/src/test/resources/lookup/`. To check the real APIs themselves (needs network and the
API keys; skipped otherwise):

```sh
gradlew.bat testDebugUnitTest -PliveLookup --tests "*LiveLookupSmokeTest"
```

The camera barcode detector itself (`ScanBarcodeActivity`) still has no automated coverage —
test scanning manually on a device.

## Known limitations

- The `com.google.android.gms:play-services-vision` barcode API used for scanning is deprecated
  in favor of ML Kit.
- Database queries in `DBManager` are built via string concatenation rather than parameterized
  statements/bind args.
- `AsyncTask` (used for network calls in `HomeFragment`), `ViewModelProviders`, and
  `FragmentPagerAdapter` are all deprecated Android APIs.
- Test coverage is limited to the database layer — no tests for the network lookup fallback
  chain, barcode scanning, or UI flows.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#known-technical-debt) for details and
suggested follow-ups.
