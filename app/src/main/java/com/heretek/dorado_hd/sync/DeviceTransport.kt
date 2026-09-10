package com.heretek.dorado_hd.sync

/**
 * Transport boundary to a device's content store, mirroring the desktop
 * `IDeviceTransport` (MTPZ semantics). The simulated implementation powers the
 * full Device-view flow today; a real MTP or LAN transport implements the same
 * contract later with zero UI changes.
 */
interface DeviceTransport {
    val deviceSerialNumber: String
    val deviceName: String
    val totalCapacityBytes: Long
    val systemBytes: Long

    fun contents(): List<DeviceContentItem>
    fun tryGetItem(entityId: String): DeviceContentItem?

    fun copyToDevice(item: TransferItem)
    fun removeFromDevice(item: DeviceContentItem)

    val usedBytes: Long
    val freeBytes: Long
}
