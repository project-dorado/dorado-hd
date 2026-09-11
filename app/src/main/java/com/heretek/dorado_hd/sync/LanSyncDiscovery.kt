package com.heretek.dorado_hd.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** A Dorado desktop discovered on the LAN via mDNS. */
data class DiscoveredDesktop(
    val serviceName: String,
    val host: String,
    val port: Int,
)

/**
 * Discovers Dorado desktops advertising `_dorado-sync._tcp` (M8.2b). The seam
 * lets the Device view and tests avoid the platform mDNS stack.
 */
interface LanSyncDiscovery {
    suspend fun discover(timeoutMs: Long = 4000): List<DiscoveredDesktop>
}

/**
 * `NsdManager`-backed discovery. Best-effort: unresolved services are skipped,
 * and a timeout returns whatever was found. Runs entirely off the UI thread.
 */
class NsdLanSyncDiscovery(private val context: Context) : LanSyncDiscovery {

    override suspend fun discover(timeoutMs: Long): List<DiscoveredDesktop> = suspendCancellableCoroutine { cont ->
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsd == null) {
            cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }

        val found = LinkedHashMap<String, DiscoveredDesktop>()

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val address = serviceInfo.host?.hostAddress ?: return
                found[serviceInfo.serviceName] = DiscoveredDesktop(
                    serviceName = serviceInfo.serviceName,
                    host = address,
                    port = serviceInfo.port,
                )
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                runCatching { nsd.resolveService(serviceInfo, resolveListener) }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                found.remove(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsd.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsd.stopServiceDiscovery(this) }
            }
        }

        runCatching { nsd.discoverServices(SyncProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener) }
            .onFailure { cont.resume(emptyList()) }

        CoroutineScope(Dispatchers.Default).launch {
            delay(timeoutMs)
            runCatching { nsd.stopServiceDiscovery(discoveryListener) }
            if (cont.isActive) {
                cont.resume(found.values.toList())
            }
        }
    }
}
