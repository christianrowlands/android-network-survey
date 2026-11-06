package com.craxiom.networksurvey.ui.survey.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.CdrPermissions
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.ui.survey.SurveyEventMessages
import com.craxiom.networksurvey.ui.theme.NsTheme

/** The name a record type goes by everywhere on the Survey screen. */
@get:StringRes
val RecordType.labelRes: Int
    get() = when (this) {
        RecordType.CELLULAR -> R.string.survey_type_cellular
        RecordType.PHONE_STATE -> R.string.survey_type_phone_state
        RecordType.WIFI -> R.string.survey_type_wifi
        RecordType.BLUETOOTH -> R.string.survey_type_bluetooth
        RecordType.GNSS -> R.string.survey_type_gnss
        RecordType.CDR -> R.string.survey_type_cdr
        RecordType.WIFI_CLIENTS -> R.string.survey_type_wifi_clients
        RecordType.WIFI_RAW -> R.string.survey_type_wifi_raw
    }

/** The one-line descriptor under a record type tile. */
@get:StringRes
val RecordType.descriptionRes: Int
    get() = when (this) {
        RecordType.CELLULAR -> R.string.survey_type_cellular_description
        RecordType.PHONE_STATE -> R.string.survey_type_phone_state_description
        RecordType.WIFI -> R.string.survey_type_wifi_description
        RecordType.BLUETOOTH -> R.string.survey_type_bluetooth_description
        RecordType.GNSS -> R.string.survey_type_gnss_description
        RecordType.CDR -> if (CdrPermissions.LOGS_TEXT_MESSAGES) {
            R.string.survey_type_cdr_description
        } else {
            R.string.survey_type_cdr_description_no_texts
        }

        RecordType.WIFI_CLIENTS -> R.string.survey_type_wifi_clients_description
        RecordType.WIFI_RAW -> R.string.survey_type_wifi_raw_description
    }

/**
 * What an on tile says when no selected destination takes the type: Raw frames, which only Log
 * files save, says what to turn on.
 */
@get:StringRes
val RecordType.notSentAnywhereRes: Int
    get() = if (this == RecordType.WIFI_RAW) R.string.survey_type_wifi_raw_needs_log_files else R.string.survey_type_not_sent_anywhere

@get:DrawableRes
val RecordType.iconRes: Int
    get() = when (this) {
        RecordType.CELLULAR -> R.drawable.ic_cell_tower
        RecordType.PHONE_STATE -> R.drawable.ic_phone_state
        RecordType.WIFI -> R.drawable.ic_wifi
        RecordType.BLUETOOTH -> R.drawable.ic_bluetooth
        RecordType.GNSS -> R.drawable.ic_gnss
        RecordType.CDR -> R.drawable.ic_cdr
        RecordType.WIFI_CLIENTS -> R.drawable.ic_devices
        RecordType.WIFI_RAW -> R.drawable.ic_graphic_eq
    }

/** The record type's own color, used on its tile, chip, bar segment and legend swatch. */
val RecordType.color: Color
    @Composable
    @ReadOnlyComposable
    get() = NsTheme.colors.protocol.let {
        when (this) {
            RecordType.CELLULAR -> it.cellular
            RecordType.PHONE_STATE -> it.phoneState
            RecordType.WIFI -> it.wifi
            RecordType.BLUETOOTH -> it.bluetooth
            RecordType.GNSS -> it.gnss
            RecordType.CDR -> it.cdr
            RecordType.WIFI_CLIENTS -> it.wifiClients
            RecordType.WIFI_RAW -> it.wifiRaw
        }
    }

@get:StringRes
val Destination.labelRes: Int
    get() = when (this) {
        Destination.LOG_FILES -> R.string.survey_dest_log_files
        Destination.NS_ANALYTICS -> R.string.survey_dest_ns_analytics
        Destination.MQTT -> R.string.survey_dest_mqtt
        Destination.GRPC -> R.string.survey_dest_grpc
        Destination.COMMUNITY -> R.string.survey_dest_community
    }

@get:DrawableRes
val Destination.iconRes: Int
    get() = when (this) {
        Destination.LOG_FILES -> R.drawable.ic_folder
        Destination.NS_ANALYTICS -> R.drawable.ic_ns_analytics
        Destination.MQTT -> R.drawable.ic_cloud_connection
        Destination.GRPC -> R.drawable.ic_dns
        Destination.COMMUNITY -> R.drawable.ic_public
    }

/** Joins names the way the screen reads them aloud: "Cellular, Phone state and Wi-Fi". */
@Composable
@ReadOnlyComposable
fun joinNames(names: List<String>): String = SurveyEventMessages.join(LocalResources.current, names)

@Composable
@ReadOnlyComposable
fun recordTypeNames(types: List<RecordType>): String = joinNames(types.map { stringResource(it.labelRes) })

/** "Wi-Fi clients or Raw frames": for a claim that holds for any one of [types], not all of them. */
@Composable
@ReadOnlyComposable
fun recordTypeNamesOr(types: List<RecordType>): String =
    SurveyEventMessages.join(LocalResources.current, types.map { stringResource(it.labelRes) }, R.string.survey_list_last_or)

/** As [recordTypeNames], naming Wi-Fi "Wi-Fi from NS-ABCD" while [wifiScoutName]'s Scout is the source. */
@Composable
@ReadOnlyComposable
fun recordTypeNames(types: List<RecordType>, wifiScoutName: String?): String = joinNames(
    types.map { type ->
        if (type == RecordType.WIFI && wifiScoutName != null) {
            stringResource(R.string.survey_type_wifi_from, wifiScoutName)
        } else {
            stringResource(type.labelRes)
        }
    },
)

@Composable
@ReadOnlyComposable
fun destinationNames(destinations: List<Destination>): String =
    joinNames(destinations.map { stringResource(it.labelRes) })
