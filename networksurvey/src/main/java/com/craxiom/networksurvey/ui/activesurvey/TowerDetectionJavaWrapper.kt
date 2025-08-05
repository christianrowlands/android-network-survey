package com.craxiom.networksurvey.ui.activesurvey

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.function.Consumer

/**
 * Java-friendly wrapper for TowerDetectionManager.
 * 
 * This class provides non-suspend methods that can be called from Java code,
 * handling the coroutine execution internally.
 */
class TowerDetectionJavaWrapper(context: Context) {
    private val towerDetectionManager = TowerDetectionManager(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Check if a tower is new asynchronously and invoke the callback with the result.
     * 
     * @param mcc Mobile Country Code
     * @param mnc Mobile Network Code
     * @param area TAC/LAC
     * @param cellId Cell ID
     * @param radio Radio technology (LTE, NR, GSM, UMTS)
     * @param callback Callback to invoke with the result (true if new tower, false if known)
     */
    fun checkIfTowerIsNewAsync(
        mcc: Int,
        mnc: Int,
        area: Int,
        cellId: Long,
        radio: String,
        callback: Consumer<Boolean>
    ) {
        coroutineScope.launch {
            try {
                val isNew = towerDetectionManager.checkIfTowerIsNew(mcc, mnc, area, cellId, radio)
                callback.accept(isNew)
            } catch (e: Exception) {
                Timber.e(e, "Error checking tower status from Java wrapper")
                // On error, assume tower is known to avoid false positives
                callback.accept(false)
            }
        }
    }
    
    /**
     * Check if a tower is new asynchronously with an error callback.
     * 
     * @param mcc Mobile Country Code
     * @param mnc Mobile Network Code
     * @param area TAC/LAC
     * @param cellId Cell ID
     * @param radio Radio technology (LTE, NR, GSM, UMTS)
     * @param onSuccess Callback to invoke with the result (true if new tower, false if known)
     * @param onError Callback to invoke if an error occurs
     */
    fun checkIfTowerIsNewAsync(
        mcc: Int,
        mnc: Int,
        area: Int,
        cellId: Long,
        radio: String,
        onSuccess: Consumer<Boolean>,
        onError: Consumer<Exception>
    ) {
        coroutineScope.launch {
            try {
                val isNew = towerDetectionManager.checkIfTowerIsNew(mcc, mnc, area, cellId, radio)
                onSuccess.accept(isNew)
            } catch (e: Exception) {
                Timber.e(e, "Error checking tower status from Java wrapper")
                onError.accept(e)
            }
        }
    }
}