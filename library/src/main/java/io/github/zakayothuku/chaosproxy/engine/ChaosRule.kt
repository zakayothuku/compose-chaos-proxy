package io.github.zakayothuku.chaosproxy.engine

import java.util.UUID

/**
 * Defines a network chaos rule applied to matching HTTP requests.
 */
data class ChaosRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val urlPattern: String = ".*", // Regex matching target URLs
    val minDelayMs: Long = 0,
    val maxDelayMs: Long = 0,
    val injectedStatusCode: Int? = null, // null means let original request proceed
    val customResponseBody: String? = null,
    val dropConnection: Boolean = false, // Throws SocketTimeoutException / ConnectionReset
    val failureProbabilityPercent: Int = 100 // 0 to 100%
) {
    /**
     * Checks if this rule matches a given request URL.
     */
    fun matchesUrl(url: String): Boolean {
        return try {
            Regex(urlPattern, RegexOption.IGNORE_CASE).containsMatchIn(url)
        } catch (e: Exception) {
            url.contains(urlPattern, ignoreCase = true)
        }
    }
}
