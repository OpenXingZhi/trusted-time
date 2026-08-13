package com.xingzhi.trustedtime

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrustedClockTest {
    private val base = Instant.parse("2026-08-13T12:00:00Z")

    @Test
    fun `uses source consensus and advances with monotonic time`() = runTest {
        val device = FakeDeviceTime()
        val store = MemoryStore()
        val clock = clock(
            device = device,
            store = store,
            readings = listOf(base, base.plusSeconds(2), base.plusSeconds(300)),
        )

        assertEquals(base.plusSeconds(2), clock.now().getOrThrow().instant)
        device.elapsed += Duration.ofMinutes(5)

        assertEquals(base.plusSeconds(302), clock.now().getOrThrow().instant)
    }

    @Test
    fun `reboot invalidates cached anchor and refreshes`() = runTest {
        val device = FakeDeviceTime()
        val store = MemoryStore()
        val clock = clock(device, store, listOf(base, base, base))
        clock.now().getOrThrow()

        device.boot = "boot-2"
        device.elapsed = Duration.ZERO
        val refreshed = clock(device, store, listOf(base.plusSeconds(600), base.plusSeconds(601), base.plusSeconds(900)))

        assertEquals(base.plusSeconds(601), refreshed.now().getOrThrow().instant)
    }

    @Test
    fun `fails closed when sources do not agree`() = runTest {
        val result = clock(
            FakeDeviceTime(),
            MemoryStore(),
            listOf(base, base.plusSeconds(60), base.plusSeconds(120)),
        ).now()

        assertTrue(result.exceptionOrNull() is TrustedTimeException.SourcesDisagree)
    }

    @Test
    fun `expired anchor requires successful refresh`() = runTest {
        val device = FakeDeviceTime()
        val store = MemoryStore().apply {
            anchor = TimeAnchor(base, device.bootId(), Duration.ZERO)
        }
        device.elapsed = Duration.ofDays(2)
        val clock = TrustedClock(
            sources = listOf(TimeSource { error("offline") }, TimeSource { error("offline") }),
            store = store,
            deviceTime = device,
        )

        assertTrue(clock.now().exceptionOrNull() is TrustedTimeException.SourceFailed)
    }

    private fun clock(
        device: FakeDeviceTime,
        store: MemoryStore,
        readings: List<Instant>,
    ) = TrustedClock(
        sources = readings.map { reading -> TimeSource { reading } },
        store = store,
        deviceTime = device,
        minimumSourceAgreement = 2,
    )

    private class MemoryStore : TimeAnchorStore {
        var anchor: TimeAnchor? = null
        override suspend fun load() = anchor
        override suspend fun save(anchor: TimeAnchor) { this.anchor = anchor }
    }

    private class FakeDeviceTime : DeviceTime {
        var elapsed: Duration = Duration.ZERO
        var boot = "boot-1"
        override fun monotonic() = elapsed
        override fun bootId() = boot
    }
}
