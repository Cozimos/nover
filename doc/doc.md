# TextForge

TextForge is a Markdown novel reader built with Kotlin and Jetpack Compose. It imports text/Markdown novels, indexes chapter boundaries by UTF-8 byte offsets, parses one requested chapter at a time, and restores reading progress.

## Project structure

```text
TextForge/
├── app/                 # Android entry point and manifest
├── parser/              # Markdown parser and chapter byte index
├── storage/             # Source files, metadata, index and progress
├── ui/
│   ├── library/         # Library Model, View and import picker
│   ├── navigation/      # Library/Reader navigation
│   ├── reader/          # Reader Model, page View, panels and pagination
│   ├── dictionary/      # StarDict data and lookup
│   └── components/      # Reusable text and HTML rendering
└── doc/
```

The project has four Gradle modules: `app`, `ui`, `storage` and `parser`. Small navigation and import modules were folded into `ui` to avoid maintaining separate modules for a handful of files.

## MVI

The feature flow follows **Model (State) → View → Intent**:

- **Model / State** — one immutable snapshot describing what the screen should show (`ReaderState` and `LibraryState`).
- **View** — Compose functions render that snapshot and do not own feature data (`ReaderScreen`, `ReaderPanels`, `LibraryScreen`).
- **Intent** — a sealed description of a user action (`ReaderIntent`, `LibraryIntent`).
- **Reducer** — converts an intent or effect result into the next immutable state.
- **Effects** — ViewModels perform file, parser and dictionary work asynchronously, then feed results back as intents.

`ReaderScreen` renders the page and reports gestures. Reader controls, menus and dialogs are in `ReaderPanels`. Reader state, intents, reducer and asynchronous operations are in `ReaderModel`.

All reader geometry and typography are grouped in one `ReaderLayout` state. Width, height, density, font scale and font size travel through the same layout update rather than independent size-related state variables.

The Library follows the same state/intent pattern. The document picker only returns a `Uri`; `LibraryViewModel` owns import, refresh and delete operations.

## Data and storage

`NovelStore` persists only:

```text
files/
└── novels/
    └── {novelId}/
        ├── source.md
        ├── metadata.json
        ├── index.json
        └── progress.json
```

- `source.md` is the original imported text.
- `metadata.json` stores the novel identity and display title.
- `index.json` stores chapter titles and UTF-8 byte ranges.
- `progress.json` stores the current chapter, block/byte position and page.

Parsed chapter JSON files, neighbour prefetch, chapter windows and persistent parsing caches are intentionally not used. The reader loads and parses one chapter from its byte range on an IO dispatcher. Pagination runs on the parsed chapter currently held by the Model. Search scans chapters sequentially and does not keep a parsed cache on disk.

## Navigation and restoration

The app restores the last opened novel from the session preference and its saved progress. Page changes and chapter navigation persist progress asynchronously. The Library remains the fallback destination when no restorable novel exists.
