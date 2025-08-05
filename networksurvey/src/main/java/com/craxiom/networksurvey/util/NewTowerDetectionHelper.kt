package com.craxiom.networksurvey.util

import android.content.Context
import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.UmtsRecord
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.ui.activesurvey.NewTowerNotificationHelper
import com.craxiom.networksurvey.ui.activesurvey.TowerDetectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Helper class for new tower detection.
 *
 * This class implements the cellular survey record listener to monitor for new towers
 * when the preference is enabled and upload scanning is active.
 */
class NewTowerDetectionHelper(
    private val context: Context,
    private val uploadScanningChecker: UploadScanningChecker
) : ICellularSurveyRecordListener {

    private val towerDetectionManager = TowerDetectionManager(context)
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastServingCellKey: String? = null

    /**
     * Interface to check if upload scanning is active.
     * This allows the helper to work without direct dependency on NetworkSurveyService.
     */
    interface UploadScanningChecker {
        fun isUploadScanningActive(): Boolean
    }

    /**
     * Resets the last serving cell key. Should be called when alerts are disabled.
     */
    fun reset() {
        lastServingCellKey = null
    }

    override fun onCellularBatch(cellularGroup: List<CellularRecordWrapper>, subscriptionId: Int) {
        // Only process if upload scanning is active
        if (!uploadScanningChecker.isUploadScanningActive()) {
            return
        }

        // Find the serving cell in the batch
        cellularGroup.firstOrNull { CellularUtils.isServingCell(it.cellularRecord) }
            ?.let { servingCell ->
                checkServingCellForNewTower(servingCell)
            }
    }

    /**
     * Check if the serving cell is a new tower and fire notification if needed.
     */
    private fun checkServingCellForNewTower(cellularRecord: CellularRecordWrapper) {
        val protocol = cellularRecord.cellularProtocol
        val record = cellularRecord.cellularRecord

        // Extract cell identity based on protocol
        val cellIdentity = when (protocol) {
            CellularProtocol.LTE -> {
                val lte = record as LteRecord
                val data = lte.data ?: return
                CellIdentity(
                    mcc = if (data.hasMcc()) data.mcc.value else 0,
                    mnc = if (data.hasMnc()) data.mnc.value else 0,
                    area = if (data.hasTac()) data.tac.value else 0,
                    cellId = if (data.hasEci()) data.eci.value.toLong() else 0L,
                    radio = "LTE"
                )
            }

            CellularProtocol.NR -> {
                val nr = record as NrRecord
                val data = nr.data ?: return
                CellIdentity(
                    mcc = if (data.hasMcc()) data.mcc.value else 0,
                    mnc = if (data.hasMnc()) data.mnc.value else 0,
                    area = if (data.hasTac()) data.tac.value else 0,
                    cellId = if (data.hasNci()) data.nci.value else 0L,
                    radio = "NR"
                )
            }

            CellularProtocol.GSM -> {
                val gsm = record as GsmRecord
                val data = gsm.data ?: return
                CellIdentity(
                    mcc = if (data.hasMcc()) data.mcc.value else 0,
                    mnc = if (data.hasMnc()) data.mnc.value else 0,
                    area = if (data.hasLac()) data.lac.value else 0,
                    cellId = if (data.hasCi()) data.ci.value.toLong() else 0L,
                    radio = "GSM"
                )
            }

            CellularProtocol.UMTS -> {
                val umts = record as UmtsRecord
                val data = umts.data ?: return
                CellIdentity(
                    mcc = if (data.hasMcc()) data.mcc.value else 0,
                    mnc = if (data.hasMnc()) data.mnc.value else 0,
                    area = if (data.hasLac()) data.lac.value else 0,
                    cellId = if (data.hasCid()) data.cid.value.toLong() else 0L,
                    radio = "UMTS"
                )
            }

            else -> return // Unsupported protocol
        }

        val cellKey =
            "${cellIdentity.mcc}-${cellIdentity.mnc}-${cellIdentity.area}-${cellIdentity.cellId}"

        // Check if this is a different cell than the last one
        if (cellKey != lastServingCellKey && cellIdentity.mcc > 0 && cellIdentity.cellId > 0) {
            lastServingCellKey = cellKey

            // Check if this is a new tower using TowerDetectionManager
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    Timber.d(
                        "Checking to see if tower is new: Cell %s-%s-%s-%s (%s)",
                        cellIdentity.mcc, cellIdentity.mnc, cellIdentity.area,
                        cellIdentity.cellId, cellIdentity.radio
                    )

                    val isNewTower = towerDetectionManager.checkIfTowerIsNew(
                        cellIdentity.mcc,
                        cellIdentity.mnc,
                        cellIdentity.area,
                        cellIdentity.cellId,
                        cellIdentity.radio
                    )

                    if (isNewTower) {
                        // Show notification on UI thread
                        launch(Dispatchers.Main) {
                            NewTowerNotificationHelper.showNewTowerNotification(
                                context,
                                cellIdentity.mcc,
                                cellIdentity.mnc,
                                cellIdentity.area,
                                cellIdentity.cellId,
                                cellIdentity.radio
                            )
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error in new tower detection")
                }
            }
        }
    }

    /**
     * Data class to hold cell identity information.
     */
    private data class CellIdentity(
        val mcc: Int,
        val mnc: Int,
        val area: Int,
        val cellId: Long,
        val radio: String
    )
}