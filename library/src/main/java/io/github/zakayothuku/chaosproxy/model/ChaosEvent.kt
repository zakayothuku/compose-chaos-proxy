package io.github.zakayothuku.chaosproxy.model

import java.util.UUID

enum class ChaosActionType {
    NONE,
    DELAYED,
    HTTP_ERROR_INJECTED,
    CONNECTION_DROPPED,
    BYPASSED
}

data class ChaosEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val requestUrl: String,
    val method: String,
    val actionType: ChaosActionType,
    val ruleNameApplied: String? = null,
    val delayAppliedMs: Long = 0,
    val statusCodeReturned: Int = 200,
    val description: String
)
