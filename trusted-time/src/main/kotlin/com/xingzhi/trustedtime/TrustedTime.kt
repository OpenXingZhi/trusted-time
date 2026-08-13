package com.xingzhi.trustedtime

import java.time.Duration
import java.time.Instant

/** A time reading whose origin has been authenticated independently of the device wall clock. */
data class TrustedTime(val instant: Instant)

/** Remote authenticated time provider, normally backed by HTTPS with platform TLS validation. */
fun interface TimeSource {
    suspend fun now(): Instant
}

/** Small application seam implemented by [TrustedClock] and test adapters. */
interface TrustedTimeProvider {
    suspend fun now(): Result<TrustedTime>
    suspend fun refresh(): Result<TrustedTime>
}

sealed interface TrustedTimeStatus {
    data object Idle : TrustedTimeStatus
    data object Synchronizing : TrustedTimeStatus
    data class Available(
        val time: TrustedTime,
        val fromCache: Boolean,
        val agreeingSources: Int,
    ) : TrustedTimeStatus
    data class Unavailable(val error: Throwable) : TrustedTimeStatus
}

/** Persists an anchor so refresh policy survives process restarts. */
interface TimeAnchorStore {
    suspend fun load(): TimeAnchor?
    suspend fun save(anchor: TimeAnchor)
}

data class TimeAnchor(
    val trustedInstant: Instant,
    val bootId: String,
    val monotonicMark: Duration,
)

/** Platform facts required to detect reboot and advance an anchor without trusting wall-clock edits. */
interface DeviceTime {
    fun monotonic(): Duration
    fun bootId(): String
}

sealed class TrustedTimeException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object Unavailable : TrustedTimeException("Trusted time is unavailable.")
    data object RefreshRequired : TrustedTimeException("Trusted time must be refreshed after reboot or expiry.")
    class SourceFailed(cause: Throwable) : TrustedTimeException("All trusted time sources failed.", cause)
    data object SourcesDisagree : TrustedTimeException("Trusted time sources disagree.")
}
