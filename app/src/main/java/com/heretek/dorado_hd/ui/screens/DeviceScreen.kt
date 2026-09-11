package com.heretek.dorado_hd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heretek.dorado_hd.data.repo.DoradoSettings
import com.heretek.dorado_hd.data.repo.toSyncRuleSettings
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.sync.DeviceContentItem
import com.heretek.dorado_hd.sync.DeviceTransport
import com.heretek.dorado_hd.sync.DiscoveredDesktop
import com.heretek.dorado_hd.sync.SyncEngine
import com.heretek.dorado_hd.sync.SyncPlan
import com.heretek.dorado_hd.sync.SyncProtocol
import com.heretek.dorado_hd.sync.SyncRulePresets
import com.heretek.dorado_hd.sync.TransferAction
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import com.heretek.dorado_hd.ui.components.SectionLabel
import kotlinx.coroutines.launch

/**
 * Device Link (M8.3): the phone library is the sync source; the linked device
 * is the target. Shows the pairing state, the storage gauge, the four Zune
 * sync rules, a dry-run "what will sync" manifest and the reverse copy-back
 * queue. Targets a simulated transport until the LAN transport lands (M8.2b).
 */
@Composable
fun DeviceScreen(canvasWidth: androidx.compose.ui.unit.Dp) {
    val graph = LocalDoradoGraph.current
    val scope = rememberCoroutineScope()
    val settings by graph.settingsFlow.collectAsState(initial = DoradoSettings())
    val pending by graph.deviceLink.pendingImports.collectAsState()

    val transport = graph.deviceLink.transport
    val pairingCode = remember { SyncProtocol.newPairingCode() }
    var guest by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<SyncPlan?>(null) }
    var contents by remember { mutableStateOf(transport.contents()) }
    var busy by remember { mutableStateOf(false) }
    var discoverStatus by remember { mutableStateOf<String?>(null) }
    val linkedServer by graph.deviceLink.linkedServer.collectAsState()
    val menus = com.heretek.dorado_hd.ui.components.LocalContextMenu.current

    suspend fun recompute() {
        busy = true
        try {
            val input = graph.deviceLink.buildInput(graph.library, graph.quickplay, graph.podcasts)
            val group = SyncEngine.buildDefaultGroup(
                transport.deviceSerialNumber,
                settings.toSyncRuleSettings(),
                guest,
            )
            plan = SyncEngine.buildPlan(group, input, graph.deviceLink.snapshot())
            contents = transport.contents()
        } finally {
            busy = false
        }
    }

    LaunchedEffect(settings.musicSyncRule, settings.podcastSyncRule, settings.videoSyncRule, guest) {
        recompute()
    }

    DetailScaffold(title = "device link") {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 48.dp),
        ) {
            SectionLabel("status")
            EdgeCropText(
                text = "not paired — start a sync from the dorado desktop, then enter this code",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp),
            )
            EdgeCropText(
                text = "pairing code  $pairingCode",
                fontSize = DoradoTokens.TYPE_NOW_TITLE.dp,
                color = LocalDoradoColors.current.accent,
                modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
            )
            EdgeCropText(
                text = "${transport.deviceName} · ${formatBytes(transport.totalCapacityBytes)}",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = LocalDoradoColors.current.textSecondary,
                modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp),
            )

            SectionLabel("network")
            EdgeCropText(
                text = linkedServer?.let { "paired with $it" } ?: discoverStatus ?: "not paired over wi-fi",
                fontSize = DoradoTokens.TYPE_LIST.dp,
                color = if (linkedServer != null) {
                    LocalDoradoColors.current.accent
                } else {
                    LocalDoradoColors.current.textSecondary
                },
                modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(DoradoTokens.ROW_HEIGHT.dp)
                    .clickable {
                        scope.launch {
                            discoverStatus = "searching…"
                            val desktops = graph.deviceLink.discoverDesktops()
                            val first = desktops.firstOrNull()
                            if (first == null) {
                                discoverStatus = "no desktops found on this network"
                            } else {
                                discoverStatus = "found ${first.serviceName}"
                                menus.showPrompt("pairing code", "code shown on ${first.serviceName}") { code ->
                                    scope.launch {
                                        when (val result = graph.deviceLink.pair(first, code)) {
                                            is com.heretek.dorado_hd.sync.LanSyncResult.Paired ->
                                                discoverStatus = "paired with ${result.serverName}"
                                            is com.heretek.dorado_hd.sync.LanSyncResult.Rejected ->
                                                discoverStatus = "pairing rejected: ${result.reason}"
                                            is com.heretek.dorado_hd.sync.LanSyncResult.Failed ->
                                                discoverStatus = "pairing failed: ${result.message}"
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .padding(horizontal = DoradoTokens.EDGE.dp),
            ) {
                EdgeCropText(
                    text = "find dorado on your network",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = LocalDoradoColors.current.accent,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(DoradoTokens.ROW_HEIGHT.dp)
                    .clickable {
                        menus.showPrompt("desktop address", "192.168.1.10:8787") { value ->
                            val host = value.substringBeforeLast(':', value)
                            val port = value.substringAfterLast(':', "8787").toIntOrNull() ?: 8787
                            menus.showPrompt("pairing code", "code shown on the desktop") { code ->
                                scope.launch {
                                    val desktop = DiscoveredDesktop("manual", host, port)
                                    when (val result = graph.deviceLink.pair(desktop, code)) {
                                        is com.heretek.dorado_hd.sync.LanSyncResult.Paired ->
                                            discoverStatus = "paired with ${result.serverName}"
                                        is com.heretek.dorado_hd.sync.LanSyncResult.Rejected ->
                                            discoverStatus = "pairing rejected: ${result.reason}"
                                        is com.heretek.dorado_hd.sync.LanSyncResult.Failed ->
                                            discoverStatus = "pairing failed: ${result.message}"
                                    }
                                }
                            }
                        }
                    }
                    .padding(horizontal = DoradoTokens.EDGE.dp),
            ) {
                EdgeCropText(
                    text = "connect by address",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = LocalDoradoColors.current.accent,
                )
            }

            SectionLabel("storage")
            GasGauge(transport)
            EdgeCropText(
                text = "${formatBytes(transport.freeBytes)} free · " +
                    "${formatBytes(transport.usedBytes - transport.systemBytes)} used · " +
                    "${formatBytes(transport.systemBytes)} reserved",
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = LocalDoradoColors.current.textSecondary,
                modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
            )

            SectionLabel("sync rules")
            RuleRow("music", settings.musicSyncRule) {
                scope.launch {
                    graph.settings.setMusicSyncRule(SyncRulePresets.next(SyncRulePresets.MUSIC, settings.musicSyncRule))
                }
            }
            RuleRow("podcasts", settings.podcastSyncRule) {
                scope.launch {
                    graph.settings.setPodcastSyncRule(SyncRulePresets.next(SyncRulePresets.PODCASTS, settings.podcastSyncRule))
                }
            }
            RuleRow("videos & pictures", settings.videoSyncRule) {
                scope.launch {
                    graph.settings.setMediaSyncRule(SyncRulePresets.next(SyncRulePresets.MEDIA, settings.videoSyncRule))
                }
            }
            RuleRow("session", if (guest) "guest (add-only, never removes)" else "normal") {
                guest = !guest
            }

            SectionLabel("what will sync")
            val current = plan
            when {
                busy -> LoadingLine()
                current == null -> LoadingLine()
                current.items.isEmpty() -> EdgeCropText(
                    text = "up to date",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    alpha = 0.5f,
                    modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                )
                else -> {
                    EdgeCropText(
                        text = "${current.addCount} to add · ${current.removeCount} to remove · ${current.keepCount} kept",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = LocalDoradoColors.current.textSecondary,
                        modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp),
                    )
                    EdgeCropText(
                        text = "${formatBytes(current.totalAddBytes)} to copy",
                        fontSize = DoradoTokens.TYPE_CAPTION.dp,
                        color = LocalDoradoColors.current.textSecondary,
                        modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 4.dp),
                    )
                    ManifestList(current)
                    EdgeCropText(
                        text = "apply to device",
                        fontSize = DoradoTokens.TYPE_NOW_META.dp,
                        color = LocalDoradoColors.current.accent,
                        modifier = Modifier
                            .clickable(enabled = !busy) {
                                val p = plan ?: return@clickable
                                scope.launch {
                                    busy = true
                                    try {
                                        SyncEngine.applyPlan(p, transport)
                                        recompute()
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                    )
                }
            }

            SectionLabel("on device")
            if (contents.isEmpty()) {
                EdgeCropText(
                    text = "nothing on device",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    alpha = 0.4f,
                    modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp),
                )
            } else {
                contents.forEach { item -> DeviceItemRow(item) { graph.deviceLink.copyBack(item) } }
            }

            if (pending.isNotEmpty()) {
                SectionLabel("pending imports (${pending.size})")
                pending.forEach { p ->
                    EdgeCropText(
                        text = "${p.title} · ${formatBytes(p.sizeBytes)}",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 2.dp),
                    )
                }
                EdgeCropText(
                    text = "clear queue",
                    fontSize = DoradoTokens.TYPE_NOW_META.dp,
                    color = LocalDoradoColors.current.accent,
                    modifier = Modifier
                        .clickable { graph.deviceLink.clearPending() }
                        .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun LoadingLine() {
    EdgeCropText(
        text = "building…",
        fontSize = DoradoTokens.TYPE_LIST.dp,
        alpha = 0.4f,
        modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
    )
}

@Composable
private fun RuleRow(label: String, value: String, onClick: () -> Unit) {
    val colors = LocalDoradoColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 6.dp),
    ) {
        EdgeCropText(text = label, fontSize = DoradoTokens.TYPE_LIST.dp)
        EdgeCropText(
            text = value,
            fontSize = DoradoTokens.TYPE_LIST_SECONDARY.dp,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun GasGauge(transport: DeviceTransport) {
    val colors = LocalDoradoColors.current
    val total = transport.totalCapacityBytes.coerceAtLeast(1L).toFloat()
    val systemFrac = (transport.systemBytes / total).coerceIn(0.001f, 1f)
    val usedFrac = ((transport.usedBytes - transport.systemBytes) / total).coerceIn(0.001f, 1f)
    val freeFrac = (1f - systemFrac - usedFrac).coerceAtLeast(0.001f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .padding(horizontal = DoradoTokens.EDGE.dp)
            .background(colors.tile),
    ) {
        Box(Modifier.fillMaxHeight().weight(systemFrac).background(colors.border))
        Box(Modifier.fillMaxHeight().weight(usedFrac).background(colors.accent))
        Box(Modifier.fillMaxHeight().weight(freeFrac))
    }
}

@Composable
private fun ManifestList(plan: SyncPlan) {
    val colors = LocalDoradoColors.current
    plan.items.take(200).forEach { item ->
        val marker = when (item.action) {
            TransferAction.ADD -> "+"
            TransferAction.REMOVE -> "−"
            TransferAction.KEEP -> "="
        }
        EdgeCropText(
            text = "$marker ${item.title}",
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = when (item.action) {
                TransferAction.ADD -> colors.textPrimary
                TransferAction.REMOVE -> colors.textSecondary
                TransferAction.KEEP -> colors.textInactive
            },
            modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun DeviceItemRow(item: DeviceContentItem, onCopyBack: () -> Unit) {
    val colors = LocalDoradoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DoradoTokens.EDGE.dp, vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            EdgeCropText(text = item.title, fontSize = DoradoTokens.TYPE_LIST.dp)
            EdgeCropText(
                text = "${item.category} · ${formatBytes(item.sizeBytes)}",
                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                color = colors.textSecondary,
            )
        }
        EdgeCropText(
            text = "copy back",
            fontSize = DoradoTokens.TYPE_LIST.dp,
            color = colors.accent,
            modifier = Modifier.clickable(onClick = onCopyBack).padding(vertical = 4.dp),
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}
