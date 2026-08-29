package io.github.zakayothuku.chaosproxy.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaosRuleTest {

    @Test
    fun `matchesUrl returns true for a normal matching regex`() {
        val rule = ChaosRule(name = "Auth", urlPattern = ".*\\/auth\\/.*")

        assertTrue(rule.matchesUrl("https://api.example.com/auth/login"))
        assertFalse(rule.matchesUrl("https://api.example.com/public/feed"))
    }

    @Test
    fun `matchesUrl falls back to substring match for invalid regex`() {
        val rule = ChaosRule(name = "Bad Regex", urlPattern = "[unclosed(")

        assertTrue(rule.matchesUrl("https://api.example.com/[unclosed("))
        assertFalse(rule.matchesUrl("https://api.example.com/users"))
    }

    @Test
    fun `matchesUrl does not match when pattern exceeds max length`() {
        val rule = ChaosRule(name = "Too Long", urlPattern = "a".repeat(301))

        // Falls back to substring containment, which will not be found in a normal URL.
        assertFalse(rule.matchesUrl("https://api.example.com/users"))
    }

    @Test(timeout = 2000)
    fun `matchesUrl does not hang on catastrophic backtracking regex`() {
        // Classic ReDoS pattern: exponential backtracking on a long run of 'a's followed by
        // a character that ultimately fails to match.
        val rule = ChaosRule(name = "Evil Regex", urlPattern = "(a+)+\$")
        val maliciousUrl = "a".repeat(40) + "!"

        // Must return promptly (bounded by the internal match timeout) rather than hang.
        assertFalse(rule.matchesUrl(maliciousUrl))
    }

    @Test
    fun `matchesUrl reuses compiled pattern across repeated calls`() {
        val rule = ChaosRule(name = "Repeated", urlPattern = ".*\\/orders\\/.*")

        repeat(50) {
            assertTrue(rule.matchesUrl("https://api.example.com/orders/$it"))
        }
    }
}
