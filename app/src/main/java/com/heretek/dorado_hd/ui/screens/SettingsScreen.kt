package com.heretek.dorado_hd.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.heretek.dorado_hd.BuildConfig
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.DoradoAccent
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import com.heretek.dorado_hd.ui.components.SectionLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(canvasWidth: androidx.compose.ui.unit.Dp) {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by graph.settingsFlow.collectAsState(initial = com.heretek.dorado_hd.data.repo.DoradoSettings())
    val colors = LocalDoradoColors.current
    val scanState by graph.library.scanning.collectAsState()
    val importState by graph.library.importing.collectAsState()
    val lastScanAt by graph.library.lastScanAt.collectAsState()
    val lastScanResult by graph.library.lastScanResult.collectAsState()
    val lastImportResult by graph.library.lastImportResult.collectAsState()
    val menus = com.heretek.dorado_hd.ui.components.LocalContextMenu.current
    var trackCount by remember { mutableStateOf<Int?>(null) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { trackCount = graph.library.trackCount() }

    // SAF tree picker → recursive import into the library.
    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                graph.settings.setImportedTreeUri(uri.toString())
                graph.library.importTree(uri)
            }
        }
    }

    DetailScaffold(title = "settings") {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 48.dp),
        ) {
            SectionLabel("accent")
            Row(
                Modifier.padding(horizontal = DoradoTokens.EDGE.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DoradoAccent.entries.forEach { accent ->
                    val selected = accent == settings.accent
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(accent.primary)
                            .border(
                                width = if (selected) 2.dp else 0.5.dp,
                                color = if (selected) colors.textPrimary else colors.border,
                            )
                            .clickable { scope.launch { graph.settings.setAccent(accent) } },
                    )
                }
            }

            SectionLabel("display")
            SettingsToggle(
                label = "device mode",
                subLabel = "authentic 480x272 canvas (best in landscape)",
                value = settings.deviceMode,
            ) { enabled ->
                scope.launch { graph.settings.setDeviceMode(enabled) }
            }
            SettingsToggle(
                label = "artist photos",
                subLabel = "now playing backdrop, via musicbrainz",
                value = settings.artistImagesEnabled,
            ) { enabled ->
                scope.launch { graph.settings.setArtistImages(enabled) }
            }
            SettingsRow(
                label = "artist photo source",
                subLabel = settings.artistImageTemplate.ifBlank { "not configured" },
                onClick = {
                    menus.showPrompt("artist photo source", "url template with {mbid}") { template ->
                        scope.launch { graph.settings.setArtistImageTemplate(template) }
                    }
                },
            )

            SectionLabel("equalizer")
            SettingsRow(
                label = "preset",
                subLabel = settings.eqPreset.lowercase(),
                onClick = {
                    val presets = com.heretek.dorado_hd.analysis.EqPreset.entries
                    val index = presets.indexOfFirst { it.name.equals(settings.eqPreset, ignoreCase = true) }
                    val next = presets[(index + 1).mod(presets.size)]
                    scope.launch { graph.settings.setEqPreset(next.name) }
                },
            )

            SectionLabel("collection")
            SettingsInfoRow(
                label = "library location",
                subLabel = "${graph.library.databasePath()}\n${trackCount ?: "…"} tracks from MediaStore.Audio (all indexed audio on the device)",
            )
            SettingsRow(
                label = if (importState) "importing…" else "import music",
                subLabel = if (settings.importedTreeUri.isBlank()) {
                    "pick a folder — audio files will be added to the library"
                } else {
                    "imported: ${settings.importedTreeUri}"
                },
                onClick = { treeLauncher.launch(null) },
            )
            SettingsToggle(
                label = "watch media store",
                subLabel = "auto-rescan when files are added or removed",
                value = settings.watchMediaStore,
            ) { enabled ->
                scope.launch { graph.settings.setWatchMediaStore(enabled) }
            }
            SettingsRow(
                label = if (scanState) "scanning…" else "refresh collection",
                subLabel = "rebuild the library from device media",
                onClick = { scope.launch { graph.library.refresh() } },
            )
            SettingsInfoRow(
                label = "scan status",
                subLabel = scanStatusText(lastScanAt, lastScanResult, lastImportResult),
            )

            SectionLabel("device")
            SettingsRow(
                label = "device link",
                subLabel = "pair with dorado desktop · preview what will sync",
                onClick = { graph.nav.push(com.heretek.dorado_hd.ui.nav.DoradoDestination.Device) },
            )
            SettingsRow(
                label = "analyze library",
                subLabel = "compute on-device audio features for similarity and mixes",
                onClick = { scope.launch { graph.analysis.analyzeAll(graph.library.tracks().first()) } },
            )

            SectionLabel("scrobbling")
            SettingsToggle(
                label = "last.fm scrobbling",
                subLabel = "queue plays offline, send when configured",
                value = settings.scrobbleEnabled,
            ) { enabled ->
                scope.launch { graph.settings.setScrobbleEnabled(enabled) }
            }
            SettingsRow(
                label = "last.fm api key",
                subLabel = settings.lastFmApiKey.ifBlank { "not configured" },
                onClick = {
                    menus.showPrompt("last.fm api key", "api key") { value ->
                        scope.launch { graph.settings.setLastFmApiKey(value) }
                    }
                },
            )
            SettingsRow(
                label = "last.fm api secret",
                subLabel = if (settings.lastFmApiSecret.isBlank()) "not configured" else "configured",
                onClick = {
                    menus.showPrompt("last.fm api secret", "shared secret") { value ->
                        scope.launch { graph.settings.setLastFmApiSecret(value) }
                    }
                },
            )
            SettingsRow(
                label = "last.fm session key",
                subLabel = if (settings.lastFmSessionKey.isBlank()) "not configured" else "configured",
                onClick = {
                    menus.showPrompt("last.fm session key", "session key") { value ->
                        scope.launch { graph.settings.setLastFmSessionKey(value) }
                    }
                },
            )
            SettingsRow(
                label = "flush scrobbles",
                subLabel = "send queued plays now",
                onClick = { scope.launch { graph.scrobble.flush() } },
            )

            SectionLabel("dorado cloud")
            SettingsToggle(
                label = "cloud services",
                subLabel = "catalog, artwork, podcasts, updates and the live zune card",
                value = settings.cloudEnabled,
            ) { enabled ->
                scope.launch { graph.settings.setCloudEnabled(enabled) }
            }
            SettingsRow(
                label = "cloud url",
                subLabel = settings.cloudBaseUrl.ifBlank { "not configured" },
                onClick = {
                    menus.showPrompt("dorado cloud url", "https://cloud.example.org") { value ->
                        scope.launch { graph.settings.setCloudBaseUrl(value) }
                    }
                },
            )
            if (settings.cloudAccessToken.isBlank()) {
                SettingsRow(
                    label = "sign in",
                    subLabel = "open the browser to authorize this device (oauth + pkce)",
                    onClick = { scope.launch { graph.cloudSignIn.signIn() } },
                )
            } else {
                SettingsRow(
                    label = "sign out",
                    subLabel = "signed in to the dorado cloud",
                    onClick = { scope.launch { graph.cloudSignIn.signOut() } },
                )
            }

            SettingsRow(
                label = "check for updates",
                subLabel = updateStatus ?: "verify the latest signed release",
                onClick = {
                    scope.launch {
                        val info = graph.cloudUpdates.check("dorado-hd")
                        updateStatus = when {
                            !graph.cloudUpdates.isEnabled() -> "cloud disabled"
                            info == null -> "up to date"
                            !info.verified -> "update ${info.version} — signature not verified"
                            else -> "update ${info.version} available"
                        }
                    }
                },
            )

            SectionLabel("about")
            SettingsRow(
                label = "dorado hd ${BuildConfig.VERSION_NAME}",
                subLabel = "sister to dorado — the zune hd, reborn on android",
                onClick = {},
            )
            Spacer(Modifier.height(12.dp))
            EdgeCropText(
                text = "selawik stands in for zegoe (sil ofl). zune, zegoe and the zune hd are trademarks of microsoft; this is an independent homage.",
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                alpha = 0.08f,
                modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp),
            )
        }
    }
}

private fun scanStatusText(
    lastScanAt: Long,
    lastScanResult: com.heretek.dorado_hd.data.scan.MediaLibraryScanner.ScanResult?,
    lastImportResult: com.heretek.dorado_hd.data.repo.LibraryRepository.ImportResult?,
): String {
    if (lastScanAt == 0L && lastImportResult == null) return "no scan yet"
    val parts = mutableListOf<String>()
    if (lastScanAt != 0L) parts += "media store: ${formatTimestamp(lastScanAt)}"
    if (lastScanResult != null) parts += "scanned ${lastScanResult.scanned} · removed ${lastScanResult.removed}"
    if (lastImportResult != null) {
        parts += if (lastImportResult.ok) "imported ${lastImportResult.scanned}"
        else "import error: ${lastImportResult.error}"
    }
    return parts.joinToString("\n")
}

private fun formatTimestamp(ms: Long): String {
    if (ms == 0L) return "never"
    val df = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return df.format(java.util.Date(ms))
}

@Composable
private fun SettingsRow(
    label: String,
    subLabel: String,
    onClick: () -> Unit,
) {
    val colors = LocalDoradoColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
    ) {
        EdgeCropText(text = label, fontSize = DoradoTokens.TYPE_LIST.dp)
        EdgeCropText(
            text = subLabel,
            fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
            color = colors.textSecondary,
        )
    }
}

/** Non-tappable read-only row (e.g. "library location" — canonical info, not an action). */
@Composable
private fun SettingsInfoRow(label: String, subLabel: String) {
    val colors = LocalDoradoColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
    ) {
        EdgeCropText(text = label, fontSize = DoradoTokens.TYPE_LIST.dp)
        EdgeCropText(
            text = subLabel,
            fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    subLabel: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = LocalDoradoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!value) }
            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            EdgeCropText(text = label, fontSize = DoradoTokens.TYPE_LIST.dp)
            EdgeCropText(
                text = subLabel,
                fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
                color = colors.textSecondary,
            )
        }
        Box(
            Modifier
                .background(if (value) colors.accent else colors.tile)
                .border(0.5.dp, colors.border)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            EdgeCropText(
                text = if (value) "on" else "off",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = if (value) colors.textPrimary else colors.textSecondary,
            )
        }
    }
}
