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
}
