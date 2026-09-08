# CLAUDE.md

Guidance for working in this repository. See also [`README.md`](README.md) (overview, features,
build steps) and [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (how the app fits together and
known technical debt).

## What this is

BookStore: a single-module Android app (Java) that scans/enters ISBNs, looks up book metadata
online (Google Books, with LibraryThing/Biblio.com fallbacks), and stores the result in a local
SQLite database. No Room, no DI framework, no network client library — everything is hand-rolled
`SQLiteOpenHelper`/`HttpURLConnection`.

## Build & test

```sh
# Windows (this repo's default environment)
gradlew.bat assembleDebug          # build debug APK
gradlew.bat installDebug           # install on connected device/emulator
gradlew.bat test                   # JVM unit tests (app/src/test), incl. Robolectric DB tests
gradlew.bat connectedAndroidTest   # instrumented tests (needs a device/emulator)
```

`gradlew`/`gradlew.bat` use the wrapper pinned in `gradle/wrapper/gradle-wrapper.properties`
(Gradle 8.9) with Android Gradle Plugin 8.7.3 — any JDK 11+ works (verified against JDK 21).
`compileSdkVersion`/`targetSdkVersion` is 34, `minSdkVersion` is 21 (AGP 8 requires ≥21).
`local.properties` (gitignored) must point `sdk.dir` at a local Android SDK containing platform
34 and matching build-tools; create it if missing. It also carries `googleBooksApiKey` and
`libraryThingApiKey` (read into `BuildConfig.GOOGLE_BOOKS_API_KEY`/`LIBRARY_THING_API_KEY` by
`app/build.gradle`) — the build succeeds without them, but the metadata lookup fails at runtime
if they're missing.

`build.gradle` (root) uses `google()` + `mavenCentral()` — `jcenter()` was removed since it has
been shut down.

## Code layout

Organized by responsibility, not as one flat package — see `docs/ARCHITECTURE.md` for the full
picture:

- `app/src/main/java/com/sourav/bookstore/data/` — `DBManager`/`DatabaseHelper`
  (single `Books` table: `_id`, `Title`, `Author`, `ISBN`) + `DuplicateEntryException` +
  `OwnershipMatcher` (the fuzzy "do I already own this?" rules behind
  `DBManager.findOwnedMatch`).
- `app/src/main/java/com/sourav/bookstore/network/` — `BookMetadataLookupService` (the Google
  Books/LibraryThing/Biblio.com fallback chain, returning structured metadata) and
  `ThumbnailFetcher`, both called from `ui/home/BookLookupTask.java`/`ThumbnailTask.java`
  (thin `AsyncTask` wrappers that report results back to `HomeFragment` via a callback
  interface — `HomeFragment` itself has no network/parsing code).
- `app/src/main/java/com/sourav/bookstore/ui/` — one subpackage per screen: `main` (MainActivity
  + SectionsPagerAdapter), `home` (HomeFragment), `library` (DatabaseFragment — lists/searches/
  sorts stored books inline, no separate search screen), `edit` (ModifyCountryActivity), `scan`
  (ScanBarcodeActivity).
- `app/src/main/res/` — standard Android resources (layouts, drawables, strings, styles).

Test sources under `app/src/test/java/com/sourav/bookstore/` mirror this package structure.

## Conventions & gotchas already in the codebase

- **DB access pattern**: every screen creates its own `new DBManager(context)` and calls
  `open()` directly in `onCreate`/`onCreateView`. `DatabaseHelper.getInstance()` is a
  process-wide singleton keyed on application context — reuse this pattern rather than
  introducing a new persistence approach unless asked to.
- **Data passed between screens** is loose `String` `Intent` extras (`"title"`, `"author"`,
  `"isbn"`, `"id"`) — there is no `Book` model class. Match existing extras' keys/types when
  touching navigation to/from `ModifyCountryActivity`.
- **SQL is string-concatenated**, not parameterized, in most of `DBManager` (with manual `'`
  escaping on user-supplied strings) — except `search(keyword, sortColumn, ascending)`, which
  uses `selectionArgs` properly (a deliberate choice: it's the query backing the Books Stored
  tab's search box, built fresh rather than copying the concatenation pattern). Don't add new
  concatenated queries; if touching the other methods, prefer parameterized `selectionArgs` —
  but do this as a deliberate, scoped fix, not an incidental change.
- **Two different "already have this?" checks, on purpose**: `DBManager.existenceCheck(title,
  author)` is an exact equality test and *blocks* an insert (`DuplicateEntryException`);
  `DBManager.findOwnedMatch(title, author, isbn)` is fuzzy (`data.OwnershipMatcher`: ISBN-13
  normalization, then normalized-title edit distance + author surname overlap) and only feeds the
  scan screen's ownership banner. Don't collapse them into one: fuzzy rules produce likely, not
  certain, matches, and letting one veto an insert would block legitimate adds. If you loosen a
  threshold in `OwnershipMatcher`, add the case to `OwnershipMatcherTest` — it deliberately pins
  both the matches it must make (subtitles, articles, reordered names, other editions) and the
  near-misses it must not (same author different book, same title different author).
- **Metadata lookup fallback chain** (`network.BookMetadataLookupService.resolveMetadata`,
  called from `ui.home.BookLookupTask.doInBackground`): Google Books directly (keyed), then
  LibraryThing's `thingISBN` API for related-edition ISBNs (real XML parsing via
  `DocumentBuilder`, capped at `MAX_RELATED_ISBNS_TO_TRY`) — each candidate tried against Google
  Books, then the Library of Congress SRU/MODS catalog if that misses too — then Biblio.com's
  HTML `<title>` as the final last resort (still fixed-offset/`substringBetween`-based, still
  fragile). Each source's `tryXxx`/`parseXxx` method pair does its own fetch-and-parse and
  returns `null` on any miss/failure so the chain moves on; there's no cross-source
  content-sniffing. Treat changes here as high-risk and test manually (see below) rather than
  assuming logic is self-evidently correct from reading it once.
- **LibraryThing must be called over `https://`**, not `http://` — LibraryThing 301-redirects
  plain-HTTP requests to HTTPS via Cloudflare, and `URL#openStream()` does not follow
  cross-protocol redirects, so an `http://` link there silently returns the redirect page instead
  of API data (this was the root cause of the "ISBN Entry" lookup always failing before this was
  fixed). The Library of Congress endpoint, by contrast, is `http://` and does *not* redirect —
  confirmed directly; don't "fix" it to `https://` without re-checking.
- **`parseLocMods` reads `<mods>`'s direct children only**, never `getElementsByTagName` for
  title/author — the real LOC response nests `<name>`/`<title>` elements inside `<subject>`/
  `<relatedItem>` too (a related work's author, a subject heading's fictional character name),
  and those are not the book's own title/author. See its Javadoc and
  `BookMetadataLookupServiceTest` for a response shaped exactly like this trap.
- Several dependencies are declared twice in `app/src/main/../app/build.gradle` (residue from
  template edits) — harmless duplication, not a sign of an intentional multi-flavor split.
- **`switch (view.getId())`/`switch (R.id.x)`** is used throughout instead of `if`/`else`. This
  only compiles because `android.nonFinalResIds=false` is set in `gradle.properties`
  (modern AGP defaults resource ID fields to non-final for app modules, which breaks `switch`).
  Don't remove that flag without converting the switches first.

## Testing reality check

`app/src/test/java/com/sourav/bookstore/data/DBManagerTest.java` gives real coverage of
`DBManager` (insert/fetch/update/delete/search+sort/existenceCheck) via Robolectric,
which runs against a real SQLite database on the JVM (`ApplicationProvider.getApplicationContext()`,
`@RunWith(RobolectricTestRunner.class)`) — no emulator needed. `DatabaseHelper` is a
process-wide singleton, so tests wipe the `Books` table in `@Before` **and close the DBManager in
`@After`** to stay isolated from each other regardless of execution order — skipping the
`@After` close causes "Illegal connection pointer" failures in later tests in the same class,
since Robolectric tears down its native SQLite sandbox between test methods but the singleton's
cached connection handle survives in the JVM unless explicitly closed.

`app/src/test/java/com/sourav/bookstore/ui/{main,library,edit,home}/` hold one Robolectric test per
screen package, built on the same real-SQLite pattern, that build the actual Activity/Fragment
and its real layout and check the Intent-extra contracts and DB reads/writes end to end (the
`library` one now also covers the search box's live filtering and the sort popup menu's row
ordering). `HomeFragmentTest` is the exception to "HomeFragment has no coverage": it drives the
`BookLookupTask.Callback` methods directly (no network) and asserts only the ownership banner's
state, and its host `FragmentActivity` needs `activity.setTheme(R.style.AppTheme)` before the
fragment is committed or `fragment_main`'s Material FAB fails to inflate. These need `android.testOptions.unitTests.includeAndroidResources = true` (already set in
`app/build.gradle`) — without it, Robolectric can't resolve the app's compiled
theme/resources and Activity/Fragment tests fail with theme- or layout-inflation errors that
look unrelated to the actual code change.

`app/src/test/java/com/sourav/bookstore/network/BookMetadataLookupServiceTest.java` covers
`parseIsbnList` (the LibraryThing `<idlist>` XML parsing) and `parseLocMods` (the LOC MODS
parsing, including a canned response shaped to trip up naive `getElementsByTagName` use — see
above) against canned XML strings — pure parsing, no network, plain JUnit (no Robolectric
needed). Everything else that actually hits the network — the Google Books/LibraryThing/LOC/
Biblio.com calls themselves, barcode scanning, and the FAB/dialog UI flows in `HomeFragment` —
has no automated coverage. Don't assume `gradlew test`
passing means a UI/network change works; build and run the app (`installDebug`) and manually
exercise the affected flow (scan a barcode / enter an ISBN / add-edit-delete a book) before
calling that work complete. When adding new testable logic, prefer extending these Robolectric/
plain-JUnit patterns over reaching for instrumented (`androidTest`) tests, since those require a
connected device/emulator this environment doesn't have.

Note for live-network verification in this environment specifically: the Google Books API's
unauthenticated endpoint is frequently rate-limited (HTTP 429) from this host's outbound IP —
don't take a 429 here as evidence of a code defect; it reproduces on the sandboxed JVM but not
necessarily on a real device/network.
