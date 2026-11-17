# TextForge architecture

## MVI flow

TextForge's feature screens follow the direct Model → View → Intent loop:

1. **Model / State** is an immutable snapshot (`ReaderState`, `LibraryState`).
2. **View** observes the snapshot and only renders it (`ReaderScreen`, `ReaderPanels`, `LibraryScreen`).
3. **Intent** describes a user action (`ReaderIntent`, `LibraryIntent`).
4. A reducer creates the next snapshot. The View never mutates the model directly.
5. I/O is performed by the ViewModel as an effect. Its result returns as an intent and is reduced into state.

Reader screen state, including chapter/page, overlays, search, dictionary status, selection target and the single layout configuration, is held in `ReaderState`. The measured screen geometry and font size travel together in `ReaderLayout`; there are no independent screen-size and font-size state paths.

`ReaderScreen` is limited to the reader page surface, system-bar behavior, layout measurement and forwarding gestures. Reader controls and dialogs live in `ReaderPanels`. `ReaderModel` owns intents, reducer, asynchronous effects and the state flow. `ReaderPagination` only converts the current chapter model into page fragments; it does not load or persist data.

The Library screen uses the same state/intent pattern. Its document picker only returns a `Uri`; importing, refreshing and deleting are handled by `LibraryViewModel`.

## Data and caching

`NovelStore` persists only:

- the imported source text (`source.md`),
- metadata (`metadata.json`),
- the lightweight chapter byte index (`index.json`), and
- reading progress (`progress.json`).

Parsed chapter JSON files, neighbour prefetch, chapter windows and parsing caches are not persisted or maintained. The reader reads and parses one chapter from its byte range on an IO dispatcher. Search scans chapters sequentially and holds only the current chapter's parsed blocks while scanning.

## Reduced source layout

The project now has four Gradle modules: `app`, `ui`, `storage` and `parser`. Navigation and the document-picker UI were moved into `ui` rather than maintained as tiny stand-alone modules. Related source files were consolidated: parser types/indexing/parser, storage models/store, navigation routes, theme values, and text-rendering components. The number of Kotlin source files was reduced from 23 to 13 while keeping separate files for genuinely separate responsibilities such as the reader model, page renderer, and reader panels.
