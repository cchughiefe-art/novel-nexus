# NovelFull app Parse provider

The `novelfull-app` source is separate from the NovelFull.com and NovelFull.net HTML sources. It uses Retrofit with Gson, Parse POST requests with `_method: GET`, and the headers supplied for the authorized API. Its installation ID is generated once per Novel Nexus installation and retained in app preferences; reusing one captured device ID across every installation would conflate clients.

The source exposes `fetchTrending`, `fetchByGenre`, `searchNovels`, and `fetchNovelDetails`, and implements `NovelSource` so existing search, home, details, chapters, downloads, and reader flows can use it. The genre method is available for a future genre UI. The provided sample only verifies Novel queries; Chapter filtering and content follow the APK's Parse model (`novel` pointer, `index`, `content`) and need an on-device check against this server.

Only `mapp4u.com` permits cleartext HTTP in Android's network security configuration. This provider requires a working connection to port 1337; other sources retain the default HTTPS policy. No chapter view counter is called during reading.

The server could not be reached from the development workspace, and an Android build was unavailable here. Check search, a novel's chapter catalog, reading, and offline downloads on an Android device before publishing a signed release. If a provider request fails, Novel Nexus shows the source error while other sources remain available.
