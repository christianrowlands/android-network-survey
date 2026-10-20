package com.craxiom.networksurvey.ui.survey.model

import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.DestinationCapabilities
import com.craxiom.networksurvey.survey.model.RecordType

/**
 * A column of the "What goes where" table: a destination, with the community databases split into
 * their two upload targets because they take different record types.
 */
enum class RoutingColumn(val destination: Destination) {
    LOG_FILES(Destination.LOG_FILES),
    NS_ANALYTICS(Destination.NS_ANALYTICS),
    MQTT(Destination.MQTT),
    GRPC(Destination.GRPC),
    OPENCELLID(Destination.COMMUNITY),
    BEACONDB(Destination.COMMUNITY),
    ;

    /** @return The record types this column takes. */
    fun accepts(): Set<RecordType> = when (this) {
        OPENCELLID -> DestinationCapabilities.OPENCELLID_TYPES
        BEACONDB -> DestinationCapabilities.BEACONDB_TYPES
        else -> DestinationCapabilities.accepts(destination)
    }
}

/** One row of the table: a record type the survey records, and the columns it goes to. */
data class RoutingRowUi(val type: RecordType, val sentTo: Set<RoutingColumn>) {
    /** True when none of the switched-on destinations takes this type. */
    val notSentAnywhere: Boolean
        get() = sentTo.isEmpty()
}

/**
 * The "What goes where" table: the survey's record types against the destinations that are on.
 *
 * @property columns         The switched-on destinations, the community databases as their targets.
 * @property rows            One per record type the survey records, in screen order.
 * @property unselected      The record types the survey doesn't record.
 * @property mqttDeviceStatus True when MQTT runs with no record types and sends only device status
 *                           (a survey an organization, an upgrade or another app set up that way),
 *                           so the table adds a Device status row for it.
 */
data class RoutingTableUi(
    val columns: List<RoutingColumn>,
    val rows: List<RoutingRowUi>,
    val unselected: List<RecordType>,
    val mqttDeviceStatus: Boolean = false,
) {
    /** The record types no switched-on destination takes. */
    val notSentAnywhere: Set<RecordType>
        get() = rows.filter { it.notSentAnywhere }.mapTo(mutableSetOf()) { it.type }

    companion object {
        /**
         * @param types            The record types the survey records.
         * @param destinations     The destinations that are on.
         * @param info             Which community upload targets are selected.
         * @param mqttDeviceStatus True when MQTT (one of [destinations]) sends only device status.
         * @return The table, or null when there is nothing to record or nowhere to send it.
         */
        fun of(
            types: Set<RecordType>,
            destinations: Collection<Destination>,
            info: DestinationInfo,
            mqttDeviceStatus: Boolean = false,
        ): RoutingTableUi? {
            val deviceStatus = mqttDeviceStatus && Destination.MQTT in destinations
            if ((types.isEmpty() && !deviceStatus) || destinations.isEmpty()) return null
            val columns = RoutingColumn.entries.filter { column ->
                column.destination in destinations && when (column) {
                    RoutingColumn.OPENCELLID -> info.openCelliDSelected
                    RoutingColumn.BEACONDB -> info.beaconDbSelected
                    else -> true
                }
            }
            return RoutingTableUi(
                columns = columns,
                rows = RecordType.entries.filter { it in types }.map { type ->
                    RoutingRowUi(type, columns.filterTo(linkedSetOf()) { type in it.accepts() })
                },
                unselected = RecordType.entries.filter { it !in types },
                mqttDeviceStatus = deviceStatus,
            )
        }
    }
}
