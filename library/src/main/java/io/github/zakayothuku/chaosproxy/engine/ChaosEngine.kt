package io.github.zakayothuku.chaosproxy.engine

import io.github.zakayothuku.chaosproxy.model.ChaosActionType
import io.github.zakayothuku.chaosproxy.model.ChaosEvent
import io.github.zakayothuku.chaosproxy.repository.ChaosConfigRepository
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.random.Random

sealed class ChaosExecutionResult {
    data class Proceed(val delayMs: Long) : ChaosExecutionResult()
    data class InjectedResponse(val response: Response, val delayMs: Long) : ChaosExecutionResult()
    data class DropConnection(val delayMs: Long, val exception: IOException) : ChaosExecutionResult()
}

class ChaosEngine(
    private val random: Random = Random.Default
) {

    @Throws(IOException::class)
    fun evaluate(request: Request): ChaosExecutionResult {
        val config = ChaosConfigRepository.state.value
        val url = request.url.toString()

        if (!config.globalEnabled || config.rules.isEmpty()) {
            return ChaosExecutionResult.Proceed(delayMs = 0)
        }

        // Find first matching enabled rule
        val matchingRule = config.rules.firstOrNull { it.enabled && it.matchesUrl(url) }
            ?: return ChaosExecutionResult.Proceed(delayMs = 0)

        // Check failure probability
        val roll = random.nextInt(1, 101)
        if (roll > matchingRule.failureProbabilityPercent) {
            ChaosConfigRepository.logEvent(
                ChaosEvent(
                    requestUrl = url,
                    method = request.method,
                    actionType = ChaosActionType.BYPASSED,
                    ruleNameApplied = matchingRule.name,
                    description = "Bypassed via ${matchingRule.failureProbabilityPercent}% probability roll."
                )
            )
            return ChaosExecutionResult.Proceed(delayMs = 0)
        }

        // Calculate delay
        val delayMs = calculateDelay(matchingRule.minDelayMs, matchingRule.maxDelayMs)

        // Apply drop connection if requested
        if (matchingRule.dropConnection) {
            val exception = SocketTimeoutException("Chaos Proxy simulated connection timeout for: $url")
            ChaosConfigRepository.logEvent(
                ChaosEvent(
                    requestUrl = url,
                    method = request.method,
                    actionType = ChaosActionType.CONNECTION_DROPPED,
                    ruleNameApplied = matchingRule.name,
                    delayAppliedMs = delayMs,
                    statusCodeReturned = 0,
                    description = "Dropped socket connection (Timeout) after ${delayMs}ms."
                )
            )
            return ChaosExecutionResult.DropConnection(delayMs, exception)
        }

        // Apply HTTP status code injection if requested
        if (matchingRule.injectedStatusCode != null && matchingRule.injectedStatusCode != 200) {
            val statusCode = matchingRule.injectedStatusCode
            val responseBodyString = matchingRule.customResponseBody
                ?: """{"error": "Chaos Injected Error", "status": $statusCode, "rule": "${matchingRule.name}"}"""

            val syntheticResponse = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message(getStatusMessage(statusCode))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-Chaos-Injected", "true")
                .body(responseBodyString.toResponseBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .build()

            ChaosConfigRepository.logEvent(
                ChaosEvent(
                    requestUrl = url,
                    method = request.method,
                    actionType = ChaosActionType.HTTP_ERROR_INJECTED,
                    ruleNameApplied = matchingRule.name,
                    delayAppliedMs = delayMs,
                    statusCodeReturned = statusCode,
                    description = "Injected HTTP $statusCode error with custom payload."
                )
            )
            return ChaosExecutionResult.InjectedResponse(syntheticResponse, delayMs)
        }

        // Only delay applied
        if (delayMs > 0) {
            ChaosConfigRepository.logEvent(
                ChaosEvent(
                    requestUrl = url,
                    method = request.method,
                    actionType = ChaosActionType.DELAYED,
                    ruleNameApplied = matchingRule.name,
                    delayAppliedMs = delayMs,
                    statusCodeReturned = 200,
                    description = "Injected ${delayMs}ms network latency."
                )
            )
        }

        return ChaosExecutionResult.Proceed(delayMs)
    }

    private fun calculateDelay(minMs: Long, maxMs: Long): Long {
        if (minMs <= 0 && maxMs <= 0) return 0
        if (maxMs <= minMs) return minMs
        return random.nextLong(minMs, maxMs + 1)
    }

    private fun getStatusMessage(code: Int): String {
        return when (code) {
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            504 -> "Gateway Timeout"
            else -> "Chaos Injected Error"
        }
    }
}
