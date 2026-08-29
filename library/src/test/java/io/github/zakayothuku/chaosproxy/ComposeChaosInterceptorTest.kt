package io.github.zakayothuku.chaosproxy

import io.github.zakayothuku.chaosproxy.engine.ChaosRule
import io.github.zakayothuku.chaosproxy.repository.ChaosConfigRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException

class ComposeChaosInterceptorTest {

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
    fun `test interceptor proceeds normally when chaos is disabled`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status": "ok"}""")
        )

        ChaosConfigRepository.setGlobalEnabled(false)

        val request = Request.Builder().url(mockWebServer.url("/normal")).build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals("""{"status": "ok"}""", response.body?.string())
    }

    @Test
    fun `test interceptor returns synthetic HTTP 429 when rule matches`() {
        val rule = ChaosRule(
            name = "Rate Limit Rule",
            urlPattern = ".*",
            injectedStatusCode = 429,
            customResponseBody = """{"error": "Too Many Requests"}""",
            failureProbabilityPercent = 100
        )
        ChaosConfigRepository.addRule(rule)
        ChaosConfigRepository.setGlobalEnabled(true)

        val request = Request.Builder().url(mockWebServer.url("/data")).build()
        val response = client.newCall(request).execute()

        assertEquals(429, response.code)
        assertEquals("""{"error": "Too Many Requests"}""", response.body?.string())
        assertEquals("true", response.header("X-Chaos-Injected"))
    }

    @Test(expected = SocketTimeoutException::class)
    fun `test interceptor throws SocketTimeoutException when dropConnection is true`() {
        val rule = ChaosRule(
            name = "Drop Connection",
            urlPattern = ".*",
            dropConnection = true,
            failureProbabilityPercent = 100
        )
        ChaosConfigRepository.addRule(rule)
        ChaosConfigRepository.setGlobalEnabled(true)

        val request = Request.Builder().url(mockWebServer.url("/timeout")).build()
        client.newCall(request).execute()
    }
}
