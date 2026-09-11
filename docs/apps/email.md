# Email

- **Official package:** `ZuneHDEmail.exe` (email)
- **Corpus:** `Zune HD Apps (Decompiled)/email` (external, untracked)
- **Wave:** W7 · **Category:** networking
- **Status:** `mock` · **Complexity:** XL
- **Dorado-HD id:** email

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior & provenance

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `ZuneHDEmail.exe` | 76 | 709 | 15331 |
| `ExchangeActiveSync.dll` | 71 | 306 | 9926 |
| `Microsoft.Xna.Zune.dll` | 111 | 827 | 15830 |
| `ZHDEmail.Common.dll` | 36 | 97 | 1811 |
| `ZuneAppLib.dll` | 53 | 366 | 7780 |
| `ZuneCoreLib.dll` | 71 | 295 | 6389 |

Multi-account mail client: Windows Live/Hotmail, Gmail, Exchange (marketplace blurb). `ZHDEmail.Common` is
the sync/message hub (`MessageHub`, `ServerComms`, folder-sync messages); `ExchangeActiveSync.dll` is the
protocol client; the UI is an XNA app with 35 XML layout files under `Content/UI/`. Startup checks system
settings and legacy contact data (`CheckOldContactDataExists`, `LoadContactData`), then shows
`MainMailView`; no account runs the setup wizard, existing accounts start on `AccountChooserPage`. Mail is
server-side, but a synced folder/mail cache keeps the inbox readable offline. Only the active account id
persists (`AppState.ActiveEmailAccountID`, `App.SaveStateData`).

## 2. Screens & navigation

1. **Account chooser** (`AccountChooserView.xml`): *Accounts* / *About* pivot bar; account rows
   (`AccountChooserItem`: brand mark, address, unread badge) plus a Settings app-bar button; About pivot
   holds `AboutPanel`.
2. **Mail list** (`MainPivotView.xml`, `MainMailView`): 272-px gradient bar with back; title (Bold-10, 18
   px); 36-px `Swipebar` with All / Unread / Urgent pivots (`UpdateMailFilter` →
   `MailFilter.All/Unread/Urgent`); a 350-px `ScrollableMailListboxView` per pivot; first launch shows a
   60-px sync-message block instead of rows. Folder chooser (`FolderChooser.xml`): 426-px scroll from y=27,
   synced stack, an expand-all-folders action, unsynced stack.
3. **Mail row** (`ListboxMailItem.xml`): 272×65. 24-px checkbox column (select mode) + 248-px detail —
   sender (SemiBold-14, truncate) with grey right-aligned date; subject in accent blue plus a 37-px
   mini-icon strip (reply / important / not-important / flagged / attachment, 8–11×12 px); grey snippet;
   divider.
4. **Mail viewer** (`MailViewer.xml`, `MailViewer`): 27-px top bar, 415-px scroll. From (Segoe-20, 38 px),
   subject (Bold-11, wrap), date-time (grey SemiBold-10), To + separate CC/BCC lines, a 200-px expand-recipients action,
   a 260×24 attachment strip, body. App bar: Reply, Delete, Newer, Older + Mark-unread
   menu item; Newer/Older walk the list (`ShowNextMail/ShowPrevMail`) and auto-mark read.
5. **Composer** (`MailComposer.xml`): 272×392 scroll. Three 40-px recipient blocks (To/CC/BCC), each with a
   20-px add-contact button; 8-px spacer; 36-px subject box; 8-px spacer; 75-px body box; 20-px signature
   toggle; 27-px quoted-original label. App bar: Send (disabled state), Close, Priority and Show-CC menus.
   Reply pre-fills sender (plus all for reply-all); forward starts empty with quoted text appended.
6. **Contact picker** (`ContactPicker.xml`): 27-px top bar, title, 27-px search box, 368-px list of
   `ContactPickerItem`; app bar Close. `ApplySearchFilter` matches name and all three email fields; multiple
   addresses open `PopupList`.
7. **Contacts + detail** (`Contacts.xml`, `ContactDetails`): gradient bar, search, A–Z sections with index
   jump; detail opens in View mode with touch-target fields opening editors (Name, PhoneNumber with number
   pad, Email, Address, TextField, DatePicker). App bars switch View / Edit / Add (`ContactDetailsMode`),
   with Delete and add-field selector.
8. **Setup wizard / settings / re-auth**: type picker → step 1 (address + password; Next stays disabled) →
   step 2 (server, domain, username, password, SSL, sync frequency/since, signature), sequenced by
   `SetupWizardController`; account list + per-account form (`AccountSettingsListView.xml`,
   `AccountSettingsView.xml`); re-auth (message + username/password, Save enabled only when both non-empty).
   Overlays: `PopupList`, `MessageBox`, `Swipebar`, `ApplicationBar`, `NumericKeyboard`, `Checkbox`,
   `ImageButton`.

Flow: chooser → mail list → folder chooser / contacts / settings; row tap → viewer → reply/forward composer
→ send → sync; select mode → bulk delete / mark read / mark unread; unauthorized sync → re-auth.

## 3. Local rules & state

- **Offline:** folders/messages come from the local sync cache (`LoadFolderData`, `LoadMailFolder`,
  `FindMail`) and stay browsable while sync is down; failures show a Wi-Fi-timeout box, expired credentials
  raise `UnauthorizedMessage` → `ShowReAuthentication`. Unread counts update via `CheckAndNotifyOfNewEmail`
  and the chooser badge (`SetNewEmails`).
- **Send:** blocked when To/CC/BCC are all empty (`appBar_ButtonClicked`); addresses serialize as name +
  angle-bracket address or bare address; signature is appended unless removed; Priority maps to
  `MailPriority`; closing a touched composer asks yes/no (`EnteredDetails`, `GoBack`). No persistent drafts
  exist — store one per account in `graph.appState`.
- **Read state:** opening marks read; viewer can mark unread; select mode does bulk read/unread
  (`HandleMarkReadOrUnread`, `MailItemMessage`) and queued delete (`BuildListBoxes`). Contacts edited
  locally (`AddContact`, `UpdateContact`, `DeleteContact`).
- **Simulation plan:** seed a small offline inbox per account; persist read/unread and deletes in
  `graph.appState`; composer writes to a local Outbox labelled offline; no network code.

## 4. Controls & gestures

- Row tap: checkbox toggle in select mode, otherwise viewer (`MainPivotView.item_Clicked`); horizontal drag
  >24 px cancels the press so pivot swipes win (`MailListboxItem.TouchesMoved`). Swipe the pivot bar to
  change All/Unread/Urgent; app bar swaps Compose/Select/Folder/Sync for Close/Delete/Mark items in select
  mode.
- Recipient block tap expands contacts; plus opens the picker; chip tap edits. Date picker: three snapping
  scroll columns (day/month/year) with centre highlight; message boxes are modal.

## 5. Content inventory (must be re-authored)

- 122 files: 86 `.xnb`, 35 `.xml`, 1 `.csv` (`_index/email.json`).
- Fonts: 11 bitmap faces (Segoe-10/17, SemiBold 8–16, SemiLight-10/66) → Selawik + token sizes.
- Images: 65 under `Images/` (app-bar set, three mail-brand marks, checkboxes, mini status glyphs,
  send/reply/trash/folder/refresh/prev/next families, gradient, loader, logo, web-mail mark). Names/sizes
  only.
- UI XML: 16 top-level + 8 sub-folder (ContactDetails, ContactEditors, SetupWizard) — layout source
  transcribed in §2.
- Strings en/es/fr and a 309 KB reference contact CSV: never copied; synthesize a small fake contact set.

## 6. Implementation plan (Dorado-HD)

Current `EmailMock` (`ui/apps/mocks/Mocks.kt`) is a 3-row from/body list. Delta: a three-screen flow — Inbox
(All/Unread/Urgent) → Viewer (From/Subject/Date/To/body, Reply/Delete/Newer/Older) → Compose (To/CC/BCC chip
fields, subject, body, signature, Send) — with a visible "offline mailbox" banner; rows at the decompiled
65-px anatomy; selection, mark read/unread, discard confirmation and recipient-required validation as pure
logic; mailbox and one draft per account in `graph.appState`. Tokens only, `DetailScaffold`, zero corner
radius, `EdgeCropText` rows.

- Screens: `ui/apps/mocks/EmailApp.kt` (replace `EmailMock` in `Mocks.kt`).
- Logic: `ui/apps/MailModel.kt` (`Mailbox`, `Mail`, `Draft`, `validateSend`).
- Tests: `app/src/test/java/com/heretek/dorado_hd/MailModelTest.kt` — send validation, read/unread toggles,
  pivot filtering, draft round-trip.

```kotlin
@Composable fun EmailApp(graph: DoradoGraph)
class Mailbox(folders: List<Folder>, drafts: Map<AccountId, Draft>)
fun validateSend(to: List<String>, cc: List<String>, bcc: List<String>): Result
```

## 7. Citation log

All refs `ZuneHDEmail.exe!` unless noted.
- `ZHDEmail.ApplicationFinishedLoading`, `ZHDEmail.CheckOldContactDataExists`, `ZHDEmail.LoadContactData`,
  `AppState.ActiveEmailAccountID`, `App.SaveStateData`
- `MainMailView.OnNavigatedTo`, `MainPivotView.UpdateMailFilter`, `MainPivotView.AppBarHandler`,
  `MainPivotView.item_Clicked`, `MainPivotView.HandleMarkReadOrUnread`, `MainPivotView.BuildListBoxes`
- `MailPage.ShowNextMail`, `MailPage.ShowPrevMail`, `MailPage.DisplayMail`, `MailViewer.SetData`,
  `MailViewer.ShowAllRecipients`
- `MailComposer.SetupNewMail`, `MailComposer.SetupReply`, `MailComposer.SetupForward`,
  `MailComposer.appBar_ButtonClicked`, `MailComposer.GoBack`, `MailComposer.EnteredDetails`,
  `MailComposer.SendEmailUpdate`
- `ContactPicker.ApplySearchFilter`, `ContactEntryField.SelectEmailAddress`, `ContactDetails.Setup`,
  `ContactDetails.AddContact`, `ContactDetails.UpdateContact`, `ContactDetails.DeleteContact`,
  `ContactDetails.ContactDetailsMode`, `ReAuthenticationView.EvaulateSaveButtonState`
- `ZHDEmail.Common.dll!MessageHub.Send`, `ZHDEmail.Common.dll!ServerComms`; `ExchangeActiveSync.dll!Mail`,
  `!MailFilter`, `!MailPriority`
- Layouts:
  `Content/UI/{MainPivotView,ListboxMailItem,MailComposer,MailViewer,ContactPicker,Contacts,FolderChooser,AccountChooserView}.xml`,
  `UI/ContactDetails/*`, `UI/SetupWizard/*`; inventory `_mine/_index/email.json`.
