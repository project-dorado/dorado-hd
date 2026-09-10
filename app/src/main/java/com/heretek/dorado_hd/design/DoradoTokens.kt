package com.heretek.dorado_hd.design

/**
 * Design tokens from docs/design-tokens.md. Screens must consume these;
 * raw literals in screen code violate the design-invariant audit.
 */
object DoradoTokens {
    // Canvas
    const val CANVAS_WIDTH = 480
    const val CANVAS_HEIGHT = 272

    // Spacing
    const val EDGE = 16
    const val CROSSBAR_LEAD = 24
    const val HEADER_HEIGHT = 48
    const val CROSSBAR_HEIGHT = 34
    const val ROW_HEIGHT = 40
    const val ALBUM_TILE = 92
    const val GRID_GUTTER = 8
    const val MINI_PLAYER_HEIGHT = 32

    // Type (design-unit dp)
    const val TYPE_MENU_ITEM = 34
    const val TYPE_HEADER_CROPPED = 40
    const val TYPE_HEADER_CROP_VISIBLE = 48
    const val TYPE_CROSSBAR = 18
    const val TYPE_NOW_TITLE = 26
    const val TYPE_NOW_META = 15
    const val TYPE_LIST = 14
    const val TYPE_LIST_SECONDARY = 11
    const val TYPE_CAPTION = 9
    const val TYPE_ALPHABET = 10

    // Mini-apps & games (docs/design-tokens.md, geometry)
    const val APP_TILE = 92
    const val PIANO_KEY_W = 24
    const val PIANO_KEY_H = 72
    const val PIANO_BLACK_W = 14
    const val PIANO_BLACK_H = 44
    const val DIAL_HEIGHT = 96
    const val DIAL_TICK_MINOR = 8
    const val DIAL_TICK_MAJOR = 16
    const val SUDOKU_CELL = 36
    const val BOARD_GAP = 1
    const val BOARD_BLOCK_GAP = 4
    const val CARD_W = 44
    const val CARD_H = 62
    const val HEX_RADIUS = 14

    // Letter-spacing tokens (sp). The Zune HD's "Zegoe" rendered with slight
    // negative tracking; the 480x272 device canvas compounds it, so we keep
    // these small. Apply via `letterSpacing = (-X).sp`.
    const val LETTER_SPACING_EDGE = -0.5
    const val LETTER_SPACING_HEADER = -1.0
    const val LETTER_SPACING_CROSSBAR = -0.3

    // Now Playing screensaver type scale (device-mode design units).
    const val TYPE_SAVER_TITLE = 32
    const val TYPE_SAVER_ARTIST = 20
    const val TYPE_SAVER_ALBUM = 16

    // Drag thresholds (px in device-mode design units).
    const val SKIP_DRAG_PX = 24

    // Now Playing idle behavior (canon §4).
    const val IDLE_SCREENSAVER_MS = 5_000L
}
