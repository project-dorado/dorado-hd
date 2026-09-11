package com.heretek.dorado_hd.data.official

/**
 * The frozen official Zune HD marketplace catalog (62 packages — see
 * `docs/zcp-inventory.md` and `tools/zcp_inventory.py`). Each entry maps to
 * a real ZuneRedux-installable package; the `installedId` field, when
 * non-null, names the Dorado-HD mini-app that re-implements it canonically.
 */
data class OfficialApp(
    val title: String,
    val exe: String,
    val category: String,
    val description: String,
    val installedId: String? = null,
)

object OfficialCatalog {
    val UTILITIES = "utilities"
    val MUSIC = "music"
    val GAMES = "games"
    val SOCIAL = "social"
    val READING = "reading"
    val NETWORKING = "networking"

    /** Case-insensitive title/description search over the frozen catalog. */
    fun search(query: String): List<OfficialApp> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return all.filter {
            it.title.lowercase().contains(q) || it.description.lowercase().contains(q)
        }
    }

    val all: List<OfficialApp> = listOf(
        OfficialApp("3D Picture Puzzle", "PicturePuzzle3D.exe", GAMES, "Twist your mind in this exciting sliding picture puzzle game!", "3d-picture-puzzle"),
        OfficialApp("A Beanstalk Tale", "BeanstalkTale.exe", GAMES, "Jump up the mighty beanstalk.", "a-beanstalk-tale"),
        OfficialApp("Alarm Clock", "AlarmClock.exe", UTILITIES, "Wake to your own playlist or radio station.", "alarm"),
        OfficialApp("Animalgrams", "Anagrams.exe", GAMES, "Word game from jumbled letters.", "animalgrams"),
        OfficialApp("Audiosurf Tilt", "Audiosurf.exe", MUSIC, "Ride your music — a rollercoaster from any song."),
        OfficialApp("BBQ Battle", "BBQBattle.exe", GAMES, "Fast-paced tower defense.", "bbq-battle"),
        OfficialApp("Bees!!!", "Bees.exe", GAMES, "Arcade / strategy with simple one-button play.", "bees"),
        OfficialApp("Calculator", "Calculator.exe", UTILITIES, "Basic and scientific calculator.", "calculator"),
        OfficialApp("Calendar", "Calendar.exe", UTILITIES, "Calendar and appointments.", "calendar"),
        OfficialApp("Castles and Cannons", "CastlesAndCannons.exe", GAMES, "2010 Microsoft Corporation.", "castles-and-cannons"),
        OfficialApp("Checkers", "Checkers.exe", GAMES, "Classic Checkers in a park setting.", "checkers"),
        OfficialApp("Chess", "Chess.exe", GAMES, "Classic Chess against your Zune HD or a friend.", "chess"),
        OfficialApp("Chord Finder", "ChordFinder.exe", MUSIC, "Find the perfect guitar chord.", "chordfinder"),
        OfficialApp("Color Spill", "ColorSpill.exe", GAMES, "Colorful twist on a classic game.", "color-spill"),
        OfficialApp("Decoder Ring", "Decodering.exe", GAMES, "Sudoku meets crosswords.", "decoder-ring"),
        OfficialApp("Dr Optics Light Lab", "DrOptics.exe", GAMES, "Manipulate mirrors, lenses, and polygons.", "dr-optics-light-lab"),
        OfficialApp("Drum Machine", "DrumMachine.exe", MUSIC, "Create simple drum patterns.", "drummachine"),
        OfficialApp("Echoes", "Echoes.exe", GAMES, "Action puzzle game."),
        OfficialApp("Email", "ZuneHDEmail.exe", NETWORKING, "Hotmail, Gmail and Exchange.", "email"),
        OfficialApp("Facebook", "Facebook.exe", SOCIAL, "Connect and share.", "facebook"),
        OfficialApp("Fan Prediction", "FanPrediction.exe", GAMES, "Predict college and pro sports outcomes.", "fan-prediction"),
        OfficialApp("Finger Physics", "FingerPhysics.exe", GAMES, "10 stages, 90 levels of puzzle awesomeness.", "finger-physics"),
        OfficialApp("Fingerpaint", "Fingerpaint.exe", GAMES, "Doodle to your heart's content.", "fingerpaint"),
        OfficialApp("Goo Splat", "GooSplat.exe", GAMES, "2009 Microsoft Corporation.", "goo-splat"),
        OfficialApp("Hairball", "Hairball.exe", GAMES, "Survive the dirty drains of life.", "hairball"),
        OfficialApp("Hearts", "Hearts.exe", GAMES, "Classic card game.", "hearts"),
        OfficialApp("Hexic", "Hexic.exe", GAMES, "Cluster same-colored tiles to clear the board.", "hexic"),
        OfficialApp("Labyrinth", "Labyrinth.exe", GAMES, "Lead your marble through a colorful world."),
        OfficialApp("Level", "Level.exe", UTILITIES, "Spirit level and surface level in one.", "level"),
        OfficialApp("Lucky Lanes Bowling", "bowling.exe", GAMES, "Exhibition, blackjack and golf modes."),
        OfficialApp("MSN Money", "MSNMoney.exe", READING, "Comprehensive money and finance news.", "msnmoney"),
        OfficialApp("Metronome", "Metronome.exe", MUSIC, "Quick and easy while practicing.", "metronome"),
        OfficialApp("Music Quiz", "MusicQuiz.exe", MUSIC, "How well do you know your music?", "musicquiz"),
        OfficialApp("Notes", "Notepad.exe", UTILITIES, "Keep track of anything and everything.", "notes"),
        OfficialApp("PGR: Ferrari Edition", "PGRZune.exe", GAMES, "Streets of London, Tokyo and New York."),
        OfficialApp("Penalty! Flick Soccer", "Penalty.exe", GAMES, "Addictive flick-soccer shootout.", "penalty-flick-soccer"),
        OfficialApp("Piano", "Piano.exe", MUSIC, "Play your own tune or along with favorites.", "piano"),
        OfficialApp("Reversi", "Reversi.exe", GAMES, "Classic Reversi against the computer.", "reversi"),
        OfficialApp("Run and Jump", "RunAndJump.exe", GAMES, "Old-school challenging platformer.", "run-and-jump"),
        OfficialApp("Shell Game of the Future", "Shells.exe", GAMES, "Keep your eye on the robot.", "shell-game-of-the-future"),
        OfficialApp("Shuffle By Album", "ShuffleByAlbum.exe", MUSIC, "Randomly listen to your music, album by album.", "shufflebyalbum"),
        OfficialApp("Slider Puzzle", "PuzzleGame.exe", GAMES, "Sliding picture puzzle.", "slider-puzzle"),
        OfficialApp("Snowball", "Snowball.exe", GAMES, "Help the penguin collect snowflakes.", "snowball"),
        OfficialApp("Solitaire", "Solitaire.exe", GAMES, "The classic Klondike solitaire.", "solitaire"),
        OfficialApp("Space Battle 2", "Zauri.exe", GAMES, "Vertical scrolling shooter.", "space-battle-2"),
        OfficialApp("Spades", "Spades.exe", GAMES, "Fast-paced classic card game.", "spades"),
        OfficialApp("Splatter Bug", "SplatterBug.exe", GAMES, "Squish bugs, free critters.", "splatter-bug"),
        OfficialApp("Stopwatch", "StopWatch.exe", UTILITIES, "Stopwatch with laps.", "stopwatch"),
        OfficialApp("Sudoku", "Sudoku.exe", GAMES, "Solve Sudoku by filling all rows and columns.", "sudoku"),
        OfficialApp("SuperNova", "Supernova.exe", GAMES, "Chain supernovas for a high score.", "supernova"),
        OfficialApp("Texas Hold Em", "Holdem.exe", GAMES, "Last player holding chips wins.", "texasholdem"),
        OfficialApp("Tiki Totems", "TikiTotems.exe", GAMES, "Appease the Tiki Gods.", "tiki-totems"),
        OfficialApp("Tiles", "Tiles.exe", GAMES, "Classic tile-matching puzzle.", "tiles"),
        OfficialApp("Trash Throw", "TrashThrow.exe", GAMES, "The perfect time waster.", "trash-throw"),
        OfficialApp("Tug-O-War", "Tuginator.exe", GAMES, "Fun pick-up and play.", "tug-o-war"),
        OfficialApp("Twitter", "Twitter.exe", SOCIAL, "Real-time information network.", "twitter"),
        OfficialApp("Vans Sk8 Pool Service", "Vans.exe", GAMES, "Skate the rooftops and abandoned lots."),
        OfficialApp("Vine Climb", "VineClimb.exe", GAMES, "A hungry monkey climbs the vines.", "vine-climb"),
        OfficialApp("Weather", "Weather.exe", READING, "Forecasts and conditions around the world.", "weather"),
        OfficialApp("Messenger", "Microsoft.Live.Messenger.Client.exe", NETWORKING, "Stay in touch with Messenger.", "messenger"),
        OfficialApp("WordMonger", "WordMonger.exe", GAMES, "Word game that's fresh, original and fun.", "wordmonger"),
        OfficialApp("Zune Reader", "ZuneReaderHD.Core.exe", READING, "Read your favorite books on the go.", "zunereader"),
    )

    fun byInstalled(id: String): OfficialApp? = all.firstOrNull { it.installedId == id }
    fun categoryColor(c: String): String = c
}
