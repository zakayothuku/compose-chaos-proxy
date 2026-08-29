package io.github.zakayothuku.chaosproxy.engine

import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

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

    // Compiled lazily and cached on this instance so a rule's regex is parsed once, not
    // re-parsed on every intercepted request. `null` means urlPattern is unusable as a regex
    // (too long, or fails to compile), in which case matchesUrl() falls back to a plain
    // substring check. Excluded from equals()/hashCode()/copy() since it isn't a constructor
    // property.
    private val compiledPattern: Pattern? by lazy {
        if (urlPattern.length > MAX_PATTERN_LENGTH) {
            null
        } else {
            try {
                Pattern.compile(urlPattern, Pattern.CASE_INSENSITIVE)
            } catch (e: PatternSyntaxException) {
                null
            }
        }
    }

    /**
     * Checks if this rule matches a given request URL.
     *
     * User-supplied regex (from [urlPattern]) can be malicious or simply buggy and trigger
     * catastrophic backtracking (ReDoS), which would otherwise hang the calling thread (the
     * OkHttp interceptor chain) indefinitely. Matching is therefore bounded by
     * [MATCH_TIMEOUT_MS] on a small background pool; a pattern that doesn't resolve in time is
     * treated as "no match" so chaos rules can never make the app hang worse than the chaos
     * they're meant to simulate.
     */
    fun matchesUrl(url: String): Boolean {
        val pattern = compiledPattern ?: return url.contains(urlPattern, ignoreCase = true)
        return try {
            matchWithTimeout(pattern, url)
        } catch (e: Exception) {
            false
        }
    }

    private fun matchWithTimeout(pattern: Pattern, url: String): Boolean {
        val future = try {
            matcherExecutor.submit<Boolean> { pattern.matcher(url).find() }
        } catch (e: RejectedExecutionException) {
            return false
        }
        return try {
            future.get(MATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            future.cancel(true)
            false
        }
    }

    private companion object {
        private const val MAX_PATTERN_LENGTH = 300
        private const val MATCH_TIMEOUT_MS = 200L

        // Small bounded, daemon-threaded pool shared by all rules: a pathological regex can
        // only ever tie up one of these worker threads (leaked, in the worst case where the
        // engine ignores interruption) instead of blocking the caller's network thread.
        private val matcherExecutor: ExecutorService = ThreadPoolExecutor(
            1,
            4,
            30L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(16)
        ) { runnable -> Thread(runnable, "chaos-proxy-regex-matcher").apply { isDaemon = true } }
    }
}
