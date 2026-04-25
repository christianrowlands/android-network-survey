package com.craxiom.networksurvey.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colors for the redesigned Wi-Fi list and Network Details screens.
 *
 * Scoped to Wi-Fi screens; cellular keeps [com.craxiom.networksurvey.util.ColorUtils]
 * with its own 5-bucket ramp so neither screen drifts visually.
 */
object WifiTokens {

    val SsidAccent = Color(0xFF03A9F4)
    val SsidSoft = Color(0x2603A9F4)

    val PasspointBlue = Color(0xFF8FA0F0)

    val HiddenSsid = Color(0xFFEF8C6C)

    // Neutral text + surface tokens, hex from the handoff spec section 2. Scoped to Wi-Fi
    // screens so the rest of the app's MaterialTheme neutrals stay authoritative.
    val Ink = Color(0xFFE7EBF2)
    val InkDim = Color(0xFFB4BAC4)
    val InkMuted = Color(0xFFA8AFB8)
    val InkFaint = Color(0xFF888E98)

    val Band24 = Color(0xFFD7B96E)
    val Band5 = Color(0xFF6FCFD7)
    val Band6 = Color(0xFFC99CE0)

    val SignalStrong = Color(0xFF388E3C)
    val SignalGood = Color(0xFFFBC02D)
    val SignalFair = Color(0xFFF57C00)
    val SignalWeak = Color(0xFFD32F2F)
    val SignalVeryWeak = Color(0xFFD50000)

    val SecurityMint = Color(0xFF77D8B0)
    val SecurityEnterprise = Color(0xFF6FCFE0)
    val SecurityOpen = Color(0xFFE88A6C)
}
