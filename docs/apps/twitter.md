# Twitter

- **Official package:** `Twitter.exe` (twitter)
- **Corpus:** `Zune HD Apps (Decompiled)/twitter` (external, untracked)
- **Wave:** W7 · **Category:** social
- **Status:** `mock` · **Complexity:** L
- **Dorado-HD id:** twitter

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior & provenance

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Twitter.exe` | 56 | 588 | 14128 |
| `Matchbox.Zune.dll` | 55 | 261 | 5574 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `TwitterAPI.dll` | 16 | 83 | 1674 |
| `ZuneAppLib.dll` | 53 | 361 | 7627 |
| `ZuneCoreLib.dll` | 71 | 292 | 6259 |

The device's Twitter client. `TwitterAPI.dll` is the OAuth-era REST layer (xAuth login token exchange, HMAC-SHA1
request signing, timeline/mentions/favorites/DM/user endpoints); `Twitter.exe` is the UI plus a local cache
store. `TwitterStore` persists JSON/XML responses as per-request `.dat` files with a cache index and
last-modified stamps (`GetTimelineCache`, `GetFreindsCache`, `GetFollowersCache`); `UserSettings` keeps the last
account and per-user settings and can destroy a user's cache directory. The app boots into `LoginPage` when no
session exists, otherwise the `MainPage` timeline; cached content is rendered with an `isCache` flag while a
remote refresh runs. Level goal: layout/flow fidelity only — no endpoint revival.

## 2. Screens & navigation

1. **Login** (`LoginPage`): full-screen gradient host image, centred logo, username and password boxes, login
   button; `LoadingAnimationView` spinner overlay while authenticating; remembers the last user and re-validates
   the stored session; failure keeps the form and shows the error via `TwitterApp.ShowMessage`.
2. **Main shell** (`MainPage`): 89-px gradient header with the bird/logo button at top-left, account name
   (uppercased, max 15 chars) top-right on corner/fill plates; 40-px tab toolbar under it with four atlases at x
   offsets 1/69/137/205 — Timeline, Replies, Favourites, Direct Messages — each with a focus atlas and a
   notification blip (count label) on Timeline; 40-px context toolbar at the bottom with Back, Tweet, Search,
   Settings at x 22/84/146/208, disabled/hidden states managed by page type.
3. **Timeline main** (`TimelineMain`): 33-px swipe bar with Timeline / Following / Followers (evenly spaced,
   active text highlighted, drop shadow), each a twist page of rows; vertical scroll with refresh.
4. **Tweet row** (`Tweet`, `StyleHelpers`): divider at top; layered avatar frames 60/54/52/48 px at
   (6,9)/(9,12)/(10,13)/(12,15); name uppercased (header font) to the right; message wraps below with tappable
   links (web address, hashtag, mention); favorite heart button bottom-right of the message; relative age label
   below; transparent buttons for body vs avatar taps; selection glow state.
5. **Replies / Favourites** (`RepliesPage`, `FavoritesPage`): same row list, with pagination "more" button at
   the end and refresh; favourites rows expose favorite/unfavorite.
6. **Direct messages** (`DirectMessageMain`): 33-px pivot Inbox / Sent; `DirectMessagePage` lists 48×48 avatar
   rows (name, first-line message, relative time) with a "more" item for page 2+ (page size 20); tapping opens
   `DirectMessageView` thread with a reply action.
7. **Compose** (`CreateMessageMain`): pivot Tweet / Direct Message. `CreateTweetPage` places add-contact and
   add-music atlas buttons top-left, a multiline text box below spanning the width, a character counter above
   the box, and a send button bottom-right with a loading overlay; counter counts down remaining characters and
   the box turns red when over the limit. `CreateDirectMessagePage` adds a recipient field with contact picker;
   an input starting with `@` from the tweet page retargets to a DM.
8. **Search** (`SearchPage`): search bar with keyboard, results list of tweet rows, more-pages button.
9. **Profile** (`ProfilePage`): user header (avatar, name, handle, location/bio), counts
   (following/followers/recent), recent tweets list, and context actions unfollow / block / direct message; own
   profile lets you open favourites etc.
10. **People** (`FollowsPage`, `FollowsOtherUser`, `AddContactPage`, `UsersRecentTweets`): follower/following
    lists (`FollowEntry` rows), A–Z contact picker with letter dividers and letter selector for DM recipients,
    per-user recent tweets.
11. **Settings** (`SettingsPage`): Account / Terms pivots — account list with add/login/logout, and the
    terms/attribution text page.

Flow: login → main (tabs) → timeline pivots / replies / favourites / DM inbox–sent → thread; Tweet / DM compose
from the context bar; avatar → profile → follows / recent tweets; search from context bar; settings from context
bar.

## 3. Local rules & state

- **Offline:** timeline, friends and followers caches are stored per request key and reloaded as `isCache`
  results on entry, then refreshed remotely; a "no data" label and an error label are toggled by the page
  (`ShowNoDataMessage`, `ShowErrorMessage`). Rate-limit hits show a one-shot warning that self-clears after a
  few seconds (`TwitterAPI_RateLimitHit`).
- **Compose:** send is enabled only for non-empty input at or under the character maximum
  (`TwitterMagicValues.MaximumTweetLength`); the counter shows remaining characters (`CheckForValidSend`);
  over-limit shows a too-long message; add-contact inserts a mention and add-music attaches the now-playing
  track; DM send requires a recipient and non-empty text.
- **Accounts:** multiple accounts stored with a logged-in flag; switch via the header account name
  (`ToggleAccountClick`, `UpdateHeaderName`); logout clears the current user and returns to login; per-user
  cache is destroyed on account removal (`UserSettings.DestroyUserCacheFileByUserId`).
- **Pagination:** lists append a page-sized block per "more" press; tweet cache merges by remote id
  (`MatchTweet`), reads newest-first.
- **Simulation plan:** seed a fixed local timeline/replies/favourites/DM set per account; keep drafts and
  favorite toggles in `graph.appState`; label the feed "offline archive"; no network calls.

## 4. Controls & gestures

- Tap tweet body → tweet detail (`TweetPage`); tap avatar → profile; tap links → browser / hashtag search /
  mention profile (`labelMessage_LinkClicked`).
- Tap heart toggles favorite; tap an attached image toggles its expanded image.
- Horizontal swipe on a row list moves the page pivot (twist); vertical drag scrolls kinetically; header tabs
  jump pivots directly; bottom context bar swaps Back/Tweet/Search/Settings per screen and enablement.
- Pull to refresh on lists (refresh button/indicator); "more" row at list end fetches the next page.
- Keyboard is modal for search, compose, login, and DM recipient entry; letter rail jumps in the contact picker.

## 5. Content inventory (must be re-authored)

- 54 files: 49 `.xnb`, 5 `.xml` (`_index/twitter.json`).
- Fonts: debug face only — re-author with Selawik/token sizes.
- Images: 36 under `Images/` — action-bar atlases (back/tweet/search/settings/refresh), top-bar atlases
  (timeline/replies/favourites/DM + alert blip), add-contact/music/picture, favourite heart, dividers, login
  background and field plates, header art, bird mark, default avatar, loader strip, shadows and
  system-button/refresh families. Names/sizes only.
- Text: `Text/Fonts.xml`, `Text/Text.xml`, strings en/es/fr — never copied; re-author all display copy.

## 6. Implementation plan (Dorado-HD)

Current `TwitterMock` (`ui/apps/mocks/Mocks.kt`) is three handle/text blocks. Delta: rebuild as Main shell with
a four-tab header (Timeline/Replies/Favourites/DMs) and a Timeline/Following/Followers pivot; tweet rows at the
decompiled avatar+name+wrapped-text+heart+age anatomy; compose with a live character counter, send gating;
thread and profile screens; "more" pagination; visible "offline archive" banner. Pure logic for character
counting, pagination and cache merge; state in `graph.appState`. Tokens only, `DetailScaffold`, zero corner
radius.

- Screens: `ui/apps/mocks/TwitterApp.kt` (replace `TwitterMock` in `Mocks.kt`).
- Logic: `ui/apps/TwitterModel.kt` (`TweetCache`, `composeState`, `visiblePage`).
- Tests: `app/src/test/java/com/heretek/dorado_hd/TwitterModelTest.kt` — 140-char counter and send gating, page
  slicing, id-based merge, @/hashtag link splitting.

```kotlin
@Composable fun TwitterApp(graph: DoradoGraph)
class TweetCache(pageSize: Int) { fun merge(new: List<Tweet>): List<Tweet> }
fun composeState(text: String): ComposeState  // enabled, remaining, overLimit
```

## 7. Citation log

All refs `Twitter.exe!` unless noted.
- `MainPage.CreateHeaderSection`, `MainPage.CreateContextBar`, `MainPage.ToggleAccountClick`,
  `MainPage.UpdateHeaderName`, `MainPage.ChangeToPage`
- `TimelineMain.CreateTimelinePivot`, `DirectMessageMain.CreateDirectMessagePivot`,
  `CreateMessageMain.CreateDirectMessagePivot`
- `Tweet` constructor, `Tweet.labelMessage_LinkClicked`, `Tweet.favoriteHeart_Clicked`, `Tweet.SetTweetTime`
- `FollowEntry` constructor, `DirectMessage` constructor, `MoreButton.OnClick`, `RefreshButtonView`
- `CreateTweetPage.PopulatePage`, `CreateTweetPage.CheckForValidSend`, `CreateTweetPage.SendButtonClicked`,
  `CreateTweetPage.ShowTweetTooLongMessage`
- `CreateDirectMessagePage.CheckForValidSend`, `CreateDirectMessagePage.SendButtonClicked`
- `TwitterStore.GetTimelineCache`, `TwitterStore.GetFreindsCache`, `TwitterStore.GetFollowersCache`,
  `TwitterStore.MatchTweet`, `TwitterStore.TwitterAPI_RateLimitHit`
- `UserSettings.Load`, `UserSettings.Save`, `UserSettings.DestroyUserCacheFileByUserId`, `AccountPage.Login`
- `TimelinePage.ShowNoDataMessage`, `TimelinePage.ShowErrorMessage`, `DirectMessagePage.ParseData`
- `TwitterApp.OpenBrowser`, `TwitterApp.ShowMessage`; `TwitterAPI.dll!TwitterMagicValues`
- Inventory: `_mine/_index/twitter.json`; content tree `Images/{ActionBar,TopBar}`, `Text/{Fonts,Text}.xml`.
