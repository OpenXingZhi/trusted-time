# XingZhi Trusted Time

Rollback-resistant trusted time for JVM and Android applications.

```kotlin
val clock = TrustedClock(
    sources = listOf(
        HttpsDateTimeSource(URI("https://www.baidu.com/")),
        HttpsDateTimeSource(URI("https://www.qq.com/")),
        HttpsDateTimeSource(URI("https://www.aliyun.com/")),
    ),
    store = applicationTimeAnchorStore,
    deviceTime = androidDeviceTime,
)

val trustedNow = clock.now().getOrThrow().instant
```

The module requires agreement from independent TLS-authenticated sources, stores a trusted anchor, and advances it with a monotonic clock. Cached anchors are rejected after reboot, monotonic rollback, or expiry. It never treats the user-editable wall clock as trusted time.

Applications provide small storage and platform-clock adapters. The library contains no Android, database, DI, or license-policy dependency.
