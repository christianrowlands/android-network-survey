@file:JvmName("UploadResponses")

package com.craxiom.networksurvey.logging.db.uploader

import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Helpers the community database upload clients share for reading and logging HTTP responses.
 */

/** The most of a failed response body that is logged, so an HTML error page can't flood the log. */
const val MAX_LOGGED_BODY_LENGTH = 200

/**
 * @return The body of a successful response, or the error body of a failed one; Retrofit keeps them
 * apart, so reading only [Response.body] loses what the server said about a failure.
 */
fun Response<ResponseBody>.anyBody(): ResponseBody? = body() ?: errorBody()

/** @return The start of [body], short enough to log. */
fun logSnippet(body: String): String = body.take(MAX_LOGGED_BODY_LENGTH)

/**
 * @return True if [body] is an HTML page, which an upload API never answers with but a proxy or CDN
 * in front of it does, such as an nginx or Cloudflare error page.
 */
fun looksLikeHtml(body: String): Boolean = body.trimStart().startsWith("<")
