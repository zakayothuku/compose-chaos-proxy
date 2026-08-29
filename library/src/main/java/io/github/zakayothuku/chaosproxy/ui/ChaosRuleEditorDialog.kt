package io.github.zakayothuku.chaosproxy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.zakayothuku.chaosproxy.engine.ChaosRule
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaosRuleEditorDialog(
    initialRule: ChaosRule? = null,
    onDismiss: () -> Unit,
    onSaveRule: (ChaosRule) -> Unit
) {
    var name by remember { mutableStateOf(initialRule?.name ?: "") }
    var urlPattern by remember { mutableStateOf(initialRule?.urlPattern ?: ".*") }
    var minDelayMs by remember { mutableFloatStateOf(initialRule?.minDelayMs?.toFloat() ?: 0f) }
    var maxDelayMs by remember { mutableFloatStateOf(initialRule?.maxDelayMs?.toFloat() ?: 0f) }
    var selectedStatusCode by remember { mutableStateOf(initialRule?.injectedStatusCode?.toString() ?: "None (200)") }
    var customResponseBody by remember { mutableStateOf(initialRule?.customResponseBody ?: "") }
    var dropConnection by remember { mutableStateOf(initialRule?.dropConnection ?: false) }
    var failureProbability by remember { mutableFloatStateOf(initialRule?.failureProbabilityPercent?.toFloat() ?: 100f) }

    var statusCodeDropdownExpanded by remember { mutableStateOf(false) }
    val statusOptions = listOf("None (200)", "400 Bad Request", "401 Unauthorized", "403 Forbidden", "404 Not Found", "429 Too Many Requests", "500 Server Error", "503 Service Unavailable")

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val code = when {
                        selectedStatusCode.startsWith("400") -> 400
                        selectedStatusCode.startsWith("401") -> 401
                        selectedStatusCode.startsWith("403") -> 403
                        selectedStatusCode.startsWith("404") -> 404
                        selectedStatusCode.startsWith("429") -> 429
                        selectedStatusCode.startsWith("500") -> 500
                        selectedStatusCode.startsWith("503") -> 503
                        else -> null
                    }

                    val rule = ChaosRule(
                        id = initialRule?.id ?: java.util.UUID.randomUUID().toString(),
                        name = if (name.isBlank()) "Custom Rule" else name,
                        enabled = true,
                        urlPattern = if (urlPattern.isBlank()) ".*" else urlPattern,
                        minDelayMs = minDelayMs.toLong(),
                        maxDelayMs = maxOf(minDelayMs.toLong(), maxDelayMs.toLong()),
                        injectedStatusCode = code,
                        customResponseBody = customResponseBody.ifBlank { null },
                        dropConnection = dropConnection,
                        failureProbabilityPercent = failureProbability.roundToInt()
                    )
                    onSaveRule(rule)
                }
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = {
            Text(if (initialRule != null) "Edit Chaos Rule" else "New Chaos Rule")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Rule Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Name") },
                    placeholder = { Text("e.g. Throttle User Profile") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // URL Pattern
                OutlinedTextField(
                    value = urlPattern,
                    onValueChange = { urlPattern = it },
                    label = { Text("Target URL Regex") },
                    placeholder = { Text(".*\\/api\\/v1\\/.*") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Latency Sliders
                Text(
                    text = "Artificial Delay: ${minDelayMs.roundToInt()}ms – ${maxDelayMs.roundToInt()}ms",
                    fontSize = 13.sp
                )
                Text("Min Delay: ${minDelayMs.roundToInt()}ms", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = minDelayMs,
                    onValueChange = { minDelayMs = it },
                    valueRange = 0f..5000f,
                    steps = 49
                )
                Text("Max Delay: ${maxDelayMs.roundToInt()}ms", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = maxDelayMs,
                    onValueChange = { maxDelayMs = it },
                    valueRange = 0f..5000f,
                    steps = 49
                )

                // Failure Probability
                Text(
                    text = "Failure Rate: ${failureProbability.roundToInt()}%",
                    fontSize = 13.sp
                )
                Slider(
                    value = failureProbability,
                    onValueChange = { failureProbability = it },
                    valueRange = 0f..100f,
                    steps = 19
                )

                // Drop Connection Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Drop Connection (Timeout)")
                    Switch(checked = dropConnection, onCheckedChange = { dropConnection = it })
                }

                // HTTP Status Code Picker
                if (!dropConnection) {
                    ExposedDropdownMenuBox(
                        expanded = statusCodeDropdownExpanded,
                        onExpandedChange = { statusCodeDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedStatusCode,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Injected HTTP Status") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusCodeDropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = statusCodeDropdownExpanded,
                            onDismissRequest = { statusCodeDropdownExpanded = false }
                        ) {
                            statusOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        selectedStatusCode = option
                                        statusCodeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (selectedStatusCode != "None (200)") {
                        OutlinedTextField(
                            value = customResponseBody,
                            onValueChange = { customResponseBody = it },
                            label = { Text("Custom JSON Error Body") },
                            placeholder = { Text("""{"error": "Custom simulated failure"}""") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                }
            }
        }
    )
}
