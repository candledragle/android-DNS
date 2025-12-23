package com.github.dnsresolver.dns.metrics

/**
 * Facade for configuring and creating DNS metrics collectors.
 *
 * This allows the App (module user) to control whether logging/metrics
 * are enabled, without the DNS library hard‑coding any BuildConfig logic.
 */
object DnsMetricsFacade {

    @Volatile
    private var loggingEnabled: Boolean = false

    /**
     * Configure whether DNS metrics logging is enabled.
     *
     * Typical usage in App (e.g. Application.onCreate or MainActivity):
     *
     * DnsMetricsFacade.setLoggingEnabled(BuildConfig.DEBUG)
     */
    fun setLoggingEnabled(enabled: Boolean) {
        loggingEnabled = enabled
    }

    /**
     * Check current logging flag.
     */
    fun isLoggingEnabled(): Boolean = loggingEnabled

    /**
     * Create a LogDnsMetricsCollector with the current logging setting.
     *
     * The App should call this instead of constructing LogDnsMetricsCollector directly.
     */
    fun createLogCollector(tag: String = "DnsMetrics"): LogDnsMetricsCollector {
        return LogDnsMetricsCollector(tag = tag, enableLogging = loggingEnabled)
    }
}


