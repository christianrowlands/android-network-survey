package com.craxiom.networksurvey.ui.wifi.model

import androidx.compose.runtime.Immutable
import com.craxiom.networksurvey.model.WifiRecordWrapper
import com.craxiom.networksurvey.util.WifiBand
import com.craxiom.networksurvey.util.frequencyMhzToWifiBand

/**
 * One displayable row's pre-resolved state. The ViewModel builds these from
 * [WifiRecordWrapper]s once per scan so composables don't repeat protobuf introspection.
 *
 * Marked [Immutable] and holds only primitive / enum / `String` fields so Compose can skip
 * row recomposition when an individual AP's scan result hasn't changed. The underlying
 * [WifiRecordWrapper] lives on the ViewModel, looked up via [WifiListViewModel.wrapperFor]
 * when navigating to Details keeping the protobuf out of Compose's stability graph.
 */
@Immutable
data class WifiAccessPointDisplay(
    val bssid: String,
    val ssid: String,
    val ssidIsHidden: Boolean,
    val rssi: Int?,
    val frequencyMhz: Int?,
    val band: WifiBand?,
    val channel: Int?,
    val bandwidthLabel: String,
    val standardLabel: String,
    val encryptionLabel: String,
    val passpoint: Boolean,
    val isExcluded: Boolean,
) {
    companion object {
        fun fromWrapper(
            wrapper: WifiRecordWrapper,
            bandwidthLabel: String,
            standardLabel: String,
            encryptionLabel: String,
        ): WifiAccessPointDisplay {
            val data = wrapper.wifiBeaconRecord.data
            val ssid = data.ssid ?: ""
            val rssi = if (data.hasSignalStrength()) data.signalStrength.value.toInt() else null
            val frequency = if (data.hasFrequencyMhz()) data.frequencyMhz.value else null
            val channel = if (data.hasChannel()) data.channel.value else null
            val passpoint = data.hasPasspoint() && data.passpoint.value
            return WifiAccessPointDisplay(
                bssid = data.bssid ?: "",
                ssid = ssid,
                ssidIsHidden = ssid.isEmpty(),
                rssi = rssi,
                frequencyMhz = frequency,
                band = frequencyMhzToWifiBand(frequency),
                channel = channel,
                bandwidthLabel = bandwidthLabel,
                standardLabel = standardLabel,
                encryptionLabel = encryptionLabel,
                passpoint = passpoint,
                isExcluded = wrapper.isExcluded,
            )
        }
    }
}

/**
 * Items the LazyColumn renders. Sealed so the renderer can pattern-match on the shape.
 * All variants are [Immutable]. Compose uses data-class equality for skip decisions.
 */
@Immutable
sealed class WifiDisplayItem {

    abstract val key: String

    @Immutable
    data class Flat(val ap: WifiAccessPointDisplay) : WifiDisplayItem() {
        override val key: String get() = "flat:${ap.bssid}"
    }

    @Immutable
    data class GroupParent(
        val groupKey: String,
        val displaySsid: String,
        val isHidden: Boolean,
        val aps: List<WifiAccessPointDisplay>,
        val bestRssi: Int?,
        val bands: List<WifiBand>,
        val encryptionLabel: String,
        val anyPasspoint: Boolean,
        val expanded: Boolean,
        val excludedCount: Int = 0,
    ) : WifiDisplayItem() {
        override val key: String get() = "group:$groupKey"

        val apCount: Int get() = aps.size

        /** True when every AP in this group is excluded from survey data output. */
        val allExcluded: Boolean get() = excludedCount > 0 && excludedCount == aps.size
    }

    @Immutable
    data class GroupChild(
        val groupKey: String,
        val ap: WifiAccessPointDisplay,
    ) : WifiDisplayItem() {
        override val key: String get() = "child:$groupKey:${ap.bssid}"
    }
}

enum class WifiListMode {
    FLAT,
    GROUPED,
}
