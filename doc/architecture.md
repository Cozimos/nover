# Reader architecture

The reader follows a small MVI loop:

1. Compose sends a `ReaderIntent`.
2. `ReaderViewModel` reduces the intent and performs I/O in `viewModelScope`.
3. The ViewModel publishes one immutable `ReaderState` through `StateFlow`.
4. Compose renders that state.

`ReaderLayoutState` is the single layout input: available pixel dimensions, density,
font scale, and text size travel together. Pagination is derived from that layout and
the active chapter; it is not persisted.

The source Markdown remains the canonical book content. Import builds only a chapter
byte-offset index (`index.json`) and initial reading progress. Opening or navigating
to a chapter reads that chapter's byte range from `source.md` and parses it on demand.
Parsed chapter JSON, neighbouring-chapter prefetch, and cross-chapter parsed caches
are deliberately absent. Search also parses chapters sequentially, never keeping a
window of parsed chapters in memory.

Reading progress remains persistent because it is user data, not a disposable cache.
