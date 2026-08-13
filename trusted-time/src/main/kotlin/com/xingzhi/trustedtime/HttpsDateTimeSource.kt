package com.xingzhi.trustedtime

import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads an RFC 7231 Date header over platform-validated TLS. */
class HttpsDateTimeSource(
    uri: URI,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 5_000,
) : TimeSource {
    private val endpoint = uri.also {
        require(it.scheme.equals("https", ignoreCase = true)) { "Trusted time endpoint must use HTTPS." }
        require(!it.host.isNullOrBlank()) { "Trusted time endpoint must have a host." }
        require(it.userInfo == null) { "Trusted time endpoint must not contain user info." }
    }.toURL()

    override suspend fun now(): Instant = withContext(dispatcher) {
        val connection = endpoint.openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Range", "bytes=0-0")
            connection.responseCode
            val date = requireNotNull(connection.getHeaderField("Date")) {
                "HTTPS time response has no Date header."
            }
            ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } finally {
            connection.disconnect()
        }
    }
}
