package io.github.zakayothuku.chaosproxy.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.zakayothuku.chaosproxy.ComposeChaosInterceptor
import io.github.zakayothuku.chaosproxy.ui.ComposeChaosOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : ComponentActivity() {

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(ComposeChaosInterceptor())
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        SampleAppContent(client = okHttpClient)

                        // Attach floating Chaos Proxy Overlay
                        ComposeChaosOverlay()
                    }
                }
            }
        }
    }
}

@Composable
fun SampleAppContent(client: OkHttpClient) {
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("Tap a button to test network resilience under chaos.") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🌪️ Compose Chaos Proxy",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Test network latency, 401/500/503 errors, and timeouts directly on-device.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    statusText = "Requesting: GET /api/v1/user/profile..."
                    statusText = executeRequest(client, "https://httpbin.org/get?endpoint=user_profile")
                    isLoading = false
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Fetch User Profile (/user/profile)")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    statusText = "Requesting: GET /api/v1/products..."
                    statusText = executeRequest(client, "https://httpbin.org/get?endpoint=products")
                    isLoading = false
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Fetch Products Catalog (/products)")
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    statusText = "Requesting: POST /api/v1/auth/refresh..."
                    statusText = executeRequest(client, "https://httpbin.org/post?endpoint=auth_refresh")
                    isLoading = false
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh Auth Token (/auth/refresh)")
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(12.dp))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = statusText,
                modifier = Modifier.padding(14.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private suspend fun executeRequest(client: OkHttpClient, url: String): String {
    return withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).execute()
            val duration = System.currentTimeMillis() - startTime
            val isChaosInjected = response.header("X-Chaos-Injected") == "true"

            val chaosBadge = if (isChaosInjected) "[🌪️ Chaos Injected] " else ""
            if (response.isSuccessful) {
                "✅ HTTP ${response.code} Success ($duration ms)\n$chaosBadge" + (response.body?.string()?.take(180) ?: "")
            } else {
                "🚨 HTTP ${response.code} Error ($duration ms)\n$chaosBadge" + (response.body?.string()?.take(180) ?: "")
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            "❌ Connection Dropped / Timeout ($duration ms)\nError: ${e.localizedMessage}"
        }
    }
}
