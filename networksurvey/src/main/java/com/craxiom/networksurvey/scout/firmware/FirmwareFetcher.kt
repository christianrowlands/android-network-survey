package com.craxiom.networksurvey.scout.firmware

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** Downloads for the firmware update: the manifest, its signature and images. */
fun interface FirmwareFetching {
    /**
     * @return The body at [url]. Canceling the caller stops the download.
     * @throws FirmwareFetchException When the request fails, the server refuses it, or the body runs
     * past [maxBytes] (checked before holding more).
     */
    suspend fun fetch(url: HttpUrl, maxBytes: Int): ByteArray
}

/** A failed firmware download. */
class FirmwareFetchException(val kind: Kind, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Kind { TOO_LARGE, HTTP, NETWORK, INSECURE }
}

/**
 * [FirmwareFetching] over OkHttp. Redirects are followed (GitHub sends release assets to its
 * object store) but never from https to http, the body is streamed so an oversized or endless
 * response is cut off at the limit, and a whole call may take [CALL_TIMEOUT_MINUTES] at most.
 *
 * @param allowInsecureHttp Plain http, for a local test server in unit tests only.
 */
class OkHttpFirmwareFetcher(
    private val client: OkHttpClient = sharedClient,
    private val allowInsecureHttp: Boolean = false,
) : FirmwareFetching {

    override suspend fun fetch(url: HttpUrl, maxBytes: Int): ByteArray {
        if (!url.isHttps && !allowInsecureHttp) throw FirmwareFetchException(FirmwareFetchException.Kind.INSECURE, "not https")
        val call = client.newCall(Request.Builder().url(url).header(CACHE_CONTROL, NO_CACHE).build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(networkFailure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = try {
                        Result.success(response.use { read(it, maxBytes) })
                    } catch (e: FirmwareFetchException) {
                        Result.failure(e)
                    } catch (e: IOException) {
                        Result.failure(networkFailure(e))
                    }
                    continuation.resumeWith(body)
                }
            })
        }
    }

    private fun read(response: Response, maxBytes: Int): ByteArray {
        if (!response.request.url.isHttps && !allowInsecureHttp) {
            throw FirmwareFetchException(FirmwareFetchException.Kind.INSECURE, "redirected off https")
        }
        if (!response.isSuccessful) throw FirmwareFetchException(FirmwareFetchException.Kind.HTTP, "HTTP ${response.code}")
        val body = response.body ?: throw FirmwareFetchException(FirmwareFetchException.Kind.HTTP, "no body")
        if (body.contentLength() > maxBytes) throw tooLarge(maxBytes)
        return readAtMost(body.byteStream(), maxBytes)
    }

    private fun readAtMost(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size() + read > maxBytes) throw tooLarge(maxBytes)
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun tooLarge(maxBytes: Int) = FirmwareFetchException(FirmwareFetchException.Kind.TOO_LARGE, "over the $maxBytes byte limit")

    private fun networkFailure(e: IOException) = FirmwareFetchException(FirmwareFetchException.Kind.NETWORK, e.message ?: "network error", e)

    private companion object {
        const val BUFFER_BYTES = 16 * 1024
        const val TIMEOUT_SECONDS = 30L

        /** Long enough for the largest image (8 MiB) at about 14 KB/s. */
        const val CALL_TIMEOUT_MINUTES = 10L
        const val CACHE_CONTROL = "Cache-Control"
        const val NO_CACHE = "no-cache"

        /** One client for every firmware download; it never follows a redirect from https to http. */
        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .followSslRedirects(false)
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .build()
        }
    }
}
