package com.craxiom.networksurvey.services.watchlist

import com.craxiom.messaging.WatchlistEntry
import com.craxiom.messaging.watchlist.WatchlistMatchField
import com.craxiom.messaging.watchlist.WatchlistMatchType
import com.craxiom.networksurvey.logging.db.model.WatchlistEntryEntity
import com.craxiom.networksurvey.logging.db.model.WatchlistHitEntity
import com.craxiom.networksurvey.util.NsUtils
import com.google.protobuf.BoolValue
import com.google.protobuf.StringValue
import java.time.Instant
import java.time.ZoneId

/**
 * Shared conversions from the app's watchlist Room entities to the network-survey-messaging protobuf
 * types. Kept in one place so the match publisher (in [WatchlistDetectionManager]) and the entry-change
 * publisher ([WatchlistChangePublisher]) build identical values.
 */
object WatchlistProtoMapper {

    /**
     * Format a milliseconds-since-epoch timestamp as an RFC 3339 string using the device's time zone,
     * matching how every other record stamps deviceTime.
     */
    fun rfc3339(millis: Long): String =
        NsUtils.getRfc3339String(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    /**
     * Map the entity's stored match-type string to the protobuf enum. Only exact matching exists today.
     */
    fun toMatchType(matchType: String?): WatchlistMatchType =
        if (matchType == WatchlistEntryEntity.MATCH_TYPE_EXACT) WatchlistMatchType.EXACT else WatchlistMatchType.UNKNOWN

    /**
     * Map the matched-field marker recorded at detection time to the protobuf enum. The detector only
     * ever reports SSID or BSSID today; BOTH is reserved for a future release.
     */
    fun toMatchField(matchedField: String): WatchlistMatchField = when (matchedField) {
        WatchlistHitEntity.MATCHED_FIELD_SSID -> WatchlistMatchField.SSID
        WatchlistHitEntity.MATCHED_FIELD_BSSID -> WatchlistMatchField.BSSID
        else -> WatchlistMatchField.UNKNOWN
    }

    /**
     * Convert a watchlist entry into its full protobuf representation. The nullable SSID/BSSID are left
     * unset (rather than empty strings) when the entry does not watch by that field.
     */
    fun toProtoEntry(entity: WatchlistEntryEntity): WatchlistEntry {
        val builder = WatchlistEntry.newBuilder()
            .setEntryUuid(entity.uuid ?: "")
            .setLabel(entity.label ?: "")
            .setMatchType(toMatchType(entity.matchType))
            // The BoolValue wrapper keeps "enabled":false on the wire; a plain proto3 bool would be
            // omitted from the JSON when false, violating the schema's required list.
            .setEnabled(BoolValue.of(entity.enabled))
            .setCooldownSeconds(entity.cooldownSeconds)
            .setCreatedAt(rfc3339(entity.createdAt))
            .setUpdatedAt(rfc3339(entity.updatedAt))
        entity.ssid?.let { builder.ssid = StringValue.of(it) }
        entity.bssid?.let { builder.bssid = StringValue.of(it) }
        return builder.build()
    }
}
