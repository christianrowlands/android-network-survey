package com.craxiom.networksurvey.services

import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener
import com.craxiom.networksurvey.listeners.ICdrEventListener
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener
import com.craxiom.networksurvey.listeners.IDeviceStatusListener
import com.craxiom.networksurvey.listeners.IGnssSurveyRecordListener
import com.craxiom.networksurvey.listeners.ILiveScanView
import com.craxiom.networksurvey.listeners.IPhoneStateListener
import com.craxiom.networksurvey.listeners.IPrivacyZoneExempt
import com.craxiom.networksurvey.listeners.IWifiClientSurveyRecordListener
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener
import com.craxiom.networksurvey.services.controller.WifiClientsController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.util.jar.JarFile

/**
 * Walks every record listener in the app, so a new sink cannot get around the privacy zone hold:
 *
 * 1. Every class that takes survey records is a sink unless it is a live screen ([ILiveScanView]) or
 *    exempt ([IPrivacyZoneExempt]), and those are listed here by name. A new screen or exemption has
 *    to be added on purpose; a sink that claims either fails.
 * 2. The record fan-outs keep their listeners only in [RecordListeners] (the gated delivery path),
 *    never in a plain collection that could be looped over without the hold.
 */
class PrivacyZoneSinkWalkTest {

    @Test
    fun `only the live screens and the Watchlist receive records inside a zone`() {
        val listeners = appClasses().filter { type -> RECORD_LISTENERS.any { it.isAssignableFrom(type) } && !type.isInterface }
        val names = listeners.map { it.name }.toSet()
        // The walk really saw the app's sinks, so a passing test is not an empty one
        KNOWN_SINKS.forEach { assertTrue("The walk did not find $it", it in names) }

        val reachWhileHeld = listeners
            .filter { ILiveScanView::class.java.isAssignableFrom(it) || IPrivacyZoneExempt::class.java.isAssignableFrom(it) }
            .map { it.name }
            .toSortedSet()
        assertEquals(
            "A record listener that still receives records inside a privacy zone was added or removed. " +
                "Sinks (files, streams, uploads) must never be live views or exempt.",
            ALLOWED_WHILE_HELD.toSortedSet(), reachWhileHeld,
        )
    }

    @Test
    fun `the record fan-outs hold listeners only in RecordListeners`() {
        listOf(SurveyRecordProcessor::class.java, WifiClientsController::class.java).forEach { owner ->
            owner.declaredFields.forEach { field ->
                val plainHolder = (java.util.Collection::class.java.isAssignableFrom(field.type) ||
                    java.util.Map::class.java.isAssignableFrom(field.type)) && typeArguments(field).any { it in RECORD_LISTENERS }
                assertTrue("${owner.simpleName}.${field.name} keeps record listeners outside RecordListeners", !plainHolder)
            }
        }

        val gated = SurveyRecordProcessor::class.java.declaredFields
            .filter { it.type == RecordListeners::class.java }
            .flatMap { typeArguments(it) }
            .toSet()
        assertEquals(
            setOf(
                ICellularSurveyRecordListener::class.java, IWifiSurveyRecordListener::class.java,
                IBluetoothSurveyRecordListener::class.java, IGnssSurveyRecordListener::class.java,
                ICdrEventListener::class.java, IDeviceStatusListener::class.java, IPhoneStateListener::class.java,
            ),
            gated,
        )
    }

    private fun typeArguments(field: Field): List<Class<*>> =
        (field.genericType as? ParameterizedType)?.actualTypeArguments?.mapNotNull { it as? Class<*> }.orEmpty()

    /**
     * Every class compiled from the app's main sources: the class folders or jars that hold a Java
     * and a Kotlin class of the app (the unit test classpath keeps them apart from the tests).
     */
    private fun appClasses(): List<Class<*>> {
        val loader = javaClass.classLoader!!
        val roots = listOf(SurveyRecordProcessor::class.java, WifiClientsController::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()) }
            .distinct()
        return roots.flatMap { classNames(it) }.distinct().mapNotNull { name ->
            try {
                Class.forName(name, false, loader)
            } catch (e: LinkageError) {
                null
            } catch (e: ClassNotFoundException) {
                null
            }
        }
    }

    /** @return The app's class names in a class folder or a jar. */
    private fun classNames(root: File): List<String> {
        val paths = if (root.isDirectory) {
            root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toList()
        } else {
            JarFile(root).use { jar -> jar.entries().toList().map { it.name } }
        }
        return paths.filter { it.startsWith(PACKAGE_PATH) && it.endsWith(CLASS_SUFFIX) }
            .map { it.removeSuffix(CLASS_SUFFIX).replace('/', '.') }
    }

    private companion object {
        const val PACKAGE_PATH = "com/craxiom/networksurvey"
        const val CLASS_SUFFIX = ".class"
        const val APP = "com.craxiom.networksurvey"

        val RECORD_LISTENERS = setOf(
            ICellularSurveyRecordListener::class.java, IWifiSurveyRecordListener::class.java,
            IBluetoothSurveyRecordListener::class.java, IGnssSurveyRecordListener::class.java,
            ICdrEventListener::class.java, IDeviceStatusListener::class.java, IPhoneStateListener::class.java,
            IWifiClientSurveyRecordListener::class.java,
        )

        /** The live screens and the one exempt listener (the Watchlist detector). */
        val ALLOWED_WHILE_HELD = setOf(
            "$APP.ui.map.MapTabViewModel",
            "$APP.ui.cellular.details.CellularDetailsViewModel",
            "$APP.ui.wifi.model.WifiListViewModel",
            "$APP.ui.wifi.details.WifiDetailsViewModel",
            "$APP.ui.wifi.spectrum.WifiSpectrumViewModel",
            "$APP.ui.bluetooth.list.BluetoothListViewModel",
            "$APP.ui.bluetooth.details.BluetoothDetailsViewModel",
            "$APP.services.watchlist.WatchlistDetectionManager",
        )

        /** Sinks the walk must find: one of each destination and a log file. */
        val KNOWN_SINKS = setOf(
            "$APP.mqtt.MqttConnection",
            "$APP.services.GrpcConnectionService",
            "$APP.logging.db.NsAnalyticsDataStore",
            "$APP.logging.db.DbUploadStore",
            "$APP.logging.LteCsvLogger",
            "$APP.logging.CdrLogger",
        )
    }
}
