package io.github.zakayothuku.chaosproxy.repository

import io.github.zakayothuku.chaosproxy.engine.ChaosRule

enum class ChaosPresetType {
    NONE,
    FLAKY_3G,
    AUTH_401,
    SERVER_503,
    RATE_LIMIT_429,
    OFFLINE_MODE
}

data class ChaosPreset(
    val type: ChaosPresetType,
    val title: String,
    val description: String,
    val rules: List<ChaosRule>
)

object DefaultChaosPresets {

    val PRESET_FLAKY_3G = ChaosPreset(
        type = ChaosPresetType.FLAKY_3G,
        title = "Flaky 3G Network",
        description = "1500ms–3500ms latency with 25% random connection drops.",
        rules = listOf(
            ChaosRule(
                name = "3G Latency & Flakiness",
                urlPattern = ".*",
                minDelayMs = 1500,
                maxDelayMs = 3500,
                dropConnection = true,
                failureProbabilityPercent = 25
            )
        )
    )

    val PRESET_AUTH_401 = ChaosPreset(
        type = ChaosPresetType.AUTH_401,
        title = "Auth Expired (401)",
        description = "Injects HTTP 401 Unauthorized to test token refresh & logout flows.",
        rules = listOf(
            ChaosRule(
                name = "JWT Token Expired",
                urlPattern = ".*",
                minDelayMs = 200,
                maxDelayMs = 500,
                injectedStatusCode = 401,
                customResponseBody = """{"error": "Unauthorized", "message": "Access token has expired or is invalid."}""",
                failureProbabilityPercent = 100
            )
        )
    )

    val PRESET_SERVER_503 = ChaosPreset(
        type = ChaosPresetType.SERVER_503,
        title = "Server Outage (503)",
        description = "Simulates backend maintenance outage with HTTP 503.",
        rules = listOf(
            ChaosRule(
                name = "Service Unavailable Outage",
                urlPattern = ".*",
                minDelayMs = 300,
                maxDelayMs = 600,
                injectedStatusCode = 503,
                customResponseBody = """{"error": "Service Unavailable", "message": "Backend server is down for scheduled maintenance."}""",
                failureProbabilityPercent = 100
            )
        )
    )

    val PRESET_RATE_LIMIT_429 = ChaosPreset(
        type = ChaosPresetType.RATE_LIMIT_429,
        title = "Rate Limited (429)",
        description = "Injects HTTP 429 Too Many Requests to test retry/backoff logic.",
        rules = listOf(
            ChaosRule(
                name = "API Rate Limit Exceeded",
                urlPattern = ".*",
                minDelayMs = 100,
                maxDelayMs = 300,
                injectedStatusCode = 429,
                customResponseBody = """{"error": "Too Many Requests", "retry_after": 30}""",
                failureProbabilityPercent = 100
            )
        )
    )

    val PRESET_OFFLINE_MODE = ChaosPreset(
        type = ChaosPresetType.OFFLINE_MODE,
        title = "Offline Mode",
        description = "Simulates zero network connectivity by dropping 100% of requests.",
        rules = listOf(
            ChaosRule(
                name = "Complete Network Blackout",
                urlPattern = ".*",
                minDelayMs = 50,
                maxDelayMs = 100,
                dropConnection = true,
                failureProbabilityPercent = 100
            )
        )
    )

    val ALL_PRESETS = listOf(
        PRESET_FLAKY_3G,
        PRESET_AUTH_401,
        PRESET_SERVER_503,
        PRESET_RATE_LIMIT_429,
        PRESET_OFFLINE_MODE
    )
}
