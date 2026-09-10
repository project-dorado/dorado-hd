package com.heretek.dorado_hd

import com.heretek.dorado_hd.data.model.Rating
import com.heretek.dorado_hd.sync.DeviceSnapshot
import com.heretek.dorado_hd.sync.SimulatedDeviceTransport
import com.heretek.dorado_hd.sync.SyncCategoryRule
import com.heretek.dorado_hd.sync.SyncCategoryType
import com.heretek.dorado_hd.sync.SyncEngine
import com.heretek.dorado_hd.sync.SyncGroup
import com.heretek.dorado_hd.sync.SyncInput
import com.heretek.dorado_hd.sync.SyncMode
import com.heretek.dorado_hd.sync.SyncProtocol
import com.heretek.dorado_hd.sync.SyncTrack
import com.heretek.dorado_hd.sync.TransferAction
import com.heretek.dorado_hd.sync.TransferItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTransportTest {

    private fun musicOnlyGroup(serial: String = "S") = SyncGroup(
        serial,
        categories = listOf(SyncCategoryRule(SyncCategoryType.MUSIC, SyncMode.AUTOMATIC)),
    )

    private fun snapshotOf(t: SimulatedDeviceTransport) =
        DeviceSnapshot(t.totalCapacityBytes, t.systemBytes, t.contents())

    @Test
    fun `simulator seeds only large devices, like the desktop`() {
        val big = SimulatedDeviceTransport("S", "Zune HD", 32L * 1024 * 1024 * 1024)
        assertEquals(5, big.contents().size)
        val tiny = SimulatedDeviceTransport("S", "Tiny", 11L * 1024 * 1024)
        assertTrue(tiny.contents().isEmpty())
    }

    @Test
    fun `used and free bytes include the system partition`() {
        val t = SimulatedDeviceTransport("S", "Zune HD", 32L * 1024 * 1024 * 1024)
        assertEquals(t.systemBytes + t.contents().sumOf { it.sizeBytes }, t.usedBytes)
        assertEquals(t.totalCapacityBytes - t.usedBytes, t.freeBytes)
    }

    @Test
    fun `copy and remove track free space`() {
        val t = SimulatedDeviceTransport("S", "Zune HD", 32L * 1024 * 1024 * 1024)
        val before = t.freeBytes
        val item = TransferItem(TransferAction.ADD, SyncCategoryType.MUSIC, "id1", "Song", "/s", 10L * 1024 * 1024)
        t.copyToDevice(item)
        assertEquals(before - 10L * 1024 * 1024, t.freeBytes)
        t.removeFromDevice(t.tryGetItem("id1")!!)
        assertEquals(before, t.freeBytes)
    }

    @Test
    fun `applyPlan removes stale content then adds the new track`() {
        val transport = SimulatedDeviceTransport("S", "Zune HD", 32L * 1024 * 1024 * 1024)
        val staleTitle = transport.contents().first { it.category == SyncCategoryType.MUSIC }.title

        val track = SyncTrack("new1", "New Song", "A", "Al", 1, "/n", Rating.HEART)
        val plan = SyncEngine.buildPlan(
            musicOnlyGroup(),
            SyncInput(tracks = listOf(track)),
            snapshotOf(transport),
        )
        assertTrue(plan.items.any { it.action == TransferAction.REMOVE })

        val progress = mutableListOf<Double>()
        SyncEngine.applyPlan(plan, transport) { progress += it }

        val music = transport.contents().filter { it.category == SyncCategoryType.MUSIC }
        assertFalse(music.any { it.title == staleTitle })
        assertTrue(music.any { it.title == "New Song" })
        assertTrue(progress.isNotEmpty())
        assertTrue(progress.all { it in 0.0..1.0 })
        assertEquals(1.0, progress.last(), 1e-9)
    }

    @Test
    fun `keep items are left untouched by applyPlan`() {
        val transport = SimulatedDeviceTransport("S", "Tiny", 11L * 1024 * 1024)
        transport.copyToDevice(TransferItem(TransferAction.ADD, SyncCategoryType.MUSIC, "k1", "Kept", "/k", 16000))
        val track = SyncTrack("k1", "Kept", "A", "Al", 1, "/k", Rating.HEART)

        val plan = SyncEngine.buildPlan(musicOnlyGroup(), SyncInput(tracks = listOf(track)), snapshotOf(transport))
        assertEquals(1, plan.keepCount)

        SyncEngine.applyPlan(plan, transport)
        assertNotNull(transport.tryGetItem("k1"))
    }

    @Test
    fun `pairing code is always six digits`() {
        repeat(100) {
            val code = SyncProtocol.newPairingCode()
            assertEquals(6, code.length)
            assertTrue(code.all { it.isDigit() })
        }
    }
}
