package io.github.zakayothuku.chaosproxy.repository

import io.github.zakayothuku.chaosproxy.engine.ChaosRule
import io.github.zakayothuku.chaosproxy.model.ChaosEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ChaosConfigState(
    val globalEnabled: Boolean = false,
    val activePreset: ChaosPresetType = ChaosPresetType.NONE,
    val rules: List<ChaosRule> = emptyList(),
    val events: List<ChaosEvent> = emptyList()
)

object ChaosConfigRepository {

    private const val MAX_EVENTS = 100

    private val _state = MutableStateFlow(ChaosConfigState())
    val state: StateFlow<ChaosConfigState> = _state.asStateFlow()

    fun setGlobalEnabled(enabled: Boolean) {
        _state.update { it.copy(globalEnabled = enabled) }
    }

    fun applyPreset(presetType: ChaosPresetType) {
        if (presetType == ChaosPresetType.NONE) {
            _state.update { it.copy(activePreset = ChaosPresetType.NONE, rules = emptyList()) }
            return
        }

        val preset = DefaultChaosPresets.ALL_PRESETS.find { it.type == presetType } ?: return
        _state.update {
            it.copy(
                globalEnabled = true,
                activePreset = presetType,
                rules = preset.rules
            )
        }
    }

    fun addRule(rule: ChaosRule) {
        _state.update {
            it.copy(
                activePreset = ChaosPresetType.NONE,
                rules = it.rules + rule
            )
        }
    }

    fun updateRule(ruleId: String, updateBlock: (ChaosRule) -> ChaosRule) {
        _state.update { current ->
            current.copy(
                activePreset = ChaosPresetType.NONE,
                rules = current.rules.map { rule ->
                    if (rule.id == ruleId) updateBlock(rule) else rule
                }
            )
        }
    }

    fun deleteRule(ruleId: String) {
        _state.update { current ->
            current.copy(
                activePreset = ChaosPresetType.NONE,
                rules = current.rules.filterNot { it.id == ruleId }
            )
        }
    }

    fun toggleRule(ruleId: String) {
        _state.update { current ->
            current.copy(
                rules = current.rules.map { rule ->
                    if (rule.id == ruleId) rule.copy(enabled = !rule.enabled) else rule
                }
            )
        }
    }

    fun logEvent(event: ChaosEvent) {
        _state.update { current ->
            current.copy(
                events = (listOf(event) + current.events).take(MAX_EVENTS)
            )
        }
    }

    fun clearEvents() {
        _state.update { it.copy(events = emptyList()) }
    }

    fun clearAll() {
        _state.value = ChaosConfigState()
    }
}
