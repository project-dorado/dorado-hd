# AGENTS.md — Dorado-HD repository rules

Dorado-HD is the Zune HD on-device UI, reborn as an Android music player.
Sister project of Dorado (the Zune desktop re-implementation).

## The canon is law

`docs/zune-hd-ui-canon.md` defines every screen and gesture; `docs/design-tokens.md`
defines every color, size and duration. If a change contradicts the canon, change
the canon first (with a source citation), then the code.

## Design invariants (audited by tests — `DesignInvariantTest`)

1. **Zero corner radius.** Never write `RoundedCornerShape` or set any nonzero
   corner radius on UI. Squares only, like the device.
2. **No Material chrome leakage.** Screens compose our tokens
   (`LocalDoradoColors`, `DoradoTokens`, `Selawik`). Material primitives (Icon,
   IconButton, TextField) are permitted as behaviorless shells — never for
   their styling.
3. **Typography is navigation.** New navigation is text-first (the Zune HD had
   no icon nav). Icons exist only for transport/rating glyphs.
4. **Hearts, not stars.** Rating is the tri-state heart: heart / broken / none.
5. **Back = the cropped header.** Detail screens use `CroppedHeader` /
   `DetailScaffold`; Now Playing uses the explicit back arrow. Do not add
   chrome back buttons elsewhere.
6. **Motion decelerates.** Use `DoradoMotion` easings/durations. No springs in
   navigation.

## Toolchain notes (AGP 9)

- `com.android.application` provides **built-in Kotlin**; never apply
  `org.jetbrains.kotlin.android`.
- KGP is force-aligned to 2.4.20 through the root `buildscript` block because
  2026 androidx libraries ship stdlib 2.4 metadata. Keep
  `libs.versions.kotlinComposePlugin` equal to that KGP version.
- Kotlin jvmTarget follows `compileOptions` (17).

## Code layout

- `design/` — tokens, theme, motion, reusable components. Screens may not
  hardcode colors/sizes; consume tokens.
- `data/` — Room database, MediaStore scanner, repositories.
- `media/` — Media3 session service + app-side `PlaybackController`.
- `ui/` — navigation, screens, shared widgets.
- `net/` — optional network providers (artist images via `{mbid}` templates).

## Licensing

MIT for code. MedTune attribution lives in NOTICE.md. MusicIn2001 is a
Research-Only-licensed project — **never copy code from it**; it is a
behavioral spec only. Never bundle Microsoft fonts or firmware.
