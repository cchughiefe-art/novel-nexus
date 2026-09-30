# NovelFull APK review and Novel Nexus changes

Inspected the uploaded NovelFull 1.3.8 APK's Android layout resources. `fragment_home` contains a featured area and separate horizontal hot and popular sections plus a latest list; `activity_filter` has genre filtering. `activity_novel` splits info and chapters; `fragment_novel_chapter` includes fast chapter navigation. The chapter reader has a drawer, top and bottom controls, a theme dialog, and a font selection list. The packaged fonts include several book faces and OpenDyslexic, but those font files are not copied into Novel Nexus.

Novel Nexus already had a chapter drawer, chapter search, downloads, themes, reading modes, and several navigation controls. This update adds a separate popular feed sourced from the verified Parse `-chapterCount` query, genre browsing from the verified genre filter, and persistent Serif/Sans/Mono reader font choices, including per-book overrides. It removes the backend source label from book cards, featured cards, details, and reader error screens; source selection remains in Settings.

The APK's list of fonts and UI assets are reference material, not redistributed assets. The catalog and chapter queries still need an on-device connection check. The GitHub Actions build verifies compilation, but cannot prove server availability or the visual result on the user's device.
