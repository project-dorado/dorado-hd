# Weather

- **Official package:** `Weather.exe` (weather)
- **Corpus:** `Zune HD Apps (Decompiled)/weather` (external, untracked)
- **Wave:** W7 · **Category:** reading
- **Status:** `mock` · **Complexity:** M
- **Dorado-HD id:** weather

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior & provenance

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Weather.exe` | 16 | 79 | 2114 |
| `Microsoft.Xna.Zune.dll` | 83 | 798 | 14932 |
| `ZuneAppLib.dll` | 83 | 365 | 9920 |

> Brief note — the W7 task describes Weather as corpus-absent ("a pure zune.net
> service client") and directs this spec to the Dorado-HD mock and canon. The
> workspace does contain a `Weather.exe` decompile (2,114 lines) plus a content
> folder, so this spec is grounded in the mock/canon per the brief and
> **corroborated where marked** by `Weather!Type.Method`. If the corpus is
> off-limits for this app, drop the corroborating refs in §7; the screen plan
> stands on the mock + canon alone.

Marketplace blurb: "Forecasts and conditions around the world." The app is a thin zune.net/MSN Weather client:
current conditions and a multi-day forecast per city, a city manager, °F/°C preference, and an info/about panel.
There is no offline forecast store beyond the last serialized snapshot (`data.xml`) and the unit preference
(`options.xml`, `WeatherOptions.TempSystem`), both written on exit. `CityData.AgeData` is the local retention
rule: current conditions are dropped after 3 hours, forecast rows are pruned when their local day passes, and a
missing current reading falls back to the first forecast row's high/low and condition (temperature unknown).

## 2. Screens & navigation

The flow below follows the Dorado-HD mock (`WeatherMock`) and the canon mini-app shell (§8: full-screen, cropped
header back, no chrome; §3.6); the dimensions are corroborated from the decompile.

1. **City page** (`WeatherMock` layout; `WeatherLocationViewController`, 272×431):
   - location line (city, region), 30 px tall at top-left;
   - current-condition glyph ~128 px square below-left;
   - current temperature large at top-right;
   - high / low pair on one line (high white, low grey);
   - a 7-row forecast list, one row per day (`WeatherDataRowView`, 272×40: day name left at x+16, 32-px
     condition glyph centred, high right-aligned −80, low right-aligned −24 in grey);
   - a last-refresh line under the list; when data is stale it degrades to "N days ago / N hours ago / N minutes
     ago", and is hidden when empty;
   - a message block (240×80) for the no-city state and the network-off state. The mock prints a frozen 7-day list and
     a stale "last seen" stamp; the rebuild keeps that row geometry and labels the data archived.
2. **City paging**: city pages are swipeable horizontally (one page per city); the page nudges ±3 px on
   touch-down as feedback (`WeatherPageViewController .TouchesBegan`). No city → a single blank page with the
   empty message.
3. **Settings** (`SettingsViewController`; mock currently has none): a panel pushed over the city pages with a
   title/back bar, an °F/°C two-option toggle (`TextToggle`, `TempSystem`), the city list as 272×35 rows
   (`CityEntryView`: plus/minus button at x=10, label at x=48), an add-city search (modal keyboard with live
   suggestions), and an info button opening the About panel. Closing saves `data.xml` and `options.xml`.
4. **About / information** (`InformationViewController`): logo plus a scrollable information block (232×333 at
   20,100).
5. **Empty / error states:** no-city message, network-off message when the network is unavailable, and a
   refresh error path; the mock's frozen-data banner substitutes for these in Dorado-HD.
6. **Refresh**: a system refresh button top-right re-requests every city page with a code; results update in
   place. Dorado-HD replaces this with a "refresh simulation" that visibly re-stamps the archived timestamp.

## 3. Local rules & state

- **Unit preference:** °F/°C stored as `WeatherOptions.TempSystem`; the toggle flips immediately and persists
  (`SettingsViewController.changeTempType`). Values are stored in °F and converted for display (integer
  truncation).
- **Cities:** `CityData` holds display name, weather code, time zone, current reading, daily list and
  last-refresh stamp; add via search suggestions, remove by revealing minus and pressing it (fade-out
  animation), reorder by drag; up to one page per city. Dorado-HD seeds 3–5 fictional cities.
- **Staleness rule:** current data expires after 3 hours; daily rows expire at the local day boundary; fallback
  current uses the first daily row's high/low and condition (unknown temperature shown as no reading). This rule
  is pure logic and should be re-implemented and unit-tested.
- **Offline:** nothing refreshes; the last snapshot remains visible with the archived timestamp. The information
  panel and unit toggle work offline.
- **Persistence:** cities + last-refresh in `graph.appState` (`WeatherState`), unit toggle in the same blob;
  settings changes saved on leaving the panel.
- **Simulation plan:** no network; condition glyphs re-authored as token-styled vector/solid glyphs labelled
  "archived"; the settings search matches the seeded city list locally.

## 4. Controls & gestures

- Swipe left/right horizontally to change city; vertical scroll moves within a city page (forecast list).
- Tap the refresh button to re-stamp; tap the settings button to open the panel; cropped header/system back
  returns.
- Settings: tap the °F/°C toggle; tap a city row to go to it; drag a row to reveal delete; hold/press minus to
  confirm removal; tap plus to open the city search keyboard; suggestions select and add.
- Touch-down on a city page produces the ±3 px nudge; no other hidden gestures.
- Dorado-HD adaptation: the 272-px column becomes a full-bleed column on the 480×272 canvas; keep type sizes
  from `DoradoTokens` and zero corner radius.

## 5. Content inventory (must be re-authored)

- 30 files: 12 `.xnb` fonts, 14 `.png`, 4 `.xml` (`_index/weather.json`).
- Per-screen bitmap fonts: CurrentTemp, CurrentHighLow, Location, Weather, LastRefresh, Loading, ErrorMessage,
  Information, Settings, SettingsTabs, PageTitle, SmallInformation — re-author as Selawik + token sizes.
- Images: boot screen, 128-px and 32-px condition atlases (plus es/fr variants), loading logo, MSN weather logo,
  system-button and refresh animations. All artwork is Microsoft's: re-author condition glyphs and logos from
  scratch (no MS marks).
- Text: `TextDictionary.xml`, strings en/es/fr — never copied; write original copy for the archived-data
  framing.

## 6. Implementation plan (Dorado-HD)

Current `WeatherMock` (`ui/apps/mocks/Mocks.kt`) is one static city block with a frozen 7-day list. Delta:
rebuild as a real weather shell — horizontally paged city pages with location, 128-px condition placeholder
glyph, large current temperature, hi/lo, and the 7×40-px forecast rows (day / glyph / hi / low); an archived
last-refresh line; settings panel with °F/°C toggle and city list add/remove; empty "no city" state. All data is
labelled "archived 2012 — service closed". Tokens only, `DetailScaffold`, zero corner radius.

- Screens: `ui/apps/mocks/WeatherApp.kt` (replace `WeatherMock` in `Mocks.kt`).
- Logic: `ui/apps/WeatherModel.kt` (`City`, `ageOut(now)` implementing the 3-hour/next-day retention rule,
  `formatTemp`, `formatAgo`).
- Tests: `app/src/test/java/com/heretek/dorado_hd/WeatherModelTest.kt` — 3-hour current expiry, day-boundary
  pruning, fallback-from-forecast, F↔C rounding.

```kotlin
@Composable fun WeatherApp(graph: DoradoGraph)
fun ageOut(city: City, now: Instant, tz: ZoneId): City
fun formatTemp(f: Int, unit: TempUnit): String
```

## 7. Citation log

Per the brief this app is derived from the mock + canon; corpus refs are marked `[corpus]` and can be dropped if
the Weather.exe decompile is out of scope.

- Mock: `Mocks.kt!WeatherMock` (city line, large temperature, stale line, seven day/hi-lo rows, token type
  sizes).
- Canon: `docs/zune-hd-ui-canon.md` §3.6 (apps open fullscreen, cropped-header back), §8 (mini-app platform, no
  MiniPlayer, tokens); `docs/design-tokens.md` (`DoradoTokens.TYPE_*`, `EDGE`, zero radius).
- Marketplace description: `docs/official-apps.json` entry `weather` ("Forecasts and conditions around the
  world").
- `[corpus]` `Weather!WeatherApp.ApplicationFinishedLoading` (page controller, refresh + settings buttons, logo,
  data.xml load, demo cities), `Weather!WeatherApp.OnExiting` (options.xml/data.xml save)
- `[corpus]` `Weather!WeatherLocationViewController` constructor (location, 128-px condition, temp/hi/lo
  positions, message block, daily list), `!Refresh`, `!UpdateLastRefreshLabel`, `!MakeBlankForBoot`
- `[corpus]` `Weather!WeatherDataRowView` constructor and `!Draw` (272×40, day, 32-px glyph, right-aligned hi
  −80 / lo −24, grey low)
- `[corpus]` `Weather!CityData.AgeData` (3-hour current expiry, local-day pruning, first-forecast fallback)
- `[corpus]` `Weather!SettingsViewController` constructor, `!ConfigureCityList`, `!addCity`, `!deleteCity`,
  `!getSearchResults`, `!changeTempType`; `Weather!CityEntryView` constructor, `!TouchesBegan`,
  `!deletePressed`, `!DeleteAnimation`; `Weather!TextToggle`; `Weather!TempSystem`; `Weather!WeatherOptions`;
  `Weather!InformationViewController`; `Weather!WeatherPageViewController.TouchesBegan`
- Inventory: `_mine/_index/weather.json` (fonts, condition atlases, logo).
