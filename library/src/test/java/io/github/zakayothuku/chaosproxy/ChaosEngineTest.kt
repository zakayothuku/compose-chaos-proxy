package io.github.zakayothuku.chaosproxy

import io.github.zakayothuku.chaosproxy.engine.ChaosEngine
import io.github.zakayothuku.chaosproxy.engine.ChaosExecutionResult
import io.github.zakayothuku.chaosproxy.engine.ChaosRule
import io.github.zakayothuku.chaosproxy.repository.ChaosConfigRepository
import okhttp3.Request
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class ChaosEngineTest {

    private lateinit var engine: ChaosEngine

    @Before
    fun setup() {
        ChaosConfigRepository.clearAll()
        engine = ChaosEngine(random = Random(42))
    }

    @After
    fun teardown() {
        ChaosConfigRepository.clearAll()
    }

    @Test
    fun `test evaluate returns proceed when global chaos is disabled`() {
        val rule = ChaosRule(
            name = "Test Rule",
            urlPattern = ".*",
            injectedStatusCode = 500
        )
        ChaosConfigRepository.addRule(rule)
        ChaosConfigRepository.setGlobalEnabled(false)

        val request = Request.Builder().url("https://api.example.com/users").build()
        val result = engine.evaluate(request)

        assertTrue(result is ChaosExecutionResult.Proceed)
        assertEquals(0L, (result as ChaosExecutionResult.Proceed).delayMs)
    }

    @Test
    fun `test evaluate injects HTTP 503 error when rule matches and global enabled`() {
        val rule = ChaosRule(
            name = "503 Outage",
            urlPattern = ".*\\/users",
            injectedStatusCode = 503,
            failureProbabilityPercent = 100
        )
        ChaosConfigRepository.addRule(rule)
        ChaosConfigRepository.setGlobalEnabled(true)

        val request = Request.Builder().url("https://api.example.com/users").build()
        val result = engine.evaluate(request)

        assertTrue(result is ChaosExecutionResult.InjectedResponse)
        val injected = result as ChaosExecutionResult.InjectedResponse
        assertEquals(503, injected.response.code)
        assertEquals("true", injected.response.header("X-Chaos-Injected"))
    }

    @Test
    fun `test evaluate drops connection when dropConnection is true`() {
        val rule = ChaosRule(
            name = "Drop Rule",
            urlPattern = ".*",
            dropConnection = true,
            failureProbabilityPercent = 100
        )
        ChaosConfigRepository.addRule(rule)
        ChaosConfigRepository.setGlobalEnabled(true)

        val request = Request.Builder().url("https://api.example.com/test").build()
        val result = engine.evaluate(request)

        assertTrue(result is ChaosExecutionResult.DropConnection)
    }

    @Test
    fun `test rule does not match different URL regex`() {
        val rule = ChaosRule(
            name = "Auth Only",
            urlPattern = ".*\\/auth\\/.*",
            injectedStatusCode = 401
        )
        ChaosConfigRepository.addRule(rule)
        ChaosConfigRepository.setGlobalEnabled(true)

        val request = Request.Builder().url("https://api.example.com/public/feed").build()
        val result = engine.evaluate(request)

        assertTrue(result is ChaosExecutionResult.Proceed)
    }

    @Test
    fun `test failureProbabilityPercent of 0 always bypasses regardless of random roll`() {
        val rule = ChaosRule(
            name = "Never Trigger",
            urlPattern = ".*",
            injectedStatusCode = 500,
            failureProbabilityPercent = 0
        )
        ChaosConfigRepository.addRule(rule)
        ChaosConfigRepository.setGlobalEnabled(true)

        val request = Request.Builder().url("https://api.example.com/anything").build()

        // Random.nextInt(1, 101) always yields a roll in [1, 100]; with a 0% probability
        // that roll must always exceed the threshold, so the rule should never fire across
        // many different seeds.
        repeat(20) { seed ->
            val result = ChaosEngine(random = Random(seed.toLong())).evaluate(request)
            assertTrue(result is ChaosExecutionResult.Proceed)
        }
    }

    @Test
    fun `test failureProbabilityPercent of 100 always triggers regardless of random roll`() {
        val rule = ChaosRule(
            name = "Always Trigger",
            urlPattern = ".*",
            injectedStatusCode = 500,
            failureProbabilityPercent = 100
        )
        ChaosConfigRepository.addRule(rule)
        ChaosConfigRepository.setGlobalEnabled(true)

        val request = Request.Builder().url("https://api.example.com/anything").build()

        repeat(20) { seed ->
            val result = ChaosEngine(random = Random(seed.toLong())).evaluate(request)
            assertTrue(result is ChaosExecutionResult.InjectedResponse)
        }
    }

    @Test
    fun `test first matching enabled rule wins when multiple rules match`() {
        val disabledRule = ChaosRule(name = "Disabled", urlPattern = ".*", enabled = false, injectedStatusCode = 400)
        val firstEnabledRule = ChaosRule(name = "First", urlPattern = ".*", injectedStatusCode = 401, failureProbabilityPercent = 100)
        val secondEnabledRule = ChaosRule(name = "Second", urlPattern = ".*", injectedStatusCode = 500, failureProbabilityPercent = 100)
        ChaosConfigRepository.addRule(disabledRule)
        ChaosConfigRepository.addRule(firstEnabledRule)
        ChaosConfigRepository.addRule(secondEnabledRule)
        ChaosConfigRepository.setGlobalEnabled(true)

        val request = Request.Builder().url("https://api.example.com/anything").build()
        val result = engine.evaluate(request)

        assertTrue(result is ChaosExecutionResult.InjectedResponse)
        assertEquals(401, (result as ChaosExecutionResult.InjectedResponse).response.code)
    }
}
