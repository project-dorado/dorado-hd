# Fan Prediction

- **Official package:** `FanPrediction.exe` (fan_predications)
- **Corpus:** `Zune HD Apps (Decompiled)/fan_predications` (external, untracked)
- **Wave:** W4 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `FanPrediction.exe` | 76 | 331 | 7673 |
| `Microsoft.Xna.Zune.dll` | 111 | 827 | 15830 |
| `ZuneAppLib.dll` | 67 | 363 | 8882 |
| `ZuneCoreLib.dll` | 80 | 288 | 8183 |

- A networked companion app on the ZuneAppLib view-controller framework,
  not a local game: no gameplay physics, all content is fetched from the
  service tier. Portrait layout with multitouch enabled.
- On first launch the app generates a GUID device ID and stores it in
  ZuneCoreLib settings; that GUID becomes the account username (see §3).
  Network state is tracked live and drives the two error banners.

## 2. Screens & navigation

ZuneAppLib `NavigationBar` + view-controller stack (back button top-left,
info/settings system buttons top-right):

- Sports list (each sport has an icon and a mask icon) → sport branch → team
  list (browsable by team initial) or games list.
- Games views: upcoming games (90-day window), past games, and game detail
  with team names, kickoff date/time, status, final score and prediction
  subscores (predicted score, average predicted score, "(tap to predict)"
  prompt).
- Prediction sheet: separate home/visitor score fields with an on-screen
  0–9 + Del keypad and a Save Prediction button; saving posts and returns.
- Standings: table with Name / Pts / % columns, the current user's row
  highlighted, and a header row. Two tables are served — all users and the
  user's own standing.
- Settings: nickname, first name, last name, email fields plus a favorite-team
  selector reached from the team list ("Set Favorite Team" / "Unset").
- About page (iHwy branding, version, copyright). Login is not a UI screen —
  it runs silently in the background.

## 3. Rules, scoring & progression

There is no game scoring; the product is a sports-prediction client.

- **Silent account flow.** `Login` posts the device GUID as username, an empty
  password and `ipa=true`; a REGISTER response triggers device registration and
  a retry, otherwise the token is stored. Secure calls then carry username +
  token in the body and as `x-auth-username` / `x-auth-token` headers.
- **Requests.** GET-shaped `.ashx` calls with `x-app-identifier/version/
  device-model` headers; JSON via the bundled mapper. Families: sports, teams
  by sport, games by ID, upcoming games (90-day window), standings (limit 50),
  predictions, make-prediction, authentication, registration, update-user.
- **Prediction flow.** Games expose `PredictionsEnabled`; the player enters
  integer home/visitor scores and saves, creating/updating the account
  prediction. Rows show predicted score, final score when played and the
  community average predicted score.
- **Standings.** Server rows expose name/points/%; `isYou` marks the player for
  highlight, and all progression is account history rather than local state.
- **Favorite team.** Writes a favorite to the profile and local settings
  (keyed per sport); the UI then shows "Unset" and a past-games shortcut.
- **Profile/persistence.** Nickname/first/last/email are optional, pushed via
  update-user, and the nickname is the standings name; local settings hold
  device ID, profile fields and favorite teams; the server owns predictions.
- **Failure handling.** Offline → connect banner; request exception → server
  error banner. Splash shows until startup calls complete.

## 4. Controls

- Touch list navigation: tap rows to push views, navigation-bar back to pop;
  tables scroll with the framework's fling/rubber-band behavior.
- Prediction input: tap numeric keypad buttons (0–9, Del) to fill the active
  score field, then Save Prediction. Multitouch is enabled but used only by
  the framework's scroll handling.
- Settings uses the ZuneAppLib letter picker (keyboard overlay) for text
  fields; favorite team is set by tapping the team row's action.
- No accelerometer or hardware-button input in the app.

## 5. Content inventory (must be re-authored)

43 files: 42 `.xnb` images + `Text/Fonts.xml` (no audio assets ship with the
app; cues are framework system sounds).

- Keypad digits `0`–`9` plus Del text; sport glyphs + mask icons (football,
  basketball, hockey, soccer, helmet) and `GameThumbnail` for game rows.
- Chrome: `NavBar`, up/down nav buttons, `home`/`home-on`, `ButtonBorder`,
  settings/round buttons, `HorizLine`, `Default` splash, `FanPredictionLogo`.
- Framework system sheets (buttons, volume, highlight, refresh), `MaskEffect`
  resources, and `Text/Fonts.xml` fonts.
- No audio ships; cues are framework system sounds.

Re-author: token-style sport glyphs/masks, keypad digits, nav chrome and logo,
with sport/team identity drawn from synthesized data.

## 6. Implementation plan

- Engine: `ui/apps/games/fanprediction/FanPredictionEngine.kt` — pure Kotlin
  `object` over an immutable `State` (auth session, sport/team/game caches,
  predictions map, standings, profile); no physics `step`, event-driven
  `apply(event, response)` so it stays deterministic and testable offline with
  a fake transport.
  ```kotlin
  object FanPredictionEngine {
      fun apply(state: State, event: Event): State
  }
  ```
- Transport: a `PredictionsApi` interface with a Ktor/HTTP implementation
  pointing at the self-hostable Dorado cloud (the clean-room server tier),
  plus an in-memory fake for tests. Device GUID identity maps to an anonymous
  account on first use, preserving the original silent-register behavior.
- Screen: `FanPredictionScreen.kt` in `DetailScaffold`; portrait Compose UI
  (sports list, team/game lists, standings table, prediction keypad) drawn
  with tokens only, zero corner radius. Canvas is not needed for this app —
  standard Compose lists/tables are appropriate.
- Persistence: device ID, profile fields and favorite team through
  `graph.appState`; predictions/standings stay server-side (no `graph.games`
  high score needed).
- SFX via `SfxBank`: tap/select/refresh/failure cues.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): silent-register retry
  logic, header/token attachment, JSON mapping to models, prediction
  validation and save flow, standings `isYou` highlight selection, favorite
  team round-trip, offline/error state transitions.

## 7. Citation log

- `FanPrediction.exe!FanPrediction.FanPredictionApp` — device ID generation
  and storage, startup calls, network banners, splash lifetime, multitouch.
- `FanPrediction.exe!FanPrediction.Login` — GUID username, `ipa` flag,
  REGISTER → Register → retry, token handling.
- `FanPrediction.exe!FanPrediction.SecureWebService` — deferred login, token
  and username body fields, `x-auth-*` headers.
- `FanPrediction.exe!FanPrediction.WebService` — endpoint path/method pair,
  app-id/version/device-model headers, global error handler.
- `FanPrediction.exe!FanPrediction.GetUpcomingGames` — 90-day window and
  sport filter.
- `FanPrediction.exe!FanPrediction.GetStandings` — standings limit 50 and
  all-user vs user-standings requests.
- `FanPrediction.exe!FanPrediction.GetPredictions` / `MakePrediction` —
  prediction fetch and save payload (home/visitor scores).
- `FanPrediction.exe!FanPrediction.Game` — game fields (teams, date, status,
  final scores, predictions-enabled) and predicted/average score accessors.
- `FanPrediction.exe!FanPrediction.PredictionViewController` — keypad 0–9 +
  Del, home/away fields, Save Prediction.
- `FanPrediction.exe!FanPrediction.StandingsViewController` /
  `StandingsCellView` — Name/Pts/% columns and `isYou` row highlight.
- `FanPrediction.exe!FanPrediction.SettingsViewController` — profile fields,
  settings persistence, update-user metadata keys.
- `FanPrediction.exe!FanPrediction.TeamsViewController` /
  `TeamLetterViewController` — team list and favorite-team action.
- `FanPrediction.exe!FanPrediction.SportsViewController` /
  `GamesViewController` / `UpcomingGamesViewController` /
  `GameViewController` — navigation graph; `Register` / `Team` / `Sport`
  cover device registration and model fields/icon loading.
