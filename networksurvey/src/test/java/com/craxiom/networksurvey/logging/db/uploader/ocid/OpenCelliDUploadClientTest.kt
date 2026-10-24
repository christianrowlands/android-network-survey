package com.craxiom.networksurvey.logging.db.uploader.ocid

import com.craxiom.networksurvey.logging.db.uploader.RequestResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How OpenCelliD upload responses are classified. A 403 from the proxy in front of OpenCelliD (the
 * nginx page it answered every multipart upload with in October 2026) must read as a server error
 * that is retried, not as an invalid key, while OpenCelliD's own key errors still say so.
 */
class OpenCelliDUploadClientTest {

    @Test
    fun okBodyIsSuccess() {
        assertEquals(RequestResult.Success, OpenCelliDUploadClient.handleOcidResponse(200, "0,OK\n"))
    }

    @Test
    fun urlEncodedInvalidTokenIsInvalidApiKey() {
        assertEquals(RequestResult.InvalidApiKey, OpenCelliDUploadClient.handleOcidResponse(200, "Err: Invalid token"))
    }

    @Test
    fun unauthorizedIsInvalidApiKey() {
        assertEquals(RequestResult.InvalidApiKey, OpenCelliDUploadClient.handleOcidResponse(401, INVALID_KEY_JSON))
        assertEquals(RequestResult.InvalidApiKey, OpenCelliDUploadClient.handleOcidResponse(401, ""))
    }

    @Test
    fun forbiddenFromOpenCelliDIsInvalidApiKey() {
        assertEquals(RequestResult.InvalidApiKey, OpenCelliDUploadClient.handleOcidResponse(403, INVALID_KEY_JSON))
        // A key rejection worded some other way must still stop the retries and say so
        assertEquals(RequestResult.InvalidApiKey, OpenCelliDUploadClient.handleOcidResponse(403, "Err: API key is disabled"))
        assertEquals(RequestResult.InvalidApiKey, OpenCelliDUploadClient.handleOcidResponse(403, ""))
    }

    @Test
    fun forbiddenProxyPageIsServerError() {
        assertEquals(RequestResult.ServerError, OpenCelliDUploadClient.handleOcidResponse(403, NGINX_FORBIDDEN_PAGE))
        assertEquals(RequestResult.ServerError, OpenCelliDUploadClient.handleOcidResponse(403, "\n<!DOCTYPE html><html></html>"))
    }

    @Test
    fun serverErrorsAreServerError() {
        assertEquals(RequestResult.ServerError, OpenCelliDUploadClient.handleOcidResponse(500, ""))
        assertEquals(RequestResult.ServerError, OpenCelliDUploadClient.handleOcidResponse(521, "<html>origin down</html>"))
        // Mentioning a key in an error page is not a key rejection
        assertEquals(RequestResult.ServerError, OpenCelliDUploadClient.handleOcidResponse(500, "Trace: Invalid API key check failed"))
    }

    @Test
    fun badRequestIsConfigurationError() {
        assertEquals(RequestResult.ConfigurationError, OpenCelliDUploadClient.handleOcidResponse(400, ""))
    }

    @Test
    fun filesizeLimitIsLimitExceeded() {
        assertEquals(RequestResult.LimitExceeded, OpenCelliDUploadClient.handleOcidResponse(200, "Exceeded filesize limit"))
    }

    private companion object {
        const val INVALID_KEY_JSON = """{"error":"Invalid API key"}"""
        const val NGINX_FORBIDDEN_PAGE =
            "<html>\r\n<head><title>403 Forbidden</title></head>\r\n<body>\r\n<center><h1>403 Forbidden</h1></center>\r\n" +
                "<hr><center>nginx</center>\r\n</body>\r\n</html>"
    }
}
