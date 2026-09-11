# MSN Money

- **Official package:** `MSNMoney.exe` (msn_money)
- **Corpus:** `Zune HD Apps (Decompiled)/msn_money` (external, untracked)
- **Wave:** W7 · **Category:** reading
- **Status:** `mock` · **Complexity:** L
- **Dorado-HD id:** msnmoney

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior & provenance

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `MSNMoney.exe` | 35 | 202 | 5714 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `ZuneAppLib.dll` | 57 | 367 | 7742 |
| `ZuneCoreLib.dll` | 67 | 297 | 6188 |

A thin finance client: quotes, charts, indices, currencies and news are all fetched from MSN Money feeds
(quote/symbol search, chart image service, RSS dispatches and per-symbol feeds — described at high level only).
`FinanceApp` owns three `Portfolio` collections (watchlist, market indices, currencies) plus a `Newspaper`;
state is persisted as `portfolio.xml`, `indices.xml`, `currencies.xml`, `newspaper.xml` and `settings.xml`
(`Constants`). `BaseController` supplies the refresh button, last-updated label, error reporting and
dim-while-loading behaviour. Boot shows a full-screen boot image; the main screen is a bottom twist menu.

## 2. Screens & navigation

1. **Boot** (`FinanceApp.LoadBootScreenContent`): full-screen boot image and loading text while content
   registers.
2. **Main twist menu** (`TwistMenuController`): content region x 272 at y=50 (380 px tall); bottom twist buttons
   at (6,433), (46,433), (177,433), (217,433) for Watchlist / Markets / Currencies / Articles (Articles is
   English-only); MSN Money logo bottom-centre at (72,445,128×29); refresh button top-right; contextual system
   buttons at bottom-left — watchlist manager (gear), currency converter, and info (about). Section titles use
   the MenuItem face; active white, inactive grey.
3. **Quote list** (`PortfolioViewController`): vertical list of `PortfolioQuoteView` rows, each 272×40 — short
   name/symbol at (16,11,95×20), change % right-aligned at (212,3), 8×8 change arrow at (196,12), last price
   right-aligned at (176/136,22), 32×20 country-flag atlas cell at (220,23), forward/add buttons; a toggle
   switches StockView ↔ PortfolioView (`ToggleView`); tap opens the quote detail.
4. **Quote detail** (`DetailedQuoteView`): flag (232,8,32×20), last price (180,40,84×20), change + arrow, chart
   at (0,110,272×154) (full-screen 480×272 in landscape); stat rows in two columns (prev close, open, day
   high/low, volume; year high/low, P/E, EPS, market cap) reachable by scroll; news and currency buttons;
   `LargeIntToReadableString` abbreviates big numbers.
5. **Chart** (`ChartView`, `ChartRange`): range tabs OneDay..Max, touch-drag overlay reads a point, cached chart
   image reused per range+size (`TryUseLastImage`), range switch re-fetches.
6. **Currency converter** (`CurrencyConverterViewController`): amount button (10,88,252×40), initial-currency
   label (0,130), from-card (8,164,100×120) and to-card (152,164,100×120) carousels with prev/next arrows, flag,
   name and symbol, a gradient plate behind (16,164,256×120), result value (0,325,250×48) and result currency
   (0,374,250×20); modal numeric keyboard; validates non-numeric, too-large and decimal errors.
7. **Currency carousel** (`CurrencyView`): 182×80 card button, 32×20 flag, name/symbol labels, 10×10 prev/next,
   forward/back rotation.
8. **Trade entry** (`TradeEntryView`): num-shares and share-price fields with Buy and Sell actions, computing
   the trade total; on-screen numeric keyboard.
9. **Articles** (`NewspaperViewController`): `NewsClipView` rows 272×70 — title (25,8,227×20), author (32,24),
   date (32,40); tap opens `DetailedNewsView` (scrollable article with a link action).
10. **Watchlist manager** (`PortfolioManagerViewController`): add/delete `QuoteEntryView` rows 272×35
    (plus/minus system button at (10,3), symbol and name; drag left reveals minus); search keyboard with
    suggestions (min 1 char); delete confirmation; cap 50 quotes (`Constants.MAXQUOTES`).
11. **Information** (`InformationViewController`): about/help text screen pushed over the menu.

Flow: boot → twist menu → Watchlist/Markets/Currencies lists → quote detail → chart / news / currency; Articles
→ article; manager adds/removes quotes; info panel.

## 3. Local rules & state

- **Offline:** portfolios, indices, currencies and news are saved to XML and re-loaded at startup; refresh runs
  in a bounded loop (`screensRefreshing`, max refresh window) and each controller shows a last-updated label
  whose wording degrades to days/hours/5-minute buckets (`BaseController.UpdateLastUpdated`). Network failures
  surface the error line (`ReportError`, `AddErrorText`) and dim the UI (`DimUI`).
- **Watchlist:** add via search suggestions, delete with confirmation; quotes capped at 50; list order is
  insertion order; rows are drag-indented to expose the delete button (`QuoteEntryView.Indent`).
- **Currency converter:** from/to currencies and last amount persist in `settings.xml` (`GlobalSettings`); a
  mapping table supplies symbols and carry-trade math (`createCurrencyMapping`, `calculateCarryTrade`);
  conversion reads the latest quote and validates input length/decimals.
- **Quotes/charts:** quote XML parsed per symbol (`QuoteData.Parse`); chart images cached by range+size;
  prices/changes formatted with sign and abbreviated magnitudes.
- **Simulation plan:** freeze one watchlist, one index set, one currency pair and 6–10 articles as seeded local
  data; keep watchlist edits, converter from/to/amount and last section in `graph.appState`; label the screen
  "archived 2012 — no live quotes"; no network.

## 4. Controls & gestures

- Bottom twist buttons switch sections; the header strip shows the active section title; tap the logo/left
  buttons opens the contextual action (manager, converter, info).
- Vertical kinetic scroll in lists and articles; row tap opens detail; page refresh via the top-right refresh
  button with animation.
- Chart: drag horizontally to scrub the value overlay, choose a range tab; rotating to landscape expands the
  chart to full screen (`ChartOrientationChanged`).
- Currency: prev/next arrows rotate the carousel, tap a card to select; keyboard for amount entry.
- Trade: tap fields to edit, Buy/Sell buttons; keyboard for numbers.
- Manager: drag a row to reveal delete, tap minus to confirm; keyboard with suggestions for ticker search.

## 5. Content inventory (must be re-authored)

- 20 files: 14 `.png`, 5 `.xml`, 1 `.xnb` (`_index/msnmoney.json`).
- Images: boot screen, arrow-left/right, arrows-8 change glyphs, `chartHint` (168 KB), list `entry`, `flags`
  (4×7 atlas), gradient, MSN Money logo, system-button and refresh-animation families. Names/sizes only.
- Text: `Text/Fonts.xml`, `Text/Text.xml`, strings en/es/fr — never copied; re-author all display copy.
- No bundled data feeds; the quote/news XML format is external and must be replaced with local seeds.

## 6. Implementation plan (Dorado-HD)

Current `MsnMoneyMock` (`ui/apps/mocks/Mocks.kt`) is one index value plus three section headlines. Delta:
rebuild the twist menu with Watchlist / Markets / Currencies / Articles; quote rows with symbol, price, change
arrow and colour-coded change%; quote detail with a flat chart panel and stat grid; articles list and article
view; currency converter with from/to cards; visible "archived quotes" banner. Watchlist edits, converter
pair/amount and active section persist in `graph.appState`. Tokens only, `DetailScaffold`, zero corner radius,
no red/green outside tokens (add up/down tokens if needed).

- Screens: `ui/apps/mocks/MsnMoneyApp.kt` (replace `MsnMoneyMock` in `Mocks.kt`).
- Logic: `ui/apps/MoneyModel.kt` (`Quote`, `formatChange`, `searchTickers`, `convert`).
- Tests: `app/src/test/java/com/heretek/dorado_hd/MoneyModelTest.kt` — change formatting/sign, ticker search
  with 1-char minimum, 50-quote cap, conversion rounding.

```kotlin
@Composable fun MsnMoneyApp(graph: DoradoGraph)
fun formatChange(price: Double, prev: Double): Change   // % + direction
fun convert(amount: Double, rate: Double, decimals: Int): Either<Error, Double>
```

## 7. Citation log

All refs `MSNMoney.exe!` unless noted.
- `TwistMenuController` constructor, `!ButtonLocations`, `!logoBounds`, `!ActiveMenuChanged`, `!managerScreen`,
  `!currencyConversionScreen`, `!showInfoScreen`
- `PortfolioViewController.ConfigureQuoteList`, `!AddQuote`, `!RemoveQuote`, `!ToggleView`, `!UpdateUI`;
  `PortfolioViewType`
- `PortfolioQuoteView` constructor, `!UpdateStockView`; `QuoteEntryView` constructor, `!deletePressed`,
  `!addPressed`, `!Indent`
- `DetailedQuoteView` constructor, `!AddStaticLabel`, `!AddDynamicLabel`, `!ChartOrientationChanged`,
  `!newsButton_Clicked`, `!currButton_Clicked`, `!LargeIntToReadableString`
- `ChartView.ComputeChartIndex`, `!DrawOverlay`, `!DisplayChart`, `!TryUseLastImage`; `ChartRange`
- `CurrencyConverterViewController.Initialize`, `!createCurrencyMapping`, `!calculateCarryTrade`,
  `!DisplayConversion`; `CurrencyView.Initialize`, `!prevPressed`, `!nextPressed`, `!rotateCurrencyForward`
- `TradeEntryView` (`buyButtonClicked`, `sellButtonClicked`, `sharesAddedClicked`, `sharesPriceClicked`)
- `NewsClipView` constructor, `!newsPressed`; `Newspaper.Refresh`, `!UpdateNewspaper`;
  `DetailedNewsView.ConfigureDetailedLabels`, `!linkPressed`
- `BaseController.AddRefreshButton`, `!AddErrorText`, `!ReportError`, `!DimUI`, `!AddLastUpdated`,
  `!UpdateLastUpdated`
- `GlobalSettings`, `Portfolio`, `Constants` (MAXQUOTES 50, search minimum 1), `QuoteData.Parse`, `!GetChart`
- `FinanceApp.ApplicationFinishedLoading`, `!LoadBootScreenContent`
- Inventory: `_mine/_index/msnmoney.json`; content tree `Images/*`, `Text/{Fonts,Text}.xml`.
