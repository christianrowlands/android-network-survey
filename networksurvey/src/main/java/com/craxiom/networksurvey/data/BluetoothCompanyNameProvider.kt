package com.craxiom.networksurvey.data

import android.content.Context
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

/**
 * Provider for the BluetoothCompanyNameResolver singleton. This helps init the resolver and also
 * ensures that only one instance is created.
 */
object BluetoothCompanyNameProvider {

    private val instanceRef = AtomicReference<BluetoothCompanyNameResolver?>()

    /**
     * Lazy-inits and returns the shared instance. Safe to call from any thread.
     */
    @JvmStatic
    fun getInstance(context: Context): BluetoothCompanyNameResolver {
        return instanceRef.get() ?: synchronized(this) {
            instanceRef.get() ?: run {

                val startTime = System.currentTimeMillis()
                val resolver = BluetoothCompanyNameResolver(
                    BluetoothCompanyResolver(context.applicationContext),
                    BluetoothUuidResolver(context.applicationContext)
                )
                Timber.d(
                    "BluetoothCompanyResolver and BluetoothUuidResolver took %d ms to create",
                    System.currentTimeMillis() - startTime
                )
                instanceRef.set(resolver)
                resolver
            }
        }
    }

    /**
     * For testing or forced reset.
     */
    @JvmStatic
    fun reset() {
        instanceRef.set(null)
    }
}
