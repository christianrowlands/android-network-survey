package com.craxiom.networksurvey.ui.grpc

import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.fragments.model.GrpcConnectionSettings
import com.craxiom.networksurvey.services.GrpcConnectionService
import com.craxiom.networksurvey.survey.status.DestinationStatus
import com.craxiom.networksurvey.survey.status.StatusWord
import java.net.URI
import java.net.URISyntaxException

/**
 * What the gRPC page shows: the fields as typed, their errors, and how the saved server is doing.
 * The port is text so the field can be empty while typing.
 *
 * @property configured     True when a server is saved (the gRPC destination is set up).
 * @property inSetup        True when the survey setup sends to gRPC.
 * @property recording      True while a survey is recording.
 * @property errorEpoch     Goes up on every save that fails validation, so the page can move focus
 *                          to the first field with an error.
 */
data class GrpcServerForm(
    val loading: Boolean = true,
    val host: String = "",
    val port: String = NetworkSurveyConstants.DEFAULT_GRPC_PORT.toString(),
    val deviceName: String = "",
    val hostError: Boolean = false,
    val portError: Boolean = false,
    val errorEpoch: Int = 0,
    val configured: Boolean = false,
    val inSetup: Boolean = false,
    val recording: Boolean = false,
    val organizationOff: Boolean = false,
    val status: GrpcStatus = GrpcStatus.NotSetUp,
) {
    /** True while a survey streams (or is connecting) to the saved server. */
    val streaming: Boolean get() = status is GrpcStatus.Live || status is GrpcStatus.Unreachable && status.retrying

    /** True when Save will also turn gRPC on for the next survey ("Save and turn on"). */
    val savesAndTurnsOn: Boolean get() = !configured && !inSetup && !recording && !organizationOff

    /**
     * Checks the fields the way the gRPC connection needs them: a host that forms a valid address
     * with the port, and a port from 1 to 65535. The device name is optional.
     */
    fun validate(): GrpcFormValidation {
        val host = normalizedHost(host)
        val port = port.trim().toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT }
        val hostValid = host.isNotEmpty() && isValidHost(host, port ?: NetworkSurveyConstants.DEFAULT_GRPC_PORT)
        val settings = if (hostValid && port != null) GrpcConnectionSettings(host, port, deviceName.trim()) else null
        return GrpcFormValidation(settings, hostError = !hostValid, portError = port == null)
    }

    companion object {
        const val MIN_PORT = 1
        const val MAX_PORT = 65535

        /** Trims the host and drops the brackets around an IPv6 literal, which the channel adds itself. */
        fun normalizedHost(host: String): String {
            val trimmed = host.trim()
            return if (trimmed.startsWith('[') && trimmed.endsWith(']')) trimmed.substring(1, trimmed.length - 1) else trimmed
        }

        private fun isValidHost(host: String, port: Int): Boolean {
            if (host.any(Char::isWhitespace)) return false
            return try {
                URI(null, null, host, port, null, null, null).host != null
            } catch (e: URISyntaxException) {
                false
            }
        }
    }
}

/** The result of checking the gRPC form: the settings to save, or which fields are wrong. */
data class GrpcFormValidation(
    val settings: GrpcConnectionSettings?,
    val hostError: Boolean,
    val portError: Boolean,
)

/** How the saved gRPC server is doing, for the page's status panel. */
sealed interface GrpcStatus {
    /** No server is saved. */
    data object NotSetUp : GrpcStatus

    /** Saved, and the next survey streams to it. */
    data class Ready(val address: String) : GrpcStatus

    /** Saved, but gRPC is turned off in the survey setup. */
    data class OffInSurvey(val address: String) : GrpcStatus

    /** A running survey streams to it (or is connecting). */
    data class Live(val address: String, val status: DestinationStatus) : GrpcStatus

    /** The last attempt to reach it failed; [retrying] while a running survey keeps trying. */
    data class Unreachable(val address: String, val retrying: Boolean) : GrpcStatus
}

/**
 * Works out the status panel from the saved server, the survey setup, the live status in a running
 * survey, and the last failed connection attempt (which only counts if it was for the saved server).
 */
fun grpcStatus(
    saved: GrpcConnectionSettings?,
    inSetup: Boolean,
    live: DestinationStatus?,
    lastFailure: GrpcConnectionService.ConnectionFailure?,
): GrpcStatus {
    if (saved == null) return GrpcStatus.NotSetUp
    val address = saved.displayAddress
    val failedHere = lastFailure != null && lastFailure.host == saved.host && lastFailure.port == saved.port
    return when {
        failedHere && live != null && live.word != StatusWord.LIVE -> GrpcStatus.Unreachable(address, retrying = true)
        failedHere && live == null -> GrpcStatus.Unreachable(address, retrying = false)
        live != null -> GrpcStatus.Live(address, live)
        inSetup -> GrpcStatus.Ready(address)
        else -> GrpcStatus.OffInSurvey(address)
    }
}
