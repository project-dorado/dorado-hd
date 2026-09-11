# Facebook

- **Official package:** `Facebook.exe` (facebook)
- **Corpus:** `Zune HD Apps (Decompiled)/facebook` (external, untracked)
- **Wave:** W7 · **Category:** social
- **Status:** `mock` · **Complexity:** XL
- **Dorado-HD id:** facebook

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior & provenance

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Facebook.exe` | 183 | 1357 | 30085 |
| `FBApiService.dll` | 400 | 621 | 15351 |
| `Matchbox.Zune.dll` | 55 | 261 | 5613 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `ZuneAppLib.dll` | 53 | 361 | 7678 |
| `ZuneCoreLib.dll` | 71 | 292 | 6334 |

The device's Facebook client. `FBApiService.dll` wraps the platform REST/FQL endpoints (auth/session,
permissions, stream, wall, mail, photos, friends, notifications) with request/response objects;
`keydata.dat` carries the app credentials; `Facebook.exe` is the UI plus cache layer. `FBApiWrapper` keeps a
password-free session and an on-disk profile-picture cache; `FBNewsFeedWrapper`, `FBMailWrapper`,
`FBProfileWrapper` and `FBNewsFeedWrapper.SaveCache` persist feeds/threads; `FBErrorHandler` maps service
error codes to display text. `UserSettings` persists the last user, session and options; the login flow
validates the stored session before showing `LoginPage`. The app also supports auto-reload intervals,
shake-to-refresh (accelerometer), and keyboard tilt.

## 2. Screens & navigation

1. **Login** (`LoginPage`): blue vertical gradient, centred logo, e-mail and password boxes, login button,
   animated loader overlay while authenticating, last-user/session auto-check, and the permission-grant flow
   when the session lacks mail/stream scopes.
2. **Shell** (`MainApplicationPage`): full-screen page with a bottom main toolbar of four tabs — Home /
   Profile / Friends / Messages — and a context toolbar with Back / Notifications / Settings whose
   visibility/enablement each controller sets. Each section has a sub-navigation view: a 33-px swipe bar
   (pivots) plus a text/search bar directly beneath.
3. **Home** (`HomePageController`): pivots News Feed / Status Updates / Photos; the text bar is a status prompt
   with a Now Playing button that shares the current track; a post publishes the status
   or music share and refreshes the list to top. Rows (`NewsFeedUIIView`): cropped profile picture, name and
   posted date, message text with tappable links, likes/comments labels with small icons, attachment image
   (63×85), app icon, album label, and inline photo thumbnails; friend-request and next-page buttons
   terminate the list; initial page 25 items.
4. **Profile** (`ProfilePageController`): Wall / Info / Photos pivots for the owner or a friend; non-friends
   get Info only, not draggable, with a privacy warning and Add-as-friend button. Wall rows support the wall
   text bar; Info shows the profile header (picture, name, status) and key/value rows; Photos shows album
   rows and a `PhotoThumbViewRow` grid.
5. **Photo viewer** (`PhotoViewController`): full-screen image, swipe between photos, side-image preloading,
   tag/label overlays, comment button, auto-hiding buttons, orientation-aware relayout.
6. **Friends** (`FriendsPageController`): Friends pivot (single, non-scrolling pivot) with a search-inbox
   bar and a list/grid toggle button — list of `ProfileEntry` rows, or a photos grid of friends.
   `FriendRequestsPage` renders confirm/ignore rows. `PhoneBookPage` exists as a secondary people view.
7. **Messages** (`MessagesPageController`): Inbox / Sent / Updates pivots, search-inbox text bar and a
   Compose button. Thread rows show profile image, from-name, subject, date and snippet; unread rows get a
   tinted background and a spot. `ViewMessagePage` renders a thread (subject, message entries with
   avatar/name/time/body, reply box with send, delete) and back returns to the folder. `ComposeMessagePage`
   has an addressee chip field (friend picker), a subject line and a message box, with send/close behavior
   described below.
8. **Notifications / Settings**: `FacebookNotifications` — header with the account, typed notification rows (comment/wall/like/link/music/photo/video/friend-request/…), unread count badge, mark-as-read, tap-through targets. `SettingsPage` — Options / About / Terms pivots; Options has account header, logout, notification and auto-reload settings, shake-to-reload and keyboard-tilt toggles; Terms is a text page with clickable links.

Flow: login → shell tabs → Home pivots / Profile pivots → album → photo viewer; Friends list ↔ photo grid →
friend requests; Messages folder → thread → reply / compose; Notifications → target screens; Settings →
logout → login.

## 3. Local rules & state

- **Offline/cache:** feeds, mail threads and friends render from cache while a network refresh runs
  (`FBMailWrapper.GetCacheForFolder`, `FBNewsFeedWrapper.SaveCache`, per-page `GetCachedPage`); profile
  pictures are cached by file name with square/big variants (`FBApiWrapper.GetPic`, `GetProfilePicByID`).
  Errors are translated by `FBErrorHandler.GetErrorMessage`; failed posts show a message box.
- **Compose validation:** `ComposeMessagePage.ValidateRequiredFields` requires at least one addressee AND a
  non-empty subject AND non-empty message; bodies at/over 5000 chars are rejected with a warning; closing a
  touched composer prompts before discarding (`promptUserOnClose`); on success the thread is sent and the
  page refreshed, then controls reset.
- **Mail:** unread/read state is tracked per thread and persisted in the mail cache (`MarkReadInDB`,
  `MarkasRead`); deletion removes the row and calls the delete endpoint (`DeleteMail`,
  `deleteButton_Clicked`); folder search filters threads locally by body text (`SearchThreadsMessageBody`,
  `ApplyFilter`).
- **Feed:** like/unlike from the row updates the likes label; comments/likes open a `CommentsAndLikes` page
  with its own input; the next-page action loads older items with a rolling date window.
- **Friend requests:** confirm/ignore per row with immediate list update (`confirmbutton_Clicked`, `ignorebutton_Clicked`); auto-reload interval and shake-to-reload persist per user (`UserSettings.Save`).
- **Simulation plan:** seed a fixed feed, friends list, two mail folders and notifications; keep likes/read/deletes and composer drafts in `graph.appState`; photos replaced with token-coloured placeholders; visible "offline snapshot — service closed" banner.

## 4. Controls & gestures

- Bottom tabs switch sections; swipe bars switch pivots; text bars open the modal keyboard for search,
  status, wall or compose.
- Vertical kinetic scroll everywhere; pull-to-refresh on feed/mail lists with refresh animations; next-page/more rows append pages.
- Shake the device to refresh the active page (`MainApplicationPage.ShakeDetected`).
- Tap feed row body for detail; tap picture → photo viewer; tap likes/comments labels → `CommentsAndLikes`;
  tap links → browser.
- Photo viewer: swipe left/right pages photos, tags toggle, comment opens; orientation changes reposition
  buttons.
- Friend requests: dedicated confirm/ignore buttons; mail rows reveal a delete button; messages thread has a
  reply input and delete.

## 5. Content inventory (must be re-authored)

- 87 files: 81 `.xnb`, 5 `.xml`, 1 `.dat` (`_index/facebook.json`).
- Fonts: debug face only — re-author with Selawik/token sizes.
- Images: 14 top-level (EmptyProfile, MainTabButtons, login-logo, pic_h, pivot backgrounds, popup,
  system-button/refresh families) plus 11 Buttons, 2 ActionBarIcons, 12 NotificationIcons, 9 Wall Icons, 16
  Search/Update bar parts, 3 Loading, 4 Mail, 2 MainNavigation, 3 Facebook glyphs, 3 Text Tap plates.
  Names/sizes only.
- Text: `Text/Fonts.xml`, `Text/Text.xml`, strings en/es/fr — never copied; re-author all display copy.
- `keydata.dat` (133 B): credential blob, never copied or reproduced.

## 6. Implementation plan (Dorado-HD)

Current `FacebookMock` (`ui/apps/mocks/Mocks.kt`) is three name/action blocks. Delta: rebuild as the
four-tab shell (Home/Profile/Friends/Messages) with Home pivots News Feed/Status Updates/Photos plus a
status composer bar; feed rows with profile-pic block, name/date, link-split message, likes/comments labels
and attachment; Friends list with photos-grid toggle; Inbox/Sent/Updates with thread view and addressee-chip
compose (validation + discard prompt); Notifications list; Settings; visible "offline snapshot" banner. All
state (likes, read, drafts) via `graph.appState`; tokens only, `DetailScaffold`, zero corner radius.

- Screens: `ui/apps/mocks/FacebookApp.kt` (replace `FacebookMock` in `Mocks.kt`).
- Logic: `ui/apps/FacebookModel.kt` (`FeedCache`, `MailFolder`, `validateCompose`).
- Tests: `app/src/test/java/com/heretek/dorado_hd/FacebookModelTest.kt` — compose validation
  (addressee/subject/body, 5000 limit), folder filter and read-state merge, page slicing.

```kotlin
@Composable fun FacebookApp(graph: DoradoGraph)
class FeedCache(pageSize: Int) { fun merge(items: List<Post>): List<Post> }
fun validateCompose(to: List<String>, subject: String, body: String): Result
```

## 7. Citation log

All refs `Facebook.exe!` unless noted.
- Shell: `MainApplicationPage.ViewableDataArea`, `!ConfigureMenubarFor`, `!ShakeDetected`, `!ShowFriendRequests`
- Home: `HomePageController.TextBar_TextBoxClicked`, `!TextBar/ButtonClicked`, `!publishStatus`, `!publishMusicStatus`, `!GetPage`; `NewsFeedPage` (25-item page), `HomePhotosPage.Next25Button`, `StatusUpdatesPage.CreateStatusUpdateItemFromEntry`
- Rows: `NewsFeedUIIView` fields, `!UpdateCommentsAndLikesText`, `NewsFeedPage.CreateNewsFeedView`, `CommentsAndLikes.Setup`
- Profile/photos: `ProfilePageController.ConfigureMenubarFor`, `!GetInitialPage`, `!ShowProfileAlbum`, `ProfileAlbumsPage`, `PhotoViewController.ShowPhotos`, `!TouchesMoved`, `!SetLabels`
- Friends: `FriendsPageController` constructor, `!ViewButtonClicked`, `FriendsPage.SearchBoxClicked`, `FriendRequestsPage.CreateEntry`, `!confirmbutton_Clicked`, `!ignorebutton_Clicked`
- Messages: `MessagesPageController` constructor, `!ShowComposeMessagePage`, `InboxPage.Folder`, `SentItemsPage`, `BaseMailPage.DisplayData`, `!SearchMessages`, `MailItem.Setup`, `!deleteButton_Clicked`, `ViewMessagePage.DisplayMailItems`, `!CreateMessageEntry`
- Compose/wrappers: `ComposeMessagePage.ValidateRequiredFields`, `!SendButton_Clicked`, `!promptUserOnClose`, `!AddAddressee`; `FBMailWrapper.GetMailThreads`, `!SendNewThread`, `!DeleteMail`, `!MarkasRead`, `!GetCacheForFolder`; `FBNewsFeedWrapper.SaveCache`, `FBApiWrapper.GetPic`, `!GetProfilePicByID`, `FBErrorHandler.GetErrorMessage`
- Login/permissions: `LoginPage` constructor, `!CheckForSavedUserSession`, `FBAuthenticateWrapper.Authenticate`, `FBPermissionsWrapper.GrantAllRequiredPermissions`
- Notifications/settings: `FacebookNotifications.UpdateUnReadCount`, `!DisplayData`, `!MarkAsRead`, `OptionsPage.CreateHeader`, `!ShakeToReloadClicked`, `!AutoReloadClicked`, `!btnLogout_Clicked`, `UserSettings.Load`, `!Save`, `!GetLastUserAndWait`
- Inventory: `_mine/_index/facebook.json`; content tree `Images/{Buttons,NotificationIcons,Wall Icons,Search Update Bar,Mail,MainNavigation}`, `Text/{Fonts,Text}.xml`.
