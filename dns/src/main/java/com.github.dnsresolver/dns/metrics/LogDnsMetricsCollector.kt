package com.github.dnsresolver.dns.metrics

import android.util.Log
import java.util.Locale

/**
 * Log DNS Metrics Collector
 * Outputs all DNS resolution events and metrics to Android Log.
 *
 * IMPORTANT: Do not construct this directly in App code.
 * Use DnsMetricsFacade.createLogCollector(...) so that the logging switch
 * can be centrally controlled by the module user.
 */
class LogDnsMetricsCollector(
    private val tag: String = "DnsMetrics",
    private val enableLogging: Boolean
) : DnsMetricsCollector {

    private val resolverStatsMap = mutableMapOf<String, MutableResolverStats>()
    private val hostnameStatsMap = mutableMapOf<String, MutableHostnameStats>()
    private var totalRequests = 0L
    private var totalSuccess = 0L
    private var totalFailure = 0L

    /**
     * Inline log function that only calls Log.d when logging is enabled
     * This ensures no string concatenation or function call overhead when logging is disabled
     * The inline keyword allows the compiler to optimize away the entire call when enableLogging is false
     */
    private inline fun log(message: String) {
        if (enableLogging) {
            Log.d(tag, message)
        }
    }

    @Synchronized
    override fun recordEvent(event: DnsEvent) {
        when (event.type) {
            DnsEventType.RESOLVE_START -> {
                totalRequests++
                log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                log("🔍 DNS Resolution Started")
                log("   Hostname: ${event.hostname}")
                log("   Total Resolvers: ${event.totalResolvers ?: 0}")
                log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            }
            DnsEventType.RESOLVER_ATTEMPT_START -> {
                log("  ⏳ Attempting Resolver [${event.resolverIndex ?: 0}]: ${event.resolverName ?: "unknown"}")
            }
            DnsEventType.RESOLVER_ATTEMPT_SUCCESS -> {
                val duration = event.durationMs ?: 0
                val addressCount = event.addresses?.size ?: 0
                log("  ✅ Resolver [${event.resolverIndex ?: 0}] Success: ${event.resolverName ?: "unknown"}")
                log("     Duration: ${duration}ms")
                log("     IP Count: $addressCount")
                event.addresses?.take(5)?.forEachIndexed { index, address ->
                    log("     IP[${index + 1}]: ${address.hostAddress}")
                }
                if (addressCount > 5) {
                    log("     ... ${addressCount - 5} more IP addresses")
                }
                updateResolverStats(event.resolverName ?: "unknown", true, duration)
            }
            DnsEventType.RESOLVER_ATTEMPT_FAILURE -> {
                val duration = event.durationMs ?: 0
                val errorMsg = event.error?.message ?: "unknown error"
                log("  ❌ Resolver [${event.resolverIndex ?: 0}] Failed: ${event.resolverName ?: "unknown"}")
                log("     Duration: ${duration}ms")
                log("     Error Type: ${event.error?.javaClass?.simpleName ?: "Unknown"}")
                log("     Error Message: $errorMsg")
                updateResolverStats(event.resolverName ?: "unknown", false, duration)
            }
            DnsEventType.RESOLVE_SUCCESS -> {
                totalSuccess++
                val duration = event.durationMs ?: 0
                val addressCount = event.addresses?.size ?: 0
                log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                log("✅ DNS Resolution Success")
                log("   Hostname: ${event.hostname}")
                log("   Used Resolver: ${event.resolverName ?: "unknown"} [Index: ${event.resolverIndex ?: 0}]")
                log("   Total Duration: ${duration}ms")
                log("   IP Address Count: $addressCount")
                event.addresses?.take(5)?.forEachIndexed { index, address ->
                    log("   IP[${index + 1}]: ${address.hostAddress}")
                }
                if (addressCount > 5) {
                    log("   ... ${addressCount - 5} more IP addresses")
                }
                log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                updateHostnameStats(event.hostname, true, duration)
            }
            DnsEventType.RESOLVE_FAILURE -> {
                totalFailure++
                val duration = event.durationMs ?: 0
                val errorMsg = event.error?.message ?: "unknown error"
                log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                log("❌ DNS Resolution Failed")
                log("   Hostname: ${event.hostname}")
                log("   Total Duration: ${duration}ms")
                log("   Attempted Resolvers: ${event.totalResolvers ?: 0}")
                log("   Error Type: ${event.error?.javaClass?.simpleName ?: "Unknown"}")
                log("   Error Message: $errorMsg")
                log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                updateHostnameStats(event.hostname, false, duration)
            }
        }
    }

    override fun recordMetrics(metrics: DnsMetrics) {
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        if (metrics.success) {
            log("📊 DNS Metrics - Success")
        } else {
            log("📊 DNS Metrics - Failed")
        }
        log("   Hostname: ${metrics.hostname}")
        log("   Used Resolver: ${metrics.usedResolver ?: "unknown"}")
        log("   Total Duration: ${metrics.totalDurationMs}ms")
        log("   IP Address Count: ${metrics.addresses?.size ?: 0}")
        log("   Attempted Resolvers: ${metrics.attemptedResolvers.size}")

        if (metrics.attemptedResolvers.isNotEmpty()) {
            log("   Attempted Resolvers List:")
            metrics.attemptedResolvers.forEachIndexed { index, resolver ->
                val success = metrics.resolverResults[resolver] ?: false
                val duration = metrics.resolverDurations[resolver] ?: 0
                val status = if (success) "✅" else "❌"
                log("     [$index] $status $resolver (${duration}ms)")
            }
        }

        if (metrics.addresses?.isNotEmpty() == true) {
            log("   Resolved IP Addresses:")
            metrics.addresses.take(5).forEachIndexed { index, address ->
                log("     [${index + 1}] ${address.hostAddress}")
            }
            if (metrics.addresses.size > 5) {
                log("     ... ${metrics.addresses.size - 5} more IP addresses")
            }
        }

        if (metrics.error != null) {
            log("   Error: ${metrics.error.javaClass.simpleName}")
            log("   Error Message: ${metrics.error.message}")
        }
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    override fun getAggregatedStats(): DnsAggregatedStats {
        val resolverStats = resolverStatsMap.map { (resolverName, stats) ->
            resolverName to ResolverStats(
                resolverName = resolverName,
                totalRequests = stats.totalAttempts,
                successCount = stats.successCount,
                failureCount = stats.failureCount,
                totalDurationMs = stats.totalDurationMs,
                avgDurationMs = if (stats.totalAttempts > 0) stats.totalDurationMs.toDouble() / stats.totalAttempts else 0.0
            )
        }.toMap()

        val hostnameStats = hostnameStatsMap.map { (hostname, stats) ->
            hostname to HostnameStats(
                hostname = hostname,
                totalRequests = stats.totalRequests,
                successCount = stats.successCount,
                failureCount = stats.failureCount,
                avgDurationMs = if (stats.totalRequests > 0) stats.totalDurationMs.toDouble() / stats.totalRequests else 0.0
            )
        }.toMap()

        return DnsAggregatedStats(
            totalRequests = totalRequests,
            totalSuccess = totalSuccess,
            totalFailure = totalFailure,
            resolverStats = resolverStats,
            hostnameStats = hostnameStats
        )
    }

    override fun clearStats() {
        resolverStatsMap.clear()
        hostnameStatsMap.clear()
        totalRequests = 0L
        totalSuccess = 0L
        totalFailure = 0L
        log("🗑️  All DNS statistics cleared")
    }

    /**
     * Print aggregated statistics to log
     */
    fun printAggregatedStats() {
        val stats = getAggregatedStats()
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        log("📈 DNS Aggregated Statistics")
        log("   Total Requests: ${stats.totalRequests}")
        log("   Success: ${stats.totalSuccess}")
        log("   Failure: ${stats.totalFailure}")
        log(
            "   Success Rate: ${
                if (stats.totalRequests > 0) String.format(
                    Locale.US,
                    "%.2f%%",
                    stats.totalSuccess * 100.0 / stats.totalRequests
                ) else "0.00%"
            }"
        )

        if (stats.resolverStats.isNotEmpty()) {
            log("   Resolver Statistics:")
            stats.resolverStats.forEach { (resolver, resolverStat) ->
                log("     [$resolver]")
                log("       Attempts: ${resolverStat.totalRequests}")
                log("       Success: ${resolverStat.successCount}, Failure: ${resolverStat.failureCount}")
                log("       Success Rate: ${String.format(Locale.US, "%.2f%%", resolverStat.successRate * 100)}")
                log("       Avg Duration: ${String.format(Locale.US, "%.2f", resolverStat.avgDurationMs)}ms")
            }
        }

        if (stats.hostnameStats.isNotEmpty()) {
            log("   Hostname Statistics:")
            stats.hostnameStats.forEach { (hostname, hostnameStat) ->
                log("     [$hostname]")
                log("       Requests: ${hostnameStat.totalRequests}")
                log("       Success: ${hostnameStat.successCount}, Failure: ${hostnameStat.failureCount}")
                log("       Success Rate: ${String.format(Locale.US, "%.2f%%", hostnameStat.successRate * 100)}")
                log("       Avg Duration: ${String.format(Locale.US, "%.2f", hostnameStat.avgDurationMs)}ms")
            }
        }
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    private fun updateResolverStats(resolverName: String, success: Boolean, durationMs: Long) {
        val stats = resolverStatsMap.getOrPut(resolverName) { MutableResolverStats() }
        stats.totalAttempts++
        if (success) {
            stats.successCount++
        } else {
            stats.failureCount++
        }
        stats.totalDurationMs += durationMs
    }

    private fun updateHostnameStats(hostname: String, success: Boolean, durationMs: Long) {
        val stats = hostnameStatsMap.getOrPut(hostname) { MutableHostnameStats() }
        stats.totalRequests++
        if (success) {
            stats.successCount++
        } else {
            stats.failureCount++
        }
        stats.totalDurationMs += durationMs
    }

    private data class MutableResolverStats(
        var totalAttempts: Long = 0,
        var successCount: Long = 0,
        var failureCount: Long = 0,
        var totalDurationMs: Long = 0
    )

    private data class MutableHostnameStats(
        var totalRequests: Long = 0,
        var successCount: Long = 0,
        var failureCount: Long = 0,
        var totalDurationMs: Long = 0
    )
}


