package io.github.zakayothuku.chaosproxy

import io.github.zakayothuku.chaosproxy.engine.ChaosEngine
import io.github.zakayothuku.chaosproxy.engine.ChaosExecutionResult
import io.github.zakayothuku.chaosproxy.repository.ChaosConfigRepository
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Plug-and-play OkHttp Interceptor that injects network latency, synthetic HTTP status codes,
 * and connection drops into live Android applications based on active ChaosProxy rules.
 */
class ComposeChaosInterceptor(
    private val engine: ChaosEngine = ChaosEngine()
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        when (val result = engine.evaluate(request)) {
            is ChaosExecutionResult.DropConnection -> {
                if (result.delayMs > 0) {
                    Thread.sleep(result.delayMs)
                }
                throw result.exception
            }

            is ChaosExecutionResult.InjectedResponse -> {
                if (result.delayMs > 0) {
                    Thread.sleep(result.delayMs)
                }
                return result.response
            }

            is ChaosExecutionResult.Proceed -> {
                if (result.delayMs > 0) {
                    Thread.sleep(result.delayMs)
                }
                return chain.proceed(request)
            }
        }
    }

    companion object {
        /**
         * Quick utility method to enable or disable global chaos.
         */
        fun setEnabled(enabled: Boolean) {
            ChaosConfigRepository.setGlobalEnabled(enabled)
        }
    }
}
