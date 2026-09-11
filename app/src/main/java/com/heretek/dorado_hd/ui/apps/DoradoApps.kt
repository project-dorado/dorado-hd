package com.heretek.dorado_hd.ui.apps

import androidx.compose.runtime.Composable

/**
 * A single mini-app: id, label, category, and a composable renderer that
 * reads the DoradoGraph from [LocalDoradoGraph] and calls `nav.pop()` itself
 * (e.g. via the cropped-header scaffold).
 */
data class DoradoMiniApp(
    val id: String,
    val title: String,
    val category: String,
    val render: @Composable () -> Unit,
)

/**
 * The registry of installed mini-apps (canon §8). Built once at first access;
 * `byId` is used by the marketplace apps pivot and the destination router.
 */
object DoradoApps {
    val all: List<DoradoMiniApp> by lazy { buildAppRegistry() }

    fun byId(id: String): DoradoMiniApp? = all.firstOrNull { it.id == id }
}

/** The master list of installed mini-apps (Phase 3/4 append here). */
fun buildAppRegistry(): List<DoradoMiniApp> = listOf(
    DoradoMiniApp("calculator", "calculator", "utilities", { CalculatorApp() }),
    DoradoMiniApp("notes", "notes", "utilities", { NotesApp() }),
    DoradoMiniApp("stopwatch", "stopwatch", "utilities", { StopwatchApp() }),
    DoradoMiniApp("metronome", "metronome", "music", { MetronomeApp() }),
    DoradoMiniApp("alarm", "alarm clock", "utilities", { AlarmClockApp() }),
    DoradoMiniApp("calendar", "calendar", "utilities", { CalendarApp() }),
    DoradoMiniApp("level", "level", "utilities", { LevelApp() }),
    DoradoMiniApp("piano", "piano", "music", { PianoApp() }),
    DoradoMiniApp("drummachine", "drum machine", "music", { DrumMachineApp() }),
    DoradoMiniApp("chordfinder", "chord finder", "music", { ChordFinderApp() }),
    DoradoMiniApp("musicquiz", "music quiz", "music", { MusicQuizApp() }),
    DoradoMiniApp("shufflebyalbum", "shuffle by album", "music", { ShuffleByAlbumApp() }),
    DoradoMiniApp("solitaire", "solitaire", "games", { com.heretek.dorado_hd.ui.apps.games.SolitaireApp() }),
    DoradoMiniApp("sudoku", "sudoku", "games", { com.heretek.dorado_hd.ui.apps.games.SudokuApp() }),
    DoradoMiniApp("hexic", "hexic", "games", { com.heretek.dorado_hd.ui.apps.games.HexicApp() }),
    DoradoMiniApp("reversi", "reversi", "games", { com.heretek.dorado_hd.ui.apps.games.ReversiApp() }),
    DoradoMiniApp("hearts", "hearts", "games", { com.heretek.dorado_hd.ui.apps.games.HeartsApp() }),
    DoradoMiniApp("spades", "spades", "games", { com.heretek.dorado_hd.ui.apps.games.SpadesApp() }),
    DoradoMiniApp("checkers", "checkers", "games", { com.heretek.dorado_hd.ui.apps.games.CheckersApp() }),
    DoradoMiniApp("chess", "chess", "games", { com.heretek.dorado_hd.ui.apps.games.ChessApp() }),
    DoradoMiniApp("texasholdem", "texas hold 'em", "games", { com.heretek.dorado_hd.ui.apps.games.TexasHoldemApp() }),
    DoradoMiniApp("weather", "weather", "reading", { com.heretek.dorado_hd.ui.apps.mocks.WeatherMock() }),
    DoradoMiniApp("twitter", "twitter", "social", { com.heretek.dorado_hd.ui.apps.mocks.TwitterMock() }),
    DoradoMiniApp("facebook", "facebook", "social", { com.heretek.dorado_hd.ui.apps.mocks.FacebookMock() }),
    DoradoMiniApp("email", "email", "networking", { com.heretek.dorado_hd.ui.apps.mocks.EmailMock() }),
    DoradoMiniApp("messenger", "messenger", "networking", { com.heretek.dorado_hd.ui.apps.mocks.MessengerMock() }),
    DoradoMiniApp("msnmoney", "msn money", "reading", { com.heretek.dorado_hd.ui.apps.mocks.MsnMoneyMock() }),
    DoradoMiniApp("zunereader", "zune reader", "reading", { com.heretek.dorado_hd.ui.apps.mocks.ZuneReaderMock() }),
    DoradoMiniApp("zunesocial", "social", "social", { com.heretek.dorado_hd.ui.apps.mocks.SocialMock() }),
    DoradoMiniApp("color-spill", "color spill", "games", { com.heretek.dorado_hd.ui.apps.games.ColorSpillApp() }),
    DoradoMiniApp("supernova", "supernova", "games", { com.heretek.dorado_hd.ui.apps.games.SupernovaApp() }),
    DoradoMiniApp("tiles", "tiles", "games", { com.heretek.dorado_hd.ui.apps.games.TilesApp() }),
    DoradoMiniApp("slider-puzzle", "slider puzzle", "games", { com.heretek.dorado_hd.ui.apps.games.SliderPuzzleApp() }),
    DoradoMiniApp("shell-game-of-the-future", "shell game", "games", { com.heretek.dorado_hd.ui.apps.games.ShellGameApp() }),
    DoradoMiniApp("trash-throw", "trash throw", "games", { com.heretek.dorado_hd.ui.apps.games.TrashThrowApp() }),
    DoradoMiniApp("tug-o-war", "tug-o-war", "games", { com.heretek.dorado_hd.ui.apps.games.TugOWarApp() }),
    DoradoMiniApp("snowball", "snowball", "games", { com.heretek.dorado_hd.ui.apps.games.SnowballApp() }),
    DoradoMiniApp("run-and-jump", "run and jump", "games", { com.heretek.dorado_hd.ui.apps.games.RunAndJumpApp() }),
    DoradoMiniApp("hairball", "hairball", "games", { com.heretek.dorado_hd.ui.apps.games.HairballApp() }),
    DoradoMiniApp("splatter-bug", "splatter bug", "games", { com.heretek.dorado_hd.ui.apps.games.SplatterBugApp() }),
    DoradoMiniApp("goo-splat", "goo splat", "games", { com.heretek.dorado_hd.ui.apps.games.GooSplatApp() }),
)
