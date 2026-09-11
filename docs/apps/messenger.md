# Messenger

- **Official package:** `Microsoft.Live.Messenger.Client.exe` (messenger)
- **Corpus:** `Zune HD Apps (Decompiled)/messenger` (external, untracked)
- **Wave:** W7 · **Category:** networking
- **Status:** `mock` · **Complexity:** XL
- **Dorado-HD id:** messenger

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior & provenance

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Microsoft.Live.Messenger.Client.exe` | 257 | 1464 | 23396 |
| `Messenger.Core.dll` | 150 | 174 | 4376 |
| `Messenger.Services.AddressBook.dll` | 24 | 136 | 1799 |
| `Messenger.Services.Chat.dll` | 19 | 134 | 1991 |
| `Messenger.Services.Common.dll` | 17 | 137 | 2411 |
| `Messenger.Services.Configuration.dll` | 2 | 9 | 115 |
| `Messenger.Services.Core.dll` | 48 | 405 | 5259 |
| `Messenger.Services.ExpressionProfile.dll` | 8 | 50 | 748 |
| `Messenger.Services.Helpers.dll` | 26 | 130 | 1800 |
| `Messenger.Services.Identity.dll` | 1 | 3 | 32 |
| `Messenger.Services.Msnp.dll` | 31 | 117 | 2860 |
| `Messenger.Services.Presence.dll` | 40 | 291 | 4236 |
| `Messenger.dll` | 126 | 607 | 12660 |
| `Microsoft.Xna.Zune.dll` | 111 | 827 | 15830 |
| `ZuneAppLib.dll` | 52 | 378 | 8038 |
| `ZuneCoreLib.dll` | 71 | 296 | 6422 |

The device's Windows Live Messenger client (MSNP protocol family, `Messenger.Services.Msnp`): sign-in with a
Live ID, address book and groups, presence, one-to-one and group conversations, a social feed, emoticons,
nudges, and media sharing. `Microsoft.Live.Messenger.Client` is the UI; `Messenger.Core` holds
account/conversation/contact/social models; service assemblies split presence, chat, address book, identity and
configuration. Account and options are persisted locally (`AccountStore`, `AccountOptions`); sounds exist for
signed-in, text message and nudge. All content is live-service data — no message history cache was found, so
Dorado-HD seeds its own.

## 2. Screens & navigation

1. **Sign-in / account** (`SignInViewController`, `AccountView`, `SigningInView`, `SignedOutView`): welcome
   title + subtitle, display picture, name/e-mail fields, sign-in button, and a status list (available / busy /
   away / appear offline) with "change account"; a modal progress panel while signing in; a signed-out panel
   explaining the reason if the session drops.
2. **Home hub** (`HomeView`, `HomeViewController`): account display name title (clickable), profile block
   (`HomeProfileView`) with display picture and personal message (tap picture → account options), then a
   vertical menu list with 4-px spacing — Social, Friends, Chats, Options.
3. **Conversations** (`ConversationListView`, `ConversationTwistViewController`): title, scrollable list of
   `ListViewConversationItem` rows — display picture with presence, typing and new-message badges,
   participant-name title, single-line last message — with a centered empty-state label when there are none.
4. **Conversation thread** (`ConversationViewController`/`ConversationView`): portrait stacks roster strip, info
   bar, history and input; landscape hides roster and info bar and gives the height to history.
   `ConversationHistoryView` renders rich message history (text with emoticon images, timestamps, media cards);
   `ConversationInputView` is a 30-px bar showing ellipsis when idle that expands into the keyboard with a send
   button and placeholder; the input hides for contacts that appear offline (`conversation.IsEnabled()`).
5. **Contacts** (`ContactListView`): group headers (`ListViewGroupItem` — online contacts, custom groups,
   per-network groups, others) over `ListViewContactItem` rows — display picture with presence, name, personal
   message; holding a row opens a context menu.
6. **Social feed** (`SocialFeedView`, `ListViewSocialItem`): rows are [display picture | vertical stack: entry
   text with emoticons/links, comments, action buttons]; entries sort by timestamp; per-service and per-feed
   views; empty and loading states; refresh button.
7. **Options** (`OptionsView`, `AccountOptionsView`): filter pages Personal (media sharing), Contacts (sort by
   status vs alphabet), Messages (emoticons, timestamps, auto-correct), Notifications (favourites, contacts,
   text messages), Sounds (all, contact sign-in, text message, nudge), Privacy (forget account).
8. **Context menus** (`ContextMenuViewController`, `ContactContextMenuViewController`,
   `SignOutContextMenuViewController`, `ExitContextMenuViewController`): hold menus for contact/conversation
   actions (chat, block, delete) and for sign-out/exit confirmations.
9. **About** (`AboutView`): logo plus version/build and credits, scrollable.
10. **Toasts** (`ToastViewController`): transient banners for sign-in, new message and nudge events.

Flow: sign-in → home hub → Social / Friends / Chats / Options; conversation row → thread → input → send; contact
hold → context menu; picture tap → account options; sign-out → sign-in.

## 3. Local rules & state

- **Accounts:** `AccountStore`/`AccountStoreReader`/`AccountStoreWriter` persist known accounts; the last
  account is selected on the sign-in screen; `SignInAccountCommand` runs the service sign-in and reports failure
  codes to the modal; privacy "forget" removes the account (`OptionsView.OnForgetLabelClicked`,
  `AccountViewController.RemoveAccount`).
- **Options:** `AccountOptions` persists media sharing, contact sort, emoticons, timestamps, auto-correct,
  notification and sound toggles per account; changing auto-correct reconfigures the open keyboard live.
- **Presence:** contact presence drives row icons and conversation input enablement; `IsAppearOfflineTo` hides
  your presence from a contact; the roster strip shows only in portrait.
- **Messages:** rich token formatting handles links, styles and lists; emoticon text is encoded into images
  (`EmoticonEncoder`); nudges play a sound and shake; media sharing attaches the current track/video/game
  (`MediaSharer`, `MediaType`).
- **No offline history:** nothing caches conversations; a dropped session shows the signed-out panel and returns
  to sign-in. Dorado-HD seeds local conversations and marks the service "offline".
- **Simulation plan:** one account with a personal message, 3 groups, ~8 contacts, 3 conversation histories and
  a short social feed; toggles persisted in `graph.appState`; sending appends locally and shows a "not delivered
  — service offline" note; no network.

## 4. Controls & gestures

- Tap hub items to navigate; tap the title to change account; tap the profile picture for options; tap status
  rows to change presence.
- Conversation/contact/social rows: tap to open, hold for the context menu; lists scroll kinetically; twist
  pages separate feeds/conversations.
- Thread: tap the input bar to raise the keyboard, send commits and clears; keyboard auto-correct follows the
  option.
- Landscape rotation hides roster/info bar and expands history; back returns through the stack; sign-out/exit
  confirm via context menus.
- Toasts are passive overlays and never take focus.

## 5. Content inventory (must be re-authored)

- 144 files: 142 `.xnb`, 2 `.xml` (`_index/messenger.json`).
- Emoticons: 100 images (~1.5 MB) — re-author a compact emoticon set.
- UI images: 36 — display-picture frames and defaults in four sizes, multi-photo overlay,
  status/typing/new-conversation icons, comment add/more buttons, context-menu top, bubble arrow, border, logo,
  system-button/refresh families, media game/music/video glyphs. Names/sizes only.
- Sounds: 3 (signed-in, text message, nudge, ~680 KB total) — re-author or substitute silence/tonal cues.
- Text: `Text/Fonts.xml`, `Text/Strings/en.xml` — never copied; re-author all display copy.

## 6. Implementation plan (Dorado-HD)

Current `MessengerMock` (`ui/apps/mocks/Mocks.kt`) is three message lines. Delta: rebuild as sign-in (any
credentials accepted, offline) → home hub (name title, profile block, Social/Friends/Chats/Options) →
conversation list with presence-framed avatars and typing/new badges → thread (roster, info bar, history with
emoticon rendering, input bar) → contacts with group headers → options toggles → hold context menus and toasts.
Visible "offline — messages stay on this device" banner; account, options and conversations in `graph.appState`.
Tokens only, `DetailScaffold`, zero corner radius.

- Screens: `ui/apps/mocks/MessengerApp.kt` (replace `MessengerMock` in `Mocks.kt`).
- Logic: `ui/apps/MessengerModel.kt` (`Conversation`, `ContactGroup`, `encodeEmoticons`, `sortContacts`).
- Tests: `app/src/test/java/com/heretek/dorado_hd/MessengerModelTest.kt` — emoticon encoding,
  status-vs-alphabetical sorting, presence gating of send, group partitioning.

```kotlin
@Composable fun MessengerApp(graph: DoradoGraph)
fun encodeEmoticons(text: String): List<RichSpan>
fun sortContacts(contacts: List<Contact>, byStatus: Boolean): List<Contact>
```

## 7. Citation log

All refs `Microsoft.Live.Messenger.Client.exe!` unless noted.
- Shell/lifecycle: `MainViewController` ctor, `!OnSignedIn`, `!OnSigningIn`, `!OnSignedOut`, `SignedInViewController.InitializeControllers`, `!SwitchToConversation`, `!SwitchToSocialFeed`
- Home: `HomeView.InitializeTitleUI`, `!InitializeProfileUI`, `!InitializeItemsUI`, `HomeViewController`
- Conversations: `ConversationListView.InitializeUI`, `!AddConversation`, `!UpdateLayout`, `ListViewConversationItem.InitializeUI`, `!UpdateUI` (typing/new badges, multi-picture)
- Thread: `ConversationView.InitializeUI`, `!UpdateUI` (portrait/landscape), `ConversationInputView.InitializeUI`, `!OnCommitted`, `!UpdateUI`, `ConversationHistoryView`, `ConversationInfoBarView`, `ConversationRosterView`
- Contacts/groups: `ContactListView.InitializeUI`, `!InitializeGroups`, `!AddGroup`, `!AddNetwork`, `ListViewGroupDataOthers`, `ListViewGroupDataNetwork`
- Social: `SocialFeedView.InitializeUI`, `!InitializeEmptyUI`, `ListViewSocialItem.InitializeUI`, `SocialFeedEntry`
- Options/account: `OptionsView` (`AddToggle`, `OptionsFilter` cases, `AddForgetLabel`), `AccountOptionsView` (presence list), `AccountView.InitializeUI`, `SigningInViewController`, `SignedOutViewController`
- Context menus/toasts: `ContextMenuViewController`, `ContactContextMenuViewController` (`ContactContextMenuActions`), `SignOutContextMenuViewController`, `ToastViewController`
- Models/state: `AccountStore`, `AccountStoreReader`, `AccountStoreWriter`, `AccountOptions`, `ConversationExtensions`, `EmoticonEncoder`, `MediaSharer`, `MediaType`, `PresenceStatusExtensions`
- About: `AboutView`, `AboutViewController`; inventory `_mine/_index/messenger.json` (`Images/Emoticons`, `Sounds`, `Images/Media`).
