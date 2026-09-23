# Adding a new novel source

Novel Nexus deliberately keeps every website behind `NovelSource`. The reader, downloader, database and navigation do not know site-specific selectors.

## 1. Create one adapter

Create a package under:

`app/src/main/java/com/novelnexus/app/source/<site>/`

Implement:

```kotlin
class ExampleSource(private val http: HttpClient) : NovelSource {
    override val id = "example"
    override val name = "Example Novels"
    override val baseUrl = "https://example.com"

    override suspend fun search(query: String, page: Int): List<NovelCard> = TODO()
    override suspend fun latest(page: Int): List<NovelCard> = TODO()
    override suspend fun novel(url: String): NovelDetails = TODO()
    override suspend fun chapters(url: String): List<ChapterRef> = TODO()
    override suspend fun chapter(ref: ChapterRef): ChapterContent = TODO()
}
```

If a site has no latest/popular endpoint, set `supportsLatest = false` or `supportsPopular = false`.

## 2. Register it

In `NovelNexusApp.kt`, add one line to the `SourceRegistry` list:

```kotlin
ExampleSource(http)
```

That is all the application layer needs. Search, detail, reader and offline download can now use the source.

## Adapter rules

- Keep site-specific selectors inside the adapter.
- Return absolute URLs.
- Remove scripts, ads and unrelated navigation from chapter content.
- Preserve paragraphs; do not flatten the whole chapter into one line.
- Respect the provider's approved access method and pacing.
- Use stable selectors with fallbacks rather than one brittle CSS selector.
- Throw meaningful errors when the provider is unavailable. The UI isolates a failing source instead of failing the whole app.
