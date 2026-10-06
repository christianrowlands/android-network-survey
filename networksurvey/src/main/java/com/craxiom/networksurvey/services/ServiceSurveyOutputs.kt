package com.craxiom.networksurvey.services

import com.craxiom.networksurvey.survey.engine.SkipReason
import com.craxiom.networksurvey.survey.engine.SurveyOutputs
import com.craxiom.networksurvey.survey.engine.SurveyTypeResolver
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType

/**
 * Connects the [com.craxiom.networksurvey.survey.engine.SurveyOrchestrator] to the
 * [NetworkSurveyService] through thin adapters over the service's existing per-destination start
 * and stop methods. Those methods keep carrying the permission, radio, MDM and backpressure logic;
 * nothing is reimplemented here.
 */
class ServiceSurveyOutputs(private val service: NetworkSurveyService) : SurveyOutputs {

    override fun activeTypes(destination: Destination): Set<RecordType> = when (destination) {
        Destination.LOG_FILES -> RecordType.entries.filterTo(mutableSetOf()) { isFileLogging(it) }
        Destination.MQTT -> if (service.isMqttStreamingActive) service.mqttStreamTypes else emptySet()
        Destination.NS_ANALYTICS -> if (service.isNsAnalyticsScanningActive) service.nsAnalyticsTypes else emptySet()
        Destination.GRPC -> if (service.isGrpcActiveOrConnecting) service.grpcStreamTypes else emptySet()
        Destination.COMMUNITY -> if (service.isUploadScanningActive) communityTargets() else emptySet()
    }

    override fun isRunning(destination: Destination): Boolean = when (destination) {
        Destination.LOG_FILES -> RecordType.entries.any { isFileLogging(it) }
        Destination.MQTT -> service.isMqttStreamingActive
        Destination.NS_ANALYTICS -> service.isNsAnalyticsScanningActive
        Destination.GRPC -> service.isGrpcActiveOrConnecting
        Destination.COMMUNITY -> service.isUploadScanningActive
    }

    override fun probeDestination(destination: Destination): SkipReason? = when (destination) {
        Destination.LOG_FILES -> null
        Destination.NS_ANALYTICS -> if (service.isNsAnalyticsRegistered) null else SkipReason.NOT_REGISTERED
        Destination.MQTT -> if (service.hasMqttBrokerConfigured()) null else SkipReason.NOT_CONFIGURED
        Destination.GRPC -> if (service.hasGrpcServerConfigured()) null else SkipReason.NOT_CONFIGURED
        Destination.COMMUNITY -> if (communityTargets().isEmpty()) SkipReason.NO_UPLOAD_TARGET else null
    }

    override fun probeRecordType(type: RecordType): SkipReason? = when (type) {
        RecordType.WIFI -> if (service.isWifiRadioOn) null else SkipReason.RADIO_OFF
        RecordType.BLUETOOTH -> when {
            !service.hasBluetoothPermissions() -> SkipReason.PERMISSION_MISSING
            !service.isBluetoothReady -> SkipReason.RADIO_OFF
            else -> null
        }

        else -> null
    }

    override fun communityTargets(): Set<RecordType> = SurveyTypeResolver.communityTargets(service)

    override fun autoIncludePhoneState(): Boolean = SurveyTypeResolver.autoIncludePhoneState(service)

    override fun setFileLoggingEnabled(type: RecordType, enabled: Boolean): Boolean {
        val result: Boolean? = when (type) {
            RecordType.CELLULAR -> service.toggleCellularLogging(enabled)
            RecordType.PHONE_STATE -> service.togglePhoneStateLogging(enabled)
            RecordType.WIFI -> service.toggleWifiLogging(enabled)
            RecordType.BLUETOOTH -> service.toggleBluetoothLogging(enabled)
            RecordType.GNSS -> service.toggleGnssLogging(enabled)
            RecordType.CDR -> service.toggleCdrLogging(enabled)
        }
        return result == enabled
    }

    override fun startMqtt(types: Set<RecordType>): Boolean = service.connectToMqttBrokerForSurvey(types)

    override fun updateMqttTypes(types: Set<RecordType>) = service.setMqttStreamTypes(types)

    override fun stopMqtt() = service.disconnectFromMqttBroker()

    override fun startNsAnalytics(types: Set<RecordType>): Boolean =
        service.toggleNsAnalyticsScanning(true, types).success

    override fun updateNsAnalyticsTypes(types: Set<RecordType>) = service.setNsAnalyticsTypes(types)

    override fun stopNsAnalytics() {
        service.toggleNsAnalyticsScanning(false)
    }

    override fun startGrpc(types: Set<RecordType>): Boolean = service.connectToGrpcServerForSurvey(types)

    override fun updateGrpcTypes(types: Set<RecordType>) = service.setGrpcStreamTypes(types)

    override fun stopGrpc() = service.disconnectFromGrpcServer()

    override fun startCommunity(): Boolean = service.toggleUploadRecordSaving(true).success

    override fun stopCommunity() {
        service.toggleUploadRecordSaving(false)
    }

    override fun endMissionSession() = service.endMissionSession()

    private fun isFileLogging(type: RecordType): Boolean = when (type) {
        RecordType.CELLULAR -> service.isCellularLoggingEnabled
        RecordType.PHONE_STATE -> service.isPhoneStateLoggingEnabled
        RecordType.WIFI -> service.isWifiLoggingEnabled
        RecordType.BLUETOOTH -> service.isBluetoothLoggingEnabled
        RecordType.GNSS -> service.isGnssLoggingEnabled
        RecordType.CDR -> service.isCdrLoggingEnabled
    }
}
