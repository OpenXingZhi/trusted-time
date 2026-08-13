package com.xingzhi.trustedtime

import java.time.Duration
import java.time.Instant
import kotlin.math.absoluteValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Supplies rollback-resistant time from a short-lived authenticated anchor.
 *
 * Cached time advances only with the platform monotonic clock. A reboot invalidates the anchor,
 * forcing an authenticated refresh instead of falling back to the editable wall clock.
 */
class TrustedClock(
    private val sources: List<TimeSource>,
    private val store: TimeAnchorStore,
    private val deviceTime: DeviceTime,
    private val maxAnchorAge: Duration = Duration.ofDays(1),
    private val maxSourceSkew: Duration = Duration.ofSeconds(10),
    private val minimumSourceAgreement: Int = 2,
) {
    private val mutex = Mutex()

    init {
        require(sources.isNotEmpty()) { "At least one trusted time source is required." }
        require(minimumSourceAgreement in 1..sources.size) {
            "minimumSourceAgreement must be between 1 and the number of sources."
        }
        require(!maxAnchorAge.isNegative && !maxAnchorAge.isZero)
        require(!maxSourceSkew.isNegative)
    }

    /** Returns a valid cached reading or refreshes it from authenticated sources. */
    suspend fun now(): Result<TrustedTime> = mutex.withLock {
        runCatching {
            cachedNow(store.load()) ?: refreshInternal()
        }
    }

    /** Forces remote synchronization and atomically replaces the persisted anchor. */
    suspend fun refresh(): Result<TrustedTime> = mutex.withLock {
        runCatching { refreshInternal() }
    }

    private fun cachedNow(anchor: TimeAnchor?): TrustedTime? {
        if (anchor == null || anchor.bootId != deviceTime.bootId()) return null
        val elapsed = deviceTime.monotonic() - anchor.monotonicMark
        if (elapsed.isNegative || elapsed > maxAnchorAge) return null
        return TrustedTime(anchor.trustedInstant.plus(elapsed))
    }

    private suspend fun refreshInternal(): TrustedTime {
        val before = deviceTime.monotonic()
        val successful = coroutineScope {
            sources.map { source -> async { runCatching { source.now() } } }
                .awaitAll()
                .mapNotNull(Result<Instant>::getOrNull)
        }
        if (successful.size < minimumSourceAgreement) {
            throw TrustedTimeException.SourceFailed(
                IllegalStateException("Only ${successful.size} trusted time source(s) succeeded."),
            )
        }

        val consensus = largestConsensus(successful)
        if (consensus.size < minimumSourceAgreement) throw TrustedTimeException.SourcesDisagree
        val after = deviceTime.monotonic()
        if (after < before) throw TrustedTimeException.RefreshRequired
        val trustedInstant = consensus.sorted()[consensus.size / 2]
            .plus((after - before).dividedBy(2))
        val anchor = TimeAnchor(
            trustedInstant = trustedInstant,
            bootId = deviceTime.bootId(),
            monotonicMark = after,
        )
        store.save(anchor)
        return TrustedTime(trustedInstant)
    }

    private fun largestConsensus(readings: List<Instant>): List<Instant> = readings
        .map { candidate ->
            readings.filter { reading ->
                Duration.between(candidate, reading).toMillis().absoluteValue <= maxSourceSkew.toMillis()
            }
        }
        .maxByOrNull(List<Instant>::size)
        .orEmpty()
}
