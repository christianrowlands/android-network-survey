package com.craxiom.networksurvey.scout.link

import com.craxiom.networksurvey.board.v1.Configure
import com.craxiom.networksurvey.board.v1.ControlRequest
import com.craxiom.networksurvey.board.v1.Role

/**
 * What a Scout should capture while something needs Wi-Fi from it (a survey or a live Wi-Fi
 * screen). The manager merges every holder's capture with [merge] and sends the result as one
 * `Configure`. The Scout's own BLE capture and GPS are never used on Android, so `ble` is always
 * off and the role is always automatic hopping. Everything else (channels, dwell, raw frame limits)
 * stays at the board's defaults (protocol.md section 6).
 */
data class ScoutCapture(
    val wifiAccessPoints: Boolean = true,
    val wifiClients: Boolean = false,
    val wifiProbes: Boolean = false,
    /** Raw 802.11 frames, which also needs the Raw subscription (about 25 KB/s of link budget). */
    val rawFrames: Boolean = false,
) {
    /** @return A capture with every type either side wants. */
    fun merge(other: ScoutCapture): ScoutCapture = ScoutCapture(
        wifiAccessPoints = wifiAccessPoints || other.wifiAccessPoints,
        wifiClients = wifiClients || other.wifiClients,
        wifiProbes = wifiProbes || other.wifiProbes,
        rawFrames = rawFrames || other.rawFrames,
    )

    /** @return The `Configure` request for this capture. */
    fun toConfigureRequest(): ControlRequest.Builder = ControlRequest.newBuilder().setConfigure(
        Configure.newBuilder()
            .setWifiAp(wifiAccessPoints)
            .setWifiClient(wifiClients)
            .setWifiProbes(wifiProbes)
            .setBle(false)
            .setRawFrames(rawFrames)
            .setRole(Role.ROLE_AUTO)
    )
}
