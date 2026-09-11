# Shuffle By Album

- **Official package:** `ShuffleByAlbum.exe` (shuffle_by_album)
- **Corpus:** `Zune HD Apps (Decompiled)/shuffle_by_album` (external, untracked)
- **Wave:** W1 · **Category:** music
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** shufflebyalbum

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

`ShuffleByAlbum.exe` is a 16-type / 105-method ZuneAppLib application (4,296
lines, `ShuffleByAlbumHD` namespace). It plays the library album by album in a
randomized album order, preserving each album's track order. The queue can be
scoped to all music, an artist, a genre or a playlist; a "group by artist"
option keeps an artist's albums contiguous. A five-album art carousel, a full
queue song list and an idle screen saver are the visible surface.

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `ShuffleByAlbum.exe` | 16 | 105 | 4296 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `ZuneAppLib.dll` | 57 | 367 | 7755 |
| `ZuneCoreLib.dll` | 68 | 304 | 6297 |

Core types: `ShuffleByAlbumHD` (application and playback state machine),
`ShuffleViewController`/`ShuffleButton` (scope picker),
`ArtistViewController`/`GenreViewController` + `IndexButton` (scope targets),
`SongListController`/`SmartVerticalScrollview`/`SongButton` (queue view) and
`AlbumButton` (carousel art).

## 2. Screens & navigation

The app is one full-screen player page (`ShuffleByAlbum!ShuffleByAlbumHD.ApplicationFinishedLoading`)
with modal overlays. The player shows the current artist/album/song labels,
five album-art buttons (±2 around the current album), upcoming-song labels,
transport buttons (previous / play-pause / next), a song-list button, an info
button and a shuffle button. Horizontal drags on the art move between albums
(`OnTouchesBegan/Moved/Ended`, `UpdateAlbumArt`, `ShiftAlbumArt`); the
previous/next buttons call `OnPreviousClicked`/`OnNextClicked`.

- **Shuffle modal** (`ShuffleViewController`): picks the scope
  (`ShuffleOptions.All | Artist | Genre | Playlist`) and toggles
  `GroupByArtist` via `ShuffleButton.UpdateGroupByArtist`.
- **Artist/Genre modals** (`ArtistViewController.LoadButtons`,
  `GenreViewController.LoadButtons`): paged alphabetical lists of artists or
  genres with a letter `IndexButton` rail (`ScrollToLetter`, `GetFirstLetter`)
  and a loading state (`IsLoading`).
- **Song list modal** (`SongListController`): the queue flattened to
  `m_indices` of (album, song) pairs, rendered through 150 recycled
  `SongButton`s in a `SmartVerticalScrollview`; tapping jumps playback
  (`OnSongClicked` → `ChangeSong`), with "song N of M" and time-left readouts.
- **About** (`AboutViewController`) and a boot/splash screen
  (`DrawBootScreen`/`LoadBootScreenContent`).

## 3. Rules, scoring & progression

- Queue build (`GenerateAlbumQueue`): with `GroupByArtists` off, albums are
  shuffled with `OrderBy(Random.Next)`. With it on and no artist scope, the
  list is reshuffled and de-interleaved so consecutive entries are not the
  same artist. With an artist scope, artists are shuffled and their albums
  appended in shuffled order, skipping `Various Artists`, `Unknown Artist` and
  `Soundtrack`. When `retainCurrentSong` is set the playing album is swapped to
  index 0 and the playing track becomes `m_currentSongIndex`, so the reshuffle
  continues seamlessly.
- Progression: playback always uses the album's track list in order
  (`MediaPlayer.Play(album.Songs, songIndex)`). At the end of an album,
  `OnSongChanged`/`OnMediaStateChanged` call `PlayNextAlbum`, which advances
  (`m_movingForward`) or reverses, clamps a negative index to the first album,
  and re-shuffles when the queue is exhausted (`ReShuffle`). Moving backward
  starts an album at its last track (`m_playAlbumFromEnd`), forward from track
  0. A short transition state (`m_transitioning`) animates labels and art and
  ignores duplicate song-changed events so one album change fires once.
- Drag: album art drag accumulates into an album offset; releasing either
  snaps back or commits to ±1/±2 albums (`m_continueDragToAlbum`) without a
  page transition, which is the "flip through albums" gesture.
- Screen saver: after idle, `EnableScreenSaver` shows drifting album art with
  randomized stats (`SetRandomScreenSaverStats`); any touch disables it, and
  an album change disables it too (`DisableScreenSaver(AlbumChanged)`).
- Persistence is minimal: `GroupByArtists` is a `Singleton<Settings>` flag;
  playback state is handed to `MediaPlayer` and not restored after exit.

## 4. Controls

Touch-first: horizontal drag on the album art flips albums; tap the art for
the song list; transport buttons play/pause/skip; shuffle opens the scope
modal; info opens About. Artist/genre lists scroll with a letter rail; the
song list scrolls kinetically and hides after idle (`m_idleTimer`). No
accelerometer, no long-press, 272×480 portrait.

## 5. Content inventory (must be re-authored)

12 files, almost all chrome: PNGs `Glow`, `MissingArtwork`, `PlayLine`,
`SetLine` and a system-button sheet; XNBs `_BOOTSCREEN_` (89-byte stub), a
refresh spinner, small system buttons and a highlight square; fonts/strings
XMLs. Album art, names and titles come from the library at runtime.

Re-authoring: all art becomes tokens and text (the time-left label, song
counter, transport glyphs); `MissingArtwork` becomes a token-drawn placeholder
in `AlbumArt`; the refresh spinner becomes a Compose animation; no PNG or
string is copied. The queue, labels and counts are computed from
`graph.library`.

## 6. Implementation plan

- Engine: new `ui/apps/shuffle/ShuffleEngine.kt` — `object ShuffleEngine` +
  immutable `data class ShuffleState(scope, groupByArtist, queue,
  currentAlbum, currentSong, isPlaying)` and pure functions
  `buildQueue(albums, scope, groupByArtist, rng)`, `nextAlbum(forward,
  fromEnd)`, `atQueueEnd`, `indicesFor(queue)`. Replaces the ad-hoc
  `albums.random()` in the current `ShuffleByAlbumApp`.
- UI: `ui/apps/shuffle/ShuffleByAlbumApp.kt` under
  `DetailScaffold(title = "shuffle by album")`: artist/album/song header,
  five-art carousel with drag, transport row, next-five upcoming tracks and a
  queue sheet with "N of M" and time-left. Scope picker and artist/genre list
  are secondary states; tokens only, zero radius.
- Playback: drive `graph.controller.play(tracks, index)` and listen for track
  changes to advance albums, so lock-screen controls keep working.
- Fidelity gaps vs current code: no queue or auto-advance (it picks one random
  album and stops), no scope picker, no group-by-artist, no art carousel or
  drag, no queue song list, no time-left/song counter, no idle screen saver.
- Persistence: `groupByArtist` and last scope in
  `graph.appState.put("shufflebyalbum", …)`; the queue is rebuilt on launch.
- Tests: `app/src/test/java/com/heretek/dorado_hd/ShuffleEngineTest.kt` —
  every scoped album once, no adjacent same-artist entries with grouping,
  various-artists excluded from artist scopes, advance/wrap, back at the last
  track, retain-current-song at index 0, index mapping.
- Edge cases: empty library (`NoAlbums`), one-album queues, albums with no
  tracks, the current track disappearing, and a reshuffle while paused.

## 7. Citation log

`ShuffleByAlbum!ShuffleByAlbumHD.GenerateAlbumQueue`,
`ShuffleByAlbum!ShuffleByAlbumHD.ReShuffle`,
`ShuffleByAlbum!ShuffleByAlbumHD.PlayNextAlbum`,
`ShuffleByAlbum!ShuffleByAlbumHD.DoPlayNextAlbum`,
`ShuffleByAlbum!ShuffleByAlbumHD.ChangeSong`,
`ShuffleByAlbum!ShuffleByAlbumHD.UpdateUIBasedOnSong`,
`ShuffleByAlbum!ShuffleByAlbumHD.OnSongChanged`,
`ShuffleByAlbum!ShuffleByAlbumHD.OnMediaStateChanged`,
`ShuffleByAlbum!ShuffleByAlbumHD.GetAllAlbums`,
`ShuffleByAlbum!ShuffleByAlbumHD.OnShuffleClicked`,
`ShuffleByAlbum!ShuffleByAlbumHD.UpdateAlbumArt`,
`ShuffleByAlbum!ShuffleByAlbumHD.ShiftAlbumArt`,
`ShuffleByAlbum!ShuffleByAlbumHD.EnableScreenSaver`,
`ShuffleByAlbum!ShuffleByAlbumHD.DisableScreenSaver`,
`ShuffleByAlbum!ShuffleByAlbumHD.SetRandomScreenSaverStats`,
`ShuffleByAlbum!ShuffleByAlbumHD.AnimateText`,
`ShuffleByAlbum!ShuffleByAlbumHD.AnimateAlbumButtons`,
`ShuffleByAlbum!SongListController.OnSongClicked`,
`ShuffleByAlbum!SongListController.UpdateSongList`,
`ShuffleByAlbum!ArtistViewController.LoadButtons`,
`ShuffleByAlbum!ArtistViewController.ScrollToLetter`,
`ShuffleByAlbum!GenreViewController.LoadButtons`,
`ShuffleByAlbum!SmartVerticalScrollview.SetCurrentIndex`,
`ShuffleByAlbum!ShuffleButton.UpdateGroupByArtist`,
`ShuffleByAlbum!ShuffleByAlbumHD.ApplicationFinishedLoading`.
