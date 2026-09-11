package com.heretek.dorado_hd

/**
 * Robolectric application for the Compose UI parity suite. Media3 cannot bind
 * its session service under Robolectric, so the app-level playback connect is
 * disabled before `super.onCreate`; everything else (graph, Room, DataStore,
 * repositories) is the real wiring.
 */
class TestDoradoApp : DoradoApp() {
    override fun onCreate() {
        autoConnectPlayback = false
        super.onCreate()
    }
}
