package io.github.zakayothuku.chaosproxy

import io.github.zakayothuku.chaosproxy.engine.ChaosRule
import io.github.zakayothuku.chaosproxy.model.ChaosActionType
import io.github.zakayothuku.chaosproxy.repository.ChaosConfigRepository
import io.github.zakayothuku.chaosproxy.repository.ChaosPresetType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * End-to-end simulation of ComposeChaosProxy against a real [MockWebServer], driven through the
 * public API exactly as a consuming app would use it (OkHttpClient + ComposeChaosInterceptor +
 * ChaosConfigRepository). Unlike the narrower unit tests elsewhere, each scenario here:
 *
 *  1. Fires a batch of real HTTP calls through the interceptor.
 *  2. Independently measures what actually happened (status code, latency, dropped connection).
 *  3. Cross-checks that against the events ChaosConfigRepository itself logged.
 *  4. Records a human-readable outcome into [ChaosSimulationReport], written to
 *     `build/reports/chaos-simulation/chaos-simulation-report.md` once the whole suite finishes,
 *     so there is a durable, reviewable artifact confirming the proxy behaved as expected for
 *     every preset/scenario — not just a green checkmark in CI.
 */
class ChaosProxySimulationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        ChaosConfigRepository.clearAll()
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client = OkHttpClient.Builder()
            .addInterceptor(ComposeChaosInterceptor())
            .build()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
        ChaosConfigRepository.clearAll()
    }

    @Test
    fun `simulate baseline traffic with chaos disabled`() {
        val trials = 5
        ChaosConfigRepository.setGlobalEnabled(false)
        repeat(trials) {
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        }

        val outcomes = (1..trials).map { execute("/baseline") }

        assertTrue(
            "expected every request to pass through untouched",
            outcomes.all { it.code == 200 && !it.chaosInjected && !it.droppedConnection }
        )
        assertEquals(
            "chaos disabled must not log any events",
            0,
            ChaosConfigRepository.state.value.events.size
        )

        ChaosSimulationReport.record(
            scenario = "Baseline (chaos disabled)",
            description = "Sanity check that with global chaos disabled, all requests pass through unmodified.",
            outcomes = outcomes,
            passed = true,
            notes = "All $trials requests returned HTTP 200 with no chaos header and no logged events."
        )
    }

    @Test
    fun `simulate Auth Expired 401 preset`() {
        val trials = 5
        ChaosConfigRepository.applyPreset(ChaosPresetType.AUTH_401)

        // Status-code injection short-circuits before chain.proceed(), so nothing is ever
        // dispatched to the mock server — no need to enqueue responses.
        val outcomes = (1..trials).map { execute("/secure/profile") }

        assertTrue(
            "expected every request to receive an injected 401",
            outcomes.all { it.code == 401 && it.chaosInjected && !it.droppedConnection }
        )
        val events = ChaosConfigRepository.state.value.events
        assertEquals(trials, events.size)
        assertTrue(events.all { it.actionType == ChaosActionType.HTTP_ERROR_INJECTED && it.statusCodeReturned == 401 })

        ChaosSimulationReport.record(
            scenario = "Preset: Auth Expired (401)",
            description = "Every request should receive a synthetic 401 with the configured latency, and a matching event should be logged for each.",
            outcomes = outcomes,
            passed = true,
            notes = "All $trials requests returned HTTP 401 (avg ${outcomes.averageDelayMs()}ms); ${events.size} matching events logged."
        )
    }

    @Test
    fun `simulate Server Outage 503 preset`() {
        val trials = 5
        ChaosConfigRepository.applyPreset(ChaosPresetType.SERVER_503)

        val outcomes = (1..trials).map { execute("/orders") }

        assertTrue(
            "expected every request to receive an injected 503",
            outcomes.all { it.code == 503 && it.chaosInjected && !it.droppedConnection }
        )
        val events = ChaosConfigRepository.state.value.events
        assertEquals(trials, events.size)
        assertTrue(events.all { it.actionType == ChaosActionType.HTTP_ERROR_INJECTED && it.statusCodeReturned == 503 })

        ChaosSimulationReport.record(
            scenario = "Preset: Server Outage (503)",
            description = "Every request should receive a synthetic 503 with the configured latency, and a matching event should be logged for each.",
            outcomes = outcomes,
            passed = true,
            notes = "All $trials requests returned HTTP 503 (avg ${outcomes.averageDelayMs()}ms); ${events.size} matching events logged."
        )
    }

    @Test
    fun `simulate Rate Limited 429 preset`() {
        val trials = 5
        ChaosConfigRepository.applyPreset(ChaosPresetType.RATE_LIMIT_429)

        val outcomes = (1..trials).map { execute("/checkout") }

        assertTrue(
            "expected every request to receive an injected 429",
            outcomes.all { it.code == 429 && it.chaosInjected && !it.droppedConnection }
        )
        val events = ChaosConfigRepository.state.value.events
        assertEquals(trials, events.size)
        assertTrue(events.all { it.actionType == ChaosActionType.HTTP_ERROR_INJECTED && it.statusCodeReturned == 429 })

        ChaosSimulationReport.record(
            scenario = "Preset: Rate Limited (429)",
            description = "Every request should receive a synthetic 429 with the configured latency, and a matching event should be logged for each.",
            outcomes = outcomes,
            passed = true,
            notes = "All $trials requests returned HTTP 429 (avg ${outcomes.averageDelayMs()}ms); ${events.size} matching events logged."
        )
    }

    @Test
    fun `simulate Offline Mode preset drops every connection`() {
        val trials = 5
        ChaosConfigRepository.applyPreset(ChaosPresetType.OFFLINE_MODE)

        val outcomes = (1..trials).map { execute("/sync") }

        assertTrue(
            "expected every request to be dropped with a timeout",
            outcomes.all { it.droppedConnection && it.exceptionType == "SocketTimeoutException" }
        )
        val events = ChaosConfigRepository.state.value.events
        assertEquals(trials, events.size)
        assertTrue(events.all { it.actionType == ChaosActionType.CONNECTION_DROPPED })

        ChaosSimulationReport.record(
            scenario = "Preset: Offline Mode",
            description = "Every request should throw SocketTimeoutException, simulating zero network connectivity.",
            outcomes = outcomes,
            passed = true,
            notes = "All $trials requests dropped with SocketTimeoutException (avg ${outcomes.averageDelayMs()}ms); ${events.size} matching events logged."
        )
    }

    @Test
    fun `simulate flaky network statistically drops roughly the configured percentage`() {
        // Mirrors the shape of the "Flaky 3G" preset (25% drop probability) but with much
        // smaller delay bounds so the statistical simulation runs quickly in CI rather than
        // waiting out multiple seconds of real latency per trial.
        val trials = 200
        val configuredDropPercent = 25
        ChaosConfigRepository.addRule(
            ChaosRule(
                name = "Flaky Network Simulation",
                urlPattern = ".*",
                minDelayMs = 2,
                maxDelayMs = 8,
                dropConnection = true,
                failureProbabilityPercent = configuredDropPercent
            )
        )
        ChaosConfigRepository.setGlobalEnabled(true)
        // Bypassed requests proceed to the real network; pre-enqueue enough 200s to cover them.
        repeat(trials) {
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        }

        val outcomes = (1..trials).map { execute("/flaky") }

        val droppedCount = outcomes.count { it.droppedConnection }
        val successCount = outcomes.count { it.code == 200 }
        assertEquals("every trial must either succeed or be dropped", trials, droppedCount + successCount)

        val observedDropRate = droppedCount.toDouble() / trials
        // Wide tolerance band (10%-45% for a configured 25%) to avoid CI flakiness while still
        // catching a badly broken probability roll (e.g. always/never dropping).
        val withinTolerance = observedDropRate in 0.10..0.45
        assertTrue(
            "expected roughly $configuredDropPercent% drop rate, observed ${(observedDropRate * 100).toInt()}%",
            withinTolerance
        )

        ChaosSimulationReport.record(
            scenario = "Statistical: Flaky network (~$configuredDropPercent% drop)",
            description = "Over $trials trials, roughly $configuredDropPercent% of requests should be dropped and the rest should proceed to the real server.",
            outcomes = outcomes,
            passed = withinTolerance,
            notes = "Observed drop rate: ${"%.1f".format(observedDropRate * 100)}% ($droppedCount/$trials dropped, $successCount/$trials succeeded)."
        )
    }

    private fun execute(path: String): RequestOutcome {
        val start = System.nanoTime()
        return try {
            val request = Request.Builder().url(mockWebServer.url(path)).build()
            client.newCall(request).execute().use { response ->
                RequestOutcome(
                    code = response.code,
                    chaosInjected = response.header("X-Chaos-Injected") == "true",
                    durationMs = (System.nanoTime() - start) / 1_000_000
                )
            }
        } catch (e: IOException) {
            RequestOutcome(
                durationMs = (System.nanoTime() - start) / 1_000_000,
                droppedConnection = true,
                exceptionType = e::class.simpleName
            )
        }
    }

    companion object {
        @JvmStatic
        @AfterClass
        fun writeReport() {
            ChaosSimulationReport.writeToDisk()
        }
    }
}

internal data class RequestOutcome(
    val code: Int? = null,
    val chaosInjected: Boolean = false,
    val durationMs: Long,
    val droppedConnection: Boolean = false,
    val exceptionType: String? = null
)

internal fun List<RequestOutcome>.averageDelayMs(): Long =
    if (isEmpty()) 0 else map { it.durationMs }.average().toLong()

/**
 * Accumulates [ScenarioOutcome] records across the whole simulation test class and, once all
 * tests have run, writes them as a Markdown report to
 * `library/build/reports/chaos-simulation/chaos-simulation-report.md`. Kept intentionally
 * dependency-free (no extra reporting library) since this is a small, self-contained artifact.
 */
internal object ChaosSimulationReport {

    private data class ScenarioOutcome(
        val scenario: String,
        val description: String,
        val requestCount: Int,
        val passed: Boolean,
        val notes: String
    )

    private val scenarios = mutableListOf<ScenarioOutcome>()

    @Synchronized
    fun record(scenario: String, description: String, outcomes: List<RequestOutcome>, passed: Boolean, notes: String) {
        scenarios += ScenarioOutcome(scenario, description, outcomes.size, passed, notes)
    }

    @Synchronized
    fun writeToDisk() {
        val markdown = buildString {
            appendLine("# Chaos Proxy Simulation Report")
            appendLine()
            appendLine("Generated by `ChaosProxySimulationTest` — an end-to-end run of every chaos preset")
            appendLine("and a statistical flaky-network scenario against a real MockWebServer.")
            appendLine()
            appendLine("| # | Scenario | Requests | Result | Notes |")
            appendLine("|---|----------|----------|--------|-------|")
            scenarios.forEachIndexed { index, outcome ->
                val result = if (outcome.passed) "✅ PASS" else "❌ FAIL"
                appendLine("| ${index + 1} | ${outcome.scenario} | ${outcome.requestCount} | $result | ${outcome.notes} |")
            }
            appendLine()
            appendLine("## Scenario details")
            scenarios.forEach { outcome ->
                appendLine()
                appendLine("### ${outcome.scenario}")
                appendLine(outcome.description)
            }
        }

        val reportDir = File("build/reports/chaos-simulation").apply { mkdirs() }
        File(reportDir, "chaos-simulation-report.md").writeText(markdown)
        println(markdown)
    }
}
