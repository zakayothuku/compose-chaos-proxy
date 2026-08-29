package io.github.zakayothuku.chaosproxy.repository

import io.github.zakayothuku.chaosproxy.engine.ChaosRule
import io.github.zakayothuku.chaosproxy.model.ChaosActionType
import io.github.zakayothuku.chaosproxy.model.ChaosEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChaosConfigRepositoryTest {

    @Before
    fun setup() {
        ChaosConfigRepository.clearAll()
    }

    @After
    fun teardown() {
        ChaosConfigRepository.clearAll()
    }

    @Test
    fun `setGlobalEnabled toggles the globalEnabled flag`() {
        assertFalse(ChaosConfigRepository.state.value.globalEnabled)

        ChaosConfigRepository.setGlobalEnabled(true)
        assertTrue(ChaosConfigRepository.state.value.globalEnabled)

        ChaosConfigRepository.setGlobalEnabled(false)
        assertFalse(ChaosConfigRepository.state.value.globalEnabled)
    }

    @Test
    fun `applyPreset NONE clears rules and active preset`() {
        ChaosConfigRepository.applyPreset(ChaosPresetType.FLAKY_3G)
        assertTrue(ChaosConfigRepository.state.value.rules.isNotEmpty())

        ChaosConfigRepository.applyPreset(ChaosPresetType.NONE)

        val state = ChaosConfigRepository.state.value
        assertEquals(ChaosPresetType.NONE, state.activePreset)
        assertTrue(state.rules.isEmpty())
    }

    @Test
    fun `applyPreset loads the matching preset rules and enables chaos`() {
        ChaosConfigRepository.applyPreset(ChaosPresetType.AUTH_401)

        val state = ChaosConfigRepository.state.value
        assertTrue(state.globalEnabled)
        assertEquals(ChaosPresetType.AUTH_401, state.activePreset)
        assertEquals(DefaultChaosPresets.PRESET_AUTH_401.rules, state.rules)
    }

    @Test
    fun `addRule appends the rule and resets active preset to NONE`() {
        ChaosConfigRepository.applyPreset(ChaosPresetType.FLAKY_3G)
        val newRule = ChaosRule(name = "Custom Rule", urlPattern = ".*\\/custom\\/.*")

        ChaosConfigRepository.addRule(newRule)

        val state = ChaosConfigRepository.state.value
        assertEquals(ChaosPresetType.NONE, state.activePreset)
        assertTrue(state.rules.any { it.id == newRule.id })
        // The preset's original rule(s) should still be present alongside the new one.
        assertEquals(DefaultChaosPresets.PRESET_FLAKY_3G.rules.size + 1, state.rules.size)
    }

    @Test
    fun `updateRule only modifies the targeted rule`() {
        val ruleA = ChaosRule(name = "Rule A")
        val ruleB = ChaosRule(name = "Rule B")
        ChaosConfigRepository.addRule(ruleA)
        ChaosConfigRepository.addRule(ruleB)

        ChaosConfigRepository.updateRule(ruleA.id) { it.copy(name = "Rule A Updated") }

        val state = ChaosConfigRepository.state.value
        assertEquals("Rule A Updated", state.rules.first { it.id == ruleA.id }.name)
        assertEquals("Rule B", state.rules.first { it.id == ruleB.id }.name)
    }

    @Test
    fun `deleteRule removes only the targeted rule`() {
        val ruleA = ChaosRule(name = "Rule A")
        val ruleB = ChaosRule(name = "Rule B")
        ChaosConfigRepository.addRule(ruleA)
        ChaosConfigRepository.addRule(ruleB)

        ChaosConfigRepository.deleteRule(ruleA.id)

        val state = ChaosConfigRepository.state.value
        assertEquals(1, state.rules.size)
        assertEquals(ruleB.id, state.rules.single().id)
    }

    @Test
    fun `toggleRule flips enabled state without affecting other rules`() {
        val rule = ChaosRule(name = "Toggle Me", enabled = true)
        ChaosConfigRepository.addRule(rule)

        ChaosConfigRepository.toggleRule(rule.id)
        assertFalse(ChaosConfigRepository.state.value.rules.single().enabled)

        ChaosConfigRepository.toggleRule(rule.id)
        assertTrue(ChaosConfigRepository.state.value.rules.single().enabled)
    }

    @Test
    fun `logEvent prepends new events with most recent first`() {
        val first = ChaosEvent(requestUrl = "https://a.example.com", method = "GET", actionType = ChaosActionType.DELAYED, description = "first")
        val second = ChaosEvent(requestUrl = "https://b.example.com", method = "GET", actionType = ChaosActionType.DELAYED, description = "second")

        ChaosConfigRepository.logEvent(first)
        ChaosConfigRepository.logEvent(second)

        val events = ChaosConfigRepository.state.value.events
        assertEquals(second.id, events[0].id)
        assertEquals(first.id, events[1].id)
    }

    @Test
    fun `logEvent trims history to MAX_EVENTS keeping the most recent`() {
        repeat(105) { index ->
            ChaosConfigRepository.logEvent(
                ChaosEvent(
                    requestUrl = "https://example.com/$index",
                    method = "GET",
                    actionType = ChaosActionType.DELAYED,
                    description = "event $index"
                )
            )
        }

        val events = ChaosConfigRepository.state.value.events
        assertEquals(100, events.size)
        // The most recently logged event (index 104) should be first; the oldest 5 (0..4) dropped.
        assertTrue(events.first().description.contains("104"))
        assertTrue(events.none { it.description.contains("event 4)") || it.description == "event 4" })
    }

    @Test
    fun `clearEvents empties the event log without touching rules`() {
        ChaosConfigRepository.addRule(ChaosRule(name = "Keep Me"))
        ChaosConfigRepository.logEvent(
            ChaosEvent(requestUrl = "https://example.com", method = "GET", actionType = ChaosActionType.DELAYED, description = "event")
        )

        ChaosConfigRepository.clearEvents()

        val state = ChaosConfigRepository.state.value
        assertTrue(state.events.isEmpty())
        assertEquals(1, state.rules.size)
    }

    @Test
    fun `clearAll resets the entire state to defaults`() {
        ChaosConfigRepository.applyPreset(ChaosPresetType.OFFLINE_MODE)
        ChaosConfigRepository.logEvent(
            ChaosEvent(requestUrl = "https://example.com", method = "GET", actionType = ChaosActionType.CONNECTION_DROPPED, description = "event")
        )

        ChaosConfigRepository.clearAll()

        assertEquals(ChaosConfigState(), ChaosConfigRepository.state.value)
    }
}
