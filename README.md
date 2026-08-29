# 🌪️ compose-chaos-proxy

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-purple.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-MinSDK%2024-green.svg)](https://developer.android.com)

> **On-Device Network & Latency Chaos Simulator with Jetpack Compose Debug UI Overlay for Android.**

`compose-chaos-proxy` is a developer tool and OkHttp interceptor that allows mobile developers, QA engineers, and automated tests to inject **network latency, synthetic HTTP status codes (401, 403, 404, 429, 500, 503), dropped connections, and timeouts** directly on-device without configuring desktop proxy software (Charles, Proxyman, mitmproxy).

<p align="center">
  <img src="docs/chaos_proxy_preview.jpg" alt="compose-chaos-proxy UI Preview" width="360" />
</p>

---

## ✨ Features

- ⏱️ **Configurable Latency Injection**: Add artificial delay (min/max ms with random jitter) to simulate 2G/3G/4G network conditions on specific URL patterns.
- 🚨 **Synthetic HTTP Status Codes**: Intercept requests and return mock HTTP 401 (Auth Expired), 429 (Rate Limited), 500 (Internal Error), or 503 (Service Outage) with custom JSON error bodies.
- 🔌 **Connection Dropper**: Simulate `SocketTimeoutException` and offline mode to test app retry and recovery mechanisms.
- 🎛️ **Quick Presets**: 1-tap presets for *Flaky 3G Network*, *Auth Expired (401)*, *Server Outage (503)*, *Rate Limited (429)*, and *Offline Mode*.
- 📱 **Jetpack Compose Floating Overlay**: Draggable floating badge and expandable Material 3 bottom-sheet with master kill switch, custom rule builder, and live intercepted event logs.
- ⚡ **Zero-Config OkHttp Interceptor**: Drop `ComposeChaosInterceptor()` into your `OkHttpClient`.

---

## 📦 Installation

Add JitPack to your `settings.gradle.kts`:

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}
```

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.zakayothuku:compose-chaos-proxy:v1.0.0")
}
```

---

## 🚀 Quickstart

### 1. Attach OkHttp Interceptor

```kotlin
val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(ComposeChaosInterceptor())
    .build()
```

### 2. Attach Floating Compose UI Overlay

Add `ComposeChaosOverlay()` inside your top-level Jetpack Compose `Surface` or `Scaffold`:

```kotlin
@Composable
fun MainScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        YourAppContent()

        // Attach floating Chaos Proxy debug badge & bottom sheet
        ComposeChaosOverlay()
    }
}
```

---

## 🛠️ Programmatic Rule Configuration (for UI & Automation Tests)

You can also configure chaos rules programmatically in automated Espresso / UI tests:

```kotlin
// Inject 2000ms delay on all authentication routes
ChaosConfigRepository.addRule(
    ChaosRule(
        name = "Auth Latency",
        urlPattern = ".*/api/v1/auth/.*",
        minDelayMs = 2000,
        maxDelayMs = 3000,
        failureProbabilityPercent = 100
    )
)

// Activate built-in Flaky 3G preset
ChaosConfigRepository.applyPreset(ChaosPresetType.FLAKY_3G)

// Master kill-switch
ComposeChaosInterceptor.setEnabled(false)
```

---

## ⚠️ Production Safety

`compose-chaos-proxy` is a **developer/QA tool**. The library itself does **not** know
whether it has been added to a debug or release build — it publishes a single `release`
Maven variant, so it cannot reliably self-detect your app's build type. **You are
responsible for gating both integration points behind a debug-only check in your own app
code**, for example:

```kotlin
val okHttpClient = OkHttpClient.Builder()
    .apply {
        if (BuildConfig.DEBUG) {
            addInterceptor(ComposeChaosInterceptor())
        }
    }
    .build()

@Composable
fun MainScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        YourAppContent()

        if (BuildConfig.DEBUG) {
            ComposeChaosOverlay()
        }
    }
}
```

Never add `ComposeChaosInterceptor()` or `ComposeChaosOverlay()` unconditionally — doing so
risks shipping a build where real network traffic can be delayed/corrupted, and where the
floating overlay exposes a live network-chaos control surface to end users. See
`app/src/main/java/io/github/zakayothuku/chaosproxy/sample/MainActivity.kt` for a reference
implementation. Note `buildFeatures.buildConfig = true` must be enabled in your app module
for `BuildConfig.DEBUG` to be available.

As an extra safety net, chaos rules are also inert by default: `ChaosConfigRepository`'s
`globalEnabled` flag defaults to `false`, and `ComposeChaosInterceptor.setEnabled(false)` acts
as a master kill-switch you can wire into a remote config / feature flag for defense in depth.

---

## 🧪 Testing

Run library unit tests:

```bash
./gradlew :library:test
```

Build sample application:

```bash
./gradlew :app:assembleDebug
```

### End-to-end simulation & recorded outcomes

Beyond isolated unit tests, [`ChaosProxySimulationTest`](library/src/test/java/io/github/zakayothuku/chaosproxy/ChaosProxySimulationTest.kt)
drives the real public API (`OkHttpClient` + `ComposeChaosInterceptor` + `ChaosConfigRepository`)
against a live `MockWebServer` for every built-in preset, plus a statistical flaky-network
scenario, to prove chaos injection actually behaves as configured end-to-end rather than just
in isolation:

- **Baseline** (chaos disabled) — requests pass through untouched, no events logged.
- **Auth Expired (401)**, **Server Outage (503)**, **Rate Limited (429)** presets — every
  request receives the expected synthetic status code + `X-Chaos-Injected` header, with a
  matching event logged per request.
- **Offline Mode** preset — every request throws `SocketTimeoutException`.
- **Statistical flaky-network scenario** — a scaled-down analogue of the Flaky 3G preset (same
  25% drop probability) run over 200 trials to verify the probability roll behaves as
  configured, within a wide tolerance band to avoid CI flakiness.

Each scenario independently measures the *real* observed outcome (status code, latency, thrown
exception) and cross-checks it against what `ChaosConfigRepository` actually logged, rather than
trusting only the engine's return value.

Run it directly with:

```bash
./gradlew :library:testDebugUnitTest --tests "*ChaosProxySimulationTest"
```

Every run writes a recorded, human-readable report to
`library/build/reports/chaos-simulation/chaos-simulation-report.md`, so results are reviewable
as a durable artifact rather than just a pass/fail in CI. Example recording from a local run:

```
| # | Scenario | Requests | Result | Notes |
|---|----------|----------|--------|-------|
| 1 | Baseline (chaos disabled) | 5 | ✅ PASS | All 5 requests returned HTTP 200 with no chaos header and no logged events. |
| 2 | Preset: Offline Mode | 5 | ✅ PASS | All 5 requests dropped with SocketTimeoutException (avg 86ms); 5 matching events logged. |
| 3 | Preset: Rate Limited (429) | 5 | ✅ PASS | All 5 requests returned HTTP 429 (avg 281ms); 5 matching events logged. |
| 4 | Preset: Auth Expired (401) | 5 | ✅ PASS | All 5 requests returned HTTP 401 (avg 369ms); 5 matching events logged. |
| 5 | Preset: Server Outage (503) | 5 | ✅ PASS | All 5 requests returned HTTP 503 (avg 367ms); 5 matching events logged. |
| 6 | Statistical: Flaky network (~25% drop) | 200 | ✅ PASS | Observed drop rate: 30.0% (60/200 dropped, 140/200 succeeded). |
```

---

## 📄 License & Author

Developed & maintained by **Zakayo Thuku** ([@zakayothuku](https://github.com/zakayothuku)).

```
MIT License - Copyright (c) 2026 Zakayo Thuku
```
