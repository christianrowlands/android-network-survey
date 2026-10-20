package com.craxiom.networksurvey.ui.organization

import androidx.annotation.StringRes
import com.craxiom.mqttlibrary.MqttConstants
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.mdm.ManagedState
import com.craxiom.networksurvey.survey.mdm.RestrictionDefinition
import com.craxiom.networksurvey.survey.mdm.RestrictionsSnapshot
import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys
import com.craxiom.networksurvey.survey.mdm.SurveyPolicy

/** What the Organization settings page shows. */
data class OrganizationSettingsUi(
    val managed: Boolean = false,
    val overrideOn: Boolean = false,
    val groups: List<OrgGroupUi> = emptyList(),
    val communityOffBecauseManaged: Boolean = false,
    val brokerOnly: Boolean = false,
    @StringRes val externalUploadTitleRes: Int = 0,
)

/** One titled group of values on the page. */
data class OrgGroupUi(val group: OrgGroup, val rows: List<OrgRowUi>)

/** The page's groups, in order. */
enum class OrgGroup { SURVEY, PERMISSIONS, MQTT, OTHER }

/**
 * One managed value.
 *
 * @property titleRes The title administrators see in their console, or 0 for a key the app does not
 *                    declare, which is shown by its raw [key].
 */
data class OrgRowUi(val key: String, @StringRes val titleRes: Int, val value: OrgValue)

/** A managed value, in the words the page uses. */
sealed interface OrgValue {
    data object On : OrgValue
    data object Off : OrgValue
    data object UserChooses : OrgValue
    data object Allowed : OrgValue
    data object NotAllowed : OrgValue

    /** A secret, such as the broker password: only that it is set is shown. */
    data object Secret : OrgValue

    data class Text(val text: String) : OrgValue
}

/**
 * Turns the managed configuration into the Organization settings page: every key the organization
 * set, grouped and in the order the schema declares them, plus the three data permissions, which
 * are always listed with the value in force. Pure, so it is tested on the JVM.
 */
object OrganizationSettingsReader {

    private val PERMISSION_KEYS = listOf(
        NetworkSurveyConstants.MDM_PROPERTY_ALLOW_EXTERNAL_DATA_UPLOAD,
        NetworkSurveyConstants.MDM_PROPERTY_ALLOW_NS_ANALYTICS,
        NetworkSurveyConstants.PROPERTY_ALLOW_INTENT_CONTROL,
    )

    private val MQTT_EXTRA_KEYS = setOf(
        NetworkSurveyConstants.PROPERTY_MQTT_DEVICE_STATUS_STREAM_ENABLED,
        NetworkSurveyConstants.PROPERTY_MQTT_WATCHLIST_STREAM_ENABLED,
    )

    private val SECRET_KEYS = setOf(MqttConstants.PROPERTY_MQTT_PASSWORD)

    /**
     * @param definitions The schema, from [com.craxiom.networksurvey.survey.mdm.AppRestrictionsDefinitions].
     * @param overrideOn  True while the user is using their own settings.
     */
    fun read(snapshot: RestrictionsSnapshot, definitions: List<RestrictionDefinition>, overrideOn: Boolean): OrganizationSettingsUi {
        if (!snapshot.isManaged) return OrganizationSettingsUi(managed = false, overrideOn = overrideOn)

        val byKey = definitions.associateBy { it.key }
        val order = definitions.mapIndexed { index, definition -> definition.key to index }.toMap()
        val present = snapshot.keys()
            .filterNot { it in PERMISSION_KEYS }
            .sortedWith(compareBy<String> { order[it] ?: Int.MAX_VALUE }.thenBy { it })

        val rows = present.groupBy(::groupOf).mapValues { (_, keys) ->
            keys.map { key -> OrgRowUi(key, byKey[key]?.titleRes ?: 0, valueOf(key, snapshot.get(key))) }
        }.toMutableMap()
        rows[OrgGroup.PERMISSIONS] = PERMISSION_KEYS.map { key ->
            OrgRowUi(key, byKey[key]?.titleRes ?: 0, permissionValue(key, snapshot))
        }

        val externalUploadSet = snapshot.contains(NetworkSurveyConstants.MDM_PROPERTY_ALLOW_EXTERNAL_DATA_UPLOAD)
        return OrganizationSettingsUi(
            managed = true,
            overrideOn = overrideOn,
            groups = OrgGroup.entries.mapNotNull { group -> rows[group]?.takeIf { it.isNotEmpty() }?.let { OrgGroupUi(group, it) } },
            communityOffBecauseManaged = !externalUploadSet,
            brokerOnly = present.isNotEmpty() && present.all { groupOf(it) == OrgGroup.MQTT },
            externalUploadTitleRes = byKey[NetworkSurveyConstants.MDM_PROPERTY_ALLOW_EXTERNAL_DATA_UPLOAD]?.titleRes ?: 0,
        )
    }

    private fun groupOf(key: String): OrgGroup = when {
        key in SurveyMdmKeys.ALL_SETUP_KEYS -> OrgGroup.SURVEY
        key in PERMISSION_KEYS -> OrgGroup.PERMISSIONS
        key.startsWith(MQTT_KEY_PREFIX) || key in MQTT_EXTRA_KEYS -> OrgGroup.MQTT
        else -> OrgGroup.OTHER
    }

    private fun valueOf(key: String, value: Any?): OrgValue = when {
        key in SECRET_KEYS -> OrgValue.Secret
        key in SurveyMdmKeys.ALL_SETUP_KEYS -> when (ManagedState.parse(value)) {
            ManagedState.ON -> OrgValue.On
            ManagedState.OFF -> OrgValue.Off
            ManagedState.USER -> OrgValue.UserChooses
        }

        value is Boolean -> if (value) OrgValue.On else OrgValue.Off
        else -> OrgValue.Text(value?.toString().orEmpty())
    }

    /** The data permissions in force: the organization's value, or what applies when it is unset. */
    private fun permissionValue(key: String, snapshot: RestrictionsSnapshot): OrgValue = when (key) {
        NetworkSurveyConstants.MDM_PROPERTY_ALLOW_EXTERNAL_DATA_UPLOAD ->
            if (SurveyPolicy.isExternalDataUploadAllowed(snapshot)) OrgValue.Allowed else OrgValue.NotAllowed

        NetworkSurveyConstants.MDM_PROPERTY_ALLOW_NS_ANALYTICS ->
            if (snapshot.getBoolean(key) != false) OrgValue.Allowed else OrgValue.NotAllowed

        else -> when (snapshot.getBoolean(key)) {
            true -> OrgValue.Allowed
            false -> OrgValue.NotAllowed
            null -> OrgValue.UserChooses
        }
    }

    private const val MQTT_KEY_PREFIX = "mqtt_"
}
