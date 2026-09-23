# NovelAtlas Android

A native Android multi-source novel reader built for approved direct-retrieval sources. It uses Kotlin + Jetpack Compose and keeps source-specific retrieval code behind a plugin-style `NovelSource` interface.

## Included sources

- NovelFull.com
- NovelFull.net
- FreeWebNovel.com
- LightNovelPub.me

NovelNow and Empire Novel are intentionally not included in this build. More providers can be plugged in without changing the reader or offline storage. See `docs/ADDING_A_SOURCE.md`.

## Implemented in this build

- Unified multi-source search with per-source failure isolation
- Latest/discovery feed from providers that expose it
- Novel metadata + chapter list parsing
- Clean chapter reader
- Native Jetpack Compose UI
- Font size controls in the reader
- SQLite offline novel/chapter storage
- Full-novel background downloads
- Four concurrent chapter download workers inside each download job
- Resume-safe downloads: existing chapters are skipped
- Offline library
- Source attribution throughout the UI
- Plugin registry for adding/removing sources
- GitHub Actions APK build

## GitHub build

Push this repository to GitHub. The workflow at `.github/workflows/android.yml` runs tests and builds an installable debug APK on every push to `main` or `master`.

In GitHub:

1. Open **Actions**.
2. Open **Build Android APK**.
3. Run the workflow or push a commit.
4. When it succeeds, open the build run.
5. Download the `NovelAtlas-debug-apk` artifact.

The APK inside the artifact is `app-debug.apk` and is signed with the Android debug key by the GitHub runner, so it can be installed for testing.

## Local structure

```text
app/src/main/java/com/novelatlas/app/
├── core/
│   ├── model/
│   ├── network/
│   └── source/          # NovelSource + SourceRegistry
├── source/
│   ├── novelfull/
│   ├── freewebnovel/
│   └── lightnovelpub/
├── data/
│   ├── db/
│   ├── repo/
│   └── worker/
└── ui/
    ├── components/
    ├── navigation/
    ├── screens/
    └── theme/
```

## Important retrieval note

Websites change. Each source is isolated so a provider selector change only requires updating that provider adapter. The app does not depend on a remote NovelAtlas backend.

LightNovelPub has historically changed hosts/protection behavior. Its adapter therefore uses multiple selector/search fallbacks and surfaces a source-specific error rather than taking down the whole app.
