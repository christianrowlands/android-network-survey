package com.craxiom.networksurvey.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colors for the redesigned Wi-Fi list and Network Details screens.
 *
 * Scoped to Wi-Fi screens; cellular keeps [com.craxiom.networksurvey.util.ColorUtils]
 * with its own 5-bucket ramp so neither screen drifts visually.
 */
object WifiTokens {

    val SsidAccent = Color(0xFF7FCFE8)
    val SsidSoft = Color(0x267FCFE8)

    val PasspointBlue = Color(0xFF8FA0F0)

    val HiddenSsid = Color(0xFFEF8C6C)

    // Neutral text + surface tokens, hex from the handoff spec section 2. Scoped to Wi-Fi
    // screens so the rest of the app's MaterialTheme neutrals stay authoritative.
    val Ink = Color(0xFFE7EBF2)
    val InkDim = Color(0xFFB4BAC4)
    val InkMuted = Color(0xFF7D8591)
    val InkFaint = Color(0xFF5A6170)

    val Surface2 = Color(0xFF1A1F28)

    val Band24 = Color(0xFFD7B96E)
    val Band5 = Color(0xFF6FCFD7)
    val Band6 = Color(0xFFC99CE0)

    val SignalStrong = Color(0xFF7FD98A)
    val SignalGood = Color(0xFFAED97F)
    val SignalFair = Color(0xFFE3C46A)
    val SignalWeak = Color(0xFFE88A6C)

    val SecurityMint = Color(0xFF77D8B0)
    val SecurityEnterprise = Color(0xFF6FCFE0)
    val SecurityOpen = Color(0xFFE88A6C)
}
