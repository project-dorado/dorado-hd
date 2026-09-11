# Zune Reader

- **Official package:** `ZuneReaderHD.Core.exe` (zunereader)
- **Corpus:** `Zune HD Apps (Decompiled)/zunereader` (external, untracked)
- **Wave:** W7 · **Category:** reading
- **Status:** `mock` · **Complexity:** L
- **Dorado-HD id:** zunereader

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior & provenance

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `ZuneReaderHD.Core.exe` | 56 | 324 | 6805 |
| `Microsoft.Xna.Zune.dll` | 111 | 827 | 15830 |
| `ZuneAppLib.dll` | 53 | 363 | 7669 |
| `ZuneCoreLib.dll` | 71 | 294 | 6279 |
| `ZuneReaderHD.Data.dll` | 68 | 165 | 3343 |
| `ZuneReaderHD.EPubImporter.dll` | 4 | 20 | 681 |
| `ZuneReaderHD.HtmlImporter.dll` | 3 | 13 | 262 |
| `ZuneReaderHD.OpdsCatalogLib.dll` | 5 | 15 | 684 |
| `ZuneReaderHD.PalmDatabaseImporter.dll` | 4 | 10 | 197 |
| `ZuneReaderHD.PmlImporter.dll` | 9 | 35 | 1202 |
| `ZuneReaderHD.Utility.dll` | 9 | 58 | 784 |
| `ZuneReaderHD.ZipImporter.dll` | 3 | 7 | 188 |
| `ZuneReaderHD.ZuneBookLib.dll` | 6 | 26 | 626 |

An ebook reader with its own HTML layout engine (`ZuneReaderHD.Data`: `HtmlLayout`, `TableLayoutInfo`, Css
styling, `ZuneFontFactory`), an importer per format and a library manager. Supported loaders registered at boot:
`.epub`, `.zip`, `.zrb` (Zune book), `.htm`/`.html`, `.pdb` (Palm), `.pml`/`.txt` (eReader/PML); catalog loader
`OPDS`; one OPDS catalog is pre-registered. `EBookManager` imports books into device folders, renames/deletes
them and persists annotatable `.zrb` books with TOC, last reading position and bookmarks. `PrefMgr` serializes
the navigation history stack and first-run flag; `AppLifeCycle.ShutdownSuccess` distinguishes a clean exit from
a crash before restoring history. Reader themes are light/dark (`AppStyle`).

## 2. Screens & navigation

1. **Reader pane** (`ReaderPane`): full-width HTML text rendered in chunks (`HtmlLayout`), vertical scroll,
   inline images, lists and tables (`TableLayoutInfo`). Tap-and-hold/back-specific context menu: Table of
   Contents, Bookmarks, Create bookmark, plus main-menu link. Font size has preferred stops between min/max,
   changed by pinch or menu. A permalink captures the current position; links navigate within the book or open
   http/https externally; a tap selects/navigates the link under the finger.
2. **Title bar** (`TitleBar`): slides open from the top with the current title and menu access; hides after
   scroll/tap idle and reappears on tap.
3. **Main menu** (`MenuManager.GetMainMenu`): Catalogs header — one row per OPDS catalog (icon, name, browse
   subtitle) plus "add catalog by URL"; Books header — add book by URL plus My Books with a count subtitle;
   Settings header — Reader Theme (current scheme as subtitle) and About.
4. **My Books / folder menus** (`GetMenuForFolder`): rows with cover thumbnail, wrapped title, and author/size
   subtitle; hold opens rename/delete dialog; select opens the book.
5. **Book TOC menu** (`GetBookMenu`): first page (when the book has one), last reading position, Bookmarks entry
   if any, then TOC entries with nested children.
6. **Bookmarks menu** (`GetBookmarkMenu`): saved position rows; hold renames/deletes; select jumps to the
   position.
7. **Reader theme menu** (`GetSettingsMenu`): Dark and Light rows, each with a theme preview icon and a
   "selected" subtitle.
8. **Catalog browse** (`CatalogManager`, `OpdsCatalog`): catalog navigation pages and entry rows, download with
   progress dialog and cancel; catalog download errors surface a message dialog.
9. **Dialogs** (`Dialog`): loading, message, yes/no and custom-option dialogs; wrapped text, animated height,
   option buttons.
10. **About page** (`MenuManager.GetAboutPage`): version/credits text screen.

Flow: boot → last history or main menu → My Books → folder → book → reader; TOC/bookmark menus from the context
menu; catalogs browse → download → import into library; settings → theme; dialogs for import URL, rename,
delete.

## 3. Local rules & state

- **Library:** books live in folders on device (`EBookManager.RootFolder`); importing copies/registers a book,
  renames use the next untitled name when needed (`GetNextUntitledName`), delete removes it (`Delete`).
  Annotatable books keep `LastReadingPosition` and `Bookmarks` in the `.zrb` file; TOC comes from a
  `TableOfContentsRecord`.
- **Imports:** add book by URL opens a URL keyboard (`OnImportUrlEntered`); supported extensions are checked
  before import (`IsTypeSupported`); downloads report progress and can be cancelled (`BookDownloadState`,
  `CancelDownload`); errors show a dialog.
- **Catalogs:** add-catalog-by-URL validates and persists catalog info (`ReadCatalogInfo`/`WriteCatalogInfo`);
  catalogs can be removed and their cache cleared (`DeleteCache`); OPDS search text is localized.
- **Persistence:** navigation history is serialized on exit and restored on launch unless the previous run was
  unclean (`PrefMgr.SerializeHistoryStack`, `AppLifeCycle.ShutdownSuccess`); the first-run flag suppresses
  restore; settings save on exit.
- **Themes:** light/dark color scheme applied to all reader visuals (`AppStyle.SetColorScheme`); theme choice is
  a menu selection.
- **Simulation plan:** ship 2–3 re-authored short public-domain-style texts as bundled "books"; library list,
  reading position, bookmarks, font size, theme and last menu in `graph.appState`; OPDS/catalog import shown as
  "offline — catalog service closed"; no downloads.

## 4. Controls & gestures

- Vertical drag scrolls with kinetic glide; tap a link to navigate; tap while the title bar is hidden shows it.
- Pinch-out/in steps font size (threshold ±40, preferred stops, 100 ms animation); menu items also change size.
- Horizontal swipe starting near the left edge (>60 px) navigates back; tap while idle shows the title bar;
  scrolling up hides it.
- Tap-and-hold on menu items performs the held action (rename/delete bookmark, secondary actions); dialogs
  accept tap on options.
- Main layout animates forward/backward between panes (`AnimateForward`/`AnimateBackward`); back pops the
  navigation stack.

## 5. Content inventory (must be re-authored)

- 17 files: 10 `.xnb`, 5 `.png`, 2 `.xml` (`_index/zunereader.json`).
- Images: back/down arrows, `BookImage`, `DarkTheme`/`LightTheme`, `Download`, `Shadow`, `TitleBar`, `bookmark`,
  `buttons-large`/`buttons-small`, `page-next`/`page-previous`, reader icon, `search`. Names/sizes only.
- Text: `Text/Fonts.xml`, `Text/Strings/en.xml` — never copied; re-author all display copy. No books are bundled in the corpus; all Dorado-HD reading content must be original.

## 6. Implementation plan (Dorado-HD)

Current `ZuneReaderMock` (`ui/apps/mocks/Mocks.kt`) is five title/author rows. Delta: rebuild as library list
(cover block, title, author) → reader (scrollable text, font-size controls, TOC/bookmark menu) → main menu (My
Books, Reader Theme, About, catalogs stubbed offline); light/dark reader themes via tokens; "offline library —
catalog closed" banner. Reading position, bookmarks, font size and theme persist in `graph.appState`; TOC
parsing and pagination are pure logic.

- Screens: `ui/apps/mocks/ZuneReaderApp.kt` (replace `ZuneReaderMock` in `Mocks.kt`).
- Logic: `ui/apps/ReaderModel.kt` (`parseToc`, `ReaderPosition`, `nextFontStop`, `pageAt`).
- Tests: `app/src/test/java/com/heretek/dorado_hd/ReaderModelTest.kt` — TOC parsing from a small fixture,
  bookmark ordering/insertion, font-step clamping, pagination index math.

```kotlin
@Composable fun ZuneReaderApp(graph: DoradoGraph)
fun parseToc(lines: List<String>): List<TocEntry>
fun nextFontStop(current: Int, up: Boolean, stops: List<Int>): Int
```

## 7. Citation log

All refs `ZuneReaderHD.Core.exe!` unless noted.
- Boot/state: `MainGame.Initialize`, `!OnExiting`, `AppLifeCycle.StartUp`, `!ShutDown`, `!ShutdownSuccess`,
  `PrefMgr.SerializeHistoryStack`, `!DeserializeHistoryStack`
- Reader: `ReaderPane.GetContextMenu`, `!OnGoToTableOfContentsClicked`, `!OnGoToBookmarksClicked`,
  `!OnAddBookmarkClicked`, `!FontSizeUp`, `!FontSizeDown`, `!OnFontSizeChanged`, `!OnZoom`, `!OnScrub`,
  `!OnTap`, `!GetPermalink`, `!NavigateToFragment`
- Chrome: `TitleBar.Open`, `!Close`, `!Show`, `!Hide`, `!OnTap`; `MainLayout.AnimateForward`,
  `!AnimateBackward`, `!ShowDialog`, `!CloseDialog`
- Menus: `MenuManager.GetMainMenu`, `!GetBookMenu`, `!GetBookmarkMenu`, `!GetSettingsMenu`, `!GetMenuForFolder`,
  `!GetAboutPage`, `!OnAddBookClicked`, `!OnImportUrlEntered`, `!OnCatalogItemSelect`, `!OnAddCatalogSelect`,
  `!GetBookThumbnail`, `!GetBookSubtitle`
- Library/import: `EBookManager.ImportBook`, `!ImportFromUrl`, `!RenameBook`, `!Delete`, `!LoadBookContent`,
  `!RegisterLoader`, `!IsTypeSupported`, `!GetNextUntitledName`, `!CancelDownload`
- Catalogs: `CatalogManager.AddCatalog`, `!OnCatalogLoaded`, `!RegisterCatalog`, `!ReadCatalogInfo`,
  `!WriteCatalogInfo`, `!DeleteCache`
- Layout: `HtmlLayout.SetSize`, `!CreateVisuals`, `!CreateNewChunkIfNeeded`; `TableLayoutInfo.SizeColumnsToFit`,
  `!AddRow`; `AppStyle.SetColorScheme`; `ZuneFontFactory.GetFont`; `Dialog.Show`, `!ShowLoading`, `!Close`
- Loaders: `ZuneReaderHD.EPubImporter!EPubLoader.Register`, `ZuneReaderHD.ZipImporter!ZipFileLoader.Register`,
  `ZuneReaderHD.ZuneBookLib!ZuneBookLoader.Register`, `ZuneReaderHD.HtmlImporter!HtmlBookLoader.Register`,
  `ZuneReaderHD.PalmDatabaseImporter!PalmDatabaseLoader.Register`,
  `ZuneReaderHD.PmlImporter!EReaderLoader.Register`, `ZuneReaderHD.PmlImporter!PmlBookLoader.Register`,
  `ZuneReaderHD.OpdsCatalogLib!OpdsCatalogLoader.Register`
- Inventory: `_mine/_index/zunereader.json`; content tree `Images/*`, `Text/{Fonts}.xml`.
