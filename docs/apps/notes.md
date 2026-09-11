# Notes

- **Official package:** `Notepad.exe` (notes)
- **Corpus:** `Zune HD Apps (Decompiled)/notes` (external, untracked)
- **Wave:** W1 · **Category:** utilities
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** notes

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

`Notepad.exe` is a 17-type / 108-method ZuneAppLib application (3,181 lines,
`Notepad` namespace). It is two intertwined note stores: free-text **notes**
and checkable **lists** (up to 100 strikethrough items each). Both are
sorted, searched, edited in a full-screen modal with next/previous navigation,
and persisted as XML. Text size and sort order are user settings, and the last
open item/scroll position is restored on relaunch.

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Notepad.exe` | 17 | 108 | 3181 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `ZuneAppLib.dll` | 57 | 367 | 7803 |
| `ZuneCoreLib.dll` | 70 | 308 | 6507 |

Models: `Notepad!NoteInfo` (Text, TimeStamp, CreationTime),
`Notepad!ListInfo` (Title, ListItems, TimeStamp, CreationTime) and
`Notepad!ListItemInfo` (Text, Strikethrough). `Notepad!NoteList` is the static
store with `Initialize`/`SaveNotes` over `noteList.xml` and `listList.xml`.
`SortBy` is LastUpdated / CreatedOn / Alphabetical; `TextSize` is Small /
Medium / Large.

## 2. Screens & navigation

`Notepad.ApplicationFinishedLoading` shows the notes list and the lists list
as two horizontally swiped pages (handled in `Notepad.OnTouchesEnded`), each a
`NotesListViewController`. The list screen has a plus button
(AddNote/AddList), a search box that raises the on-screen keyboard, a
sort-cycle button, and per-row delete affordances. Tapping a row opens the
editor modal; the editor has back/forward buttons that animate to the
previous/next item, a delete button (with a Yes/No message box), and for
lists an add-line / delete-lines toolbar.

`Notepad!CreateNoteViewController.SetText` rebuilds the editor for a given
index and type: notes get one text box; lists get a title box plus up to 100
line controllers, each with a checkbox and edit field, repositioned
top-to-bottom (`RepositionSceneHelper`). The About and Settings screens are
separate controllers (`AboutViewController`, `SettingsViewController`);
Settings shows the text-size cycler and the live notes/lists counts
(`NumNotes`/`NumLists`). Resume state means relaunching can reopen the last
note or list and its scroll offset.

## 3. Rules, scoring & progression

- Sorting (`Notepad!NoteList.Sort`): LastUpdated and CreatedOn order by
  timestamp descending (`CompareDateTime` returns −1 for newer), Alphabetical
  orders by trimmed title/text with timestamps as the tie-break. New items are
  inserted at index 0 unless the active sort is Alphabetical, in which case
  they are placed in order.
- Editing (`CreateNoteViewController.SaveData`): on exit, note text is
  compared with `m_origText`; unchanged notes are not rewritten (timestamp
  preserved), changed notes get a new `TimeStamp`, and a new note is only
  added when its trimmed text is non-empty. A list whose title and all lines
  are blank (`IsEmptyText` → `IsBlankList`) is deleted; blank list titles
  become the localized `UntitledList`, and deleting a list line relocates it
  and hides the add/delete controls until a line remains.
- Item flags: `ListItemInfo.Strikethrough` is the checked state; check/uncheck
  toggles through `ListItemViewController.SetCheckbox`. Each row shows the
  created/updated time via `NoteButton.UpdateLastUpdatedText`, formatted by
  `Extensions.DateToString` (Today/Yesterday + time + am/pm).
- Search (`PerformSearch`/`SearchHelper`): case-insensitive substring match
  (`Extensions.StringContainsSearchTerm`); non-matching rows are hidden and
  the list re-flows; `NoSearchResults` is shown when everything is filtered
  out. Adding an item clears an active search.
- Persistence: `NoteList.SaveNotes` writes both XML lists, then saves
  `Settings` (`SortBy`, `TextSize`, per-page scroll positions,
  `ViewingNote`/`SaveIndex`/`WasNote`). There is no cloud sync.

## 4. Controls

Touch and on-screen keyboard. Horizontal swipe between Notes and Lists pages;
tap a row to edit; the row delete icon arms a strikethrough delete and a second
tap removes. In the editor: tap a line to raise the keyboard, tap a checkbox
to toggle strikethrough, tap `AddLine`/`DeleteLines` for line-delete mode and
`DoneDeleteLines` to exit, and swipe Back/Forward to move between items.
Line-reorder does not exist. No accelerometer, no multitouch; the soft
keyboard's Done button commits the field (`OnScreenKeyboard` paths).

## 5. Content inventory (must be re-authored)

26 files: 23 PNG and 3 XML — every file is chrome or text, no fonts (the app
uses ZuneAppLib fonts). Images are plus/back/forward/info/search-box and
checkbox states (`off`/`on`/`press`), search boxes and done buttons.
`Text/Fonts.xml` declares `NoteFontSmall`/`Medium`/`Large`, `NoteListFont`,
`TimeFont`, `SettingsFont`; strings and key maps live in the other XMLs.

Re-authoring: checkbox/back/forward/plus/search art becomes token-drawn
shapes and text (the back affordance stays the cropped header); row chrome is
typography. The three note text sizes map to existing `DoradoTokens` type
steps; no PNG or font is copied.

## 6. Implementation plan

- Engine: new `ui/apps/NotesEngine.kt` — `object NotesEngine` + immutable
  `data class NoteDoc(id, title, body, createdAt, updatedAt, items:
  List<ChecklistItem>)` and `data class NotesState(sortBy, textSize,
  query, notes, lists)`. Pure functions: `sort`, `search`, `addNote`,
  `addList`, `toggleItem`, `addItem`, `deleteItem`, `isBlank`, `saveOnExit`
  (the drop-empty/untitled rules and timestamp semantics).
- UI: rewrite `NotesApp()` (`MiniAppsUtilities.kt:193`) as
  `ui/apps/notes/NotesApp.kt`: `DetailScaffold(title = "notes")`, two swipeable
  pages (notes | lists) with a sort-cycle text button and a search field, plus
  a full-screen editor for either kind with back/forward item navigation and a
  Yes/No delete confirmation overlay. Zero radius, tokens only.
- Data: free-text notes keep `graph.notes` (`NoteEntity`) plus new
  `createdAt`/`kind` columns; checklists, sort/text-size settings and view
  state go through `graph.appState.put("notes", …)` as a JSON blob.
- Fidelity gaps vs current code: no checklists, no sort modes, no search, no
  text-size setting, no created/updated timestamps, no next/previous item
  navigation, no untitled/empty-item rules and no resume-last-open state.
- Tests: `app/src/test/java/com/heretek/dorado_hd/NotesEngineTest.kt` — all
  three sort orders and tie-breaks, case-insensitive search hide/reflow,
  empty-note drop, blank-list drop and `UntitledList` naming, item
  toggle/strikethrough round trip, 100-line cap, and Today/Yesterday date
  formatting. Keep `LogicTest.kt` pass.
- Edge cases: deleting the last list line (disable delete mode), searching
  while adding, whitespace-only notes, unicode titles, and a stale resume
  index.

## 7. Citation log

`Notepad!NoteList.Initialize`, `Notepad!NoteList.AddNote`,
`Notepad!NoteList.AddList`, `Notepad!NoteList.DeleteNote`,
`Notepad!NoteList.DeleteList`, `Notepad!NoteList.Sort`,
`Notepad!NoteList.SaveNotes`, `Notepad!NotesListViewController.OnDelete`,
`Notepad!NotesListViewController.AddNote`,
`Notepad!NotesListViewController.AddList`,
`Notepad!NotesListViewController.OnAddButtonClicked`,
`Notepad!NotesListViewController.PerformSearch`,
`Notepad!NotesListViewController.ResetSearch`,
`Notepad!NotesListViewController.SearchHelper`,
`Notepad!NotesListViewController.SaveScrollPosition`,
`Notepad!NotesListViewController.UpdateSortedBy`,
`Notepad!CreateNoteViewController.AddLine`,
`Notepad!CreateNoteViewController.DeleteListItem`,
`Notepad!CreateNoteViewController.SaveData`,
`Notepad!CreateNoteViewController.SetText`,
`Notepad!CreateNoteViewController.IsEmptyText`,
`Notepad!CreateNoteViewController.IsBlankList`,
`Notepad!CreateNoteViewController.AnimateNext`,
`Notepad!CreateNoteViewController.AnimatePrev`,
`Notepad!ListItemViewController.SetCheckbox`,
`Notepad!NoteButton.UpdateLastUpdatedText`,
`Notepad!SettingsViewController.Update`,
`Notepad!Extensions.DateToString`,
`Notepad!Extensions.StringContainsSearchTerm`,
`Notepad!Notepad.ApplicationFinishedLoading`, `Notepad!Notepad.OnTouchesEnded`,
`Notepad!Notepad.OnExiting`.
