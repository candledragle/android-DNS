package com.example.h5.ytdemo.dns.metrics

/**
 * DNS 指标收集器接口
 * 用于收集和上报 DNS 解析相关的指标数据
 */
interface DnsMetricsCollector {
    /**
     * 记录 DNS 解析事件
     */
    fun recordEvent(event: DnsEvent)

    /**
     * 记录完整的 DNS 解析指标
     */
    fun recordMetrics(metrics: DnsMetrics)

    /**
     * 获取聚合统计信息
     */
    fun getAggregatedStats(): DnsAggregatedStats

    /**
     * 清空统计数据
     */
    fun clearStats()
}

/**
 * 默认的 DNS 指标收集器实现
 * 在内存中维护统计数据
 */
class DefaultDnsMetricsCollector : DnsMetricsCollector {
    private val resolverStatsMap = mutableMapOf<String, MutableResolverStats>()
    private val hostnameStatsMap = mutableMapOf<String, MutableHostnameStats>()
    private var totalRequests = 0L
    private var totalSuccess = 0L
    private var totalFailure = 0L

    @Synchronized
    override fun recordEvent(event: DnsEvent) {
        when (event.type) {
            DnsEventType.RESOLVE_START -> {
                totalRequests++
            }
            DnsEventType.RESOLVE_SUCCESS -> {
                totalSuccess++
                event.resolverName?.let { updateResolverStats(it, true, event.durationMs ?: 0) }
                updateHostnameStats(event.hostname, true, event.durationMs ?: 0)
            }
            DnsEventType.RESOLVE_FAILURE -> {
                totalFailure++
                updateHostnameStats(event.hostname, false, event.durationMs ?: 0)
            }
            DnsEventType.RESOLVER_ATTEMPT_SUCCESS -> {
                event.resolverName?.let { updateResolverStats(it, true, event.durationMs ?: 0) }
            }
            DnsEventType.RESOLVER_ATTEMPT_FAILURE -> {
                event.resolverName?.let { updateResolverStats(it, false, event.durationMs ?: 0) }
            }
            else -> {
                // 其他事件类型暂不处理
            }
        }
    }

    @Synchronized
    override fun recordMetrics(metrics: DnsMetrics) {
        totalRequests++
        if (metrics.success) {
            totalSuccess++
        } else {
            totalFailure++
        }

        // 更新解析器统计
        metrics.usedResolver?.let { resolverName ->
            updateResolverStats(resolverName, metrics.success, metrics.totalDurationMs)
        }

        // 更新每个尝试的解析器统计
        metrics.resolverDurations.forEach { (resolverName, duration) ->
            val success = metrics.resolverResults[resolverName] ?: false
            updateResolverStats(resolverName, success, duration)
        }

        // 更新域名统计
        updateHostnameStats(metrics.hostname, metrics.success, metrics.totalDurationMs)
    }

    private fun updateResolverStats(resolverName: String, success: Boolean, durationMs: Long) {
        val stats = resolverStatsMap.getOrPut(resolverName) { MutableResolverStats(resolverName) }
        stats.totalRequests++
        if (success) {
            stats.successCount++
        } else {
            stats.failureCount++
        }
        stats.totalDurationMs += durationMs
        stats.avgDurationMs = stats.totalDurationMs.toDouble() / stats.totalRequests
        if (durationMs < stats.minDurationMs) {
            stats.minDurationMs = durationMs
        }
        if (durationMs > stats.maxDurationMs) {
            stats.maxDurationMs = durationMs
        }
    }

    private fun updateHostnameStats(hostname: String, success: Boolean, durationMs: Long) {
        val stats = hostnameStatsMap.getOrPut(hostname) { MutableHostnameStats(hostname) }
        stats.totalRequests++
        if (success) {
            stats.successCount++
        } else {
            stats.failureCount++
        }
        stats.totalDurationMs += durationMs
        stats.avgDurationMs = stats.totalDurationMs.toDouble() / stats.totalRequests
    }

    @Synchronized
    override fun getAggregatedStats(): DnsAggregatedStats {
        return DnsAggregatedStats(
            totalRequests = totalRequests,
            totalSuccess = totalSuccess,
            totalFailure = totalFailure,
            resolverStats = resolverStatsMap.mapValues { (_, stats) -> stats.toResolverStats() },
            hostnameStats = hostnameStatsMap.mapValues { (_, stats) -> stats.toHostnameStats() }
        )
    }

    @Synchronized
    override fun clearStats() {
        resolverStatsMap.clear()
        hostnameStatsMap.clear()
        totalRequests = 0
        totalSuccess = 0
        totalFailure = 0
    }

    /**
     * 可变的解析器统计信息（内部使用）
     */
    private data class MutableResolverStats(
        val resolverName: String,
        var totalRequests: Long = 0,
        var successCount: Long = 0,
        var failureCount: Long = 0,
        var avgDurationMs: Double = 0.0,
        var minDurationMs: Long = Long.MAX_VALUE,
        var maxDurationMs: Long = 0,
        var totalDurationMs: Long = 0
    ) {
        fun toResolverStats(): ResolverStats {
            return ResolverStats(
                resolverName = resolverName,
                totalRequests = totalRequests,
                successCount = successCount,
                failureCount = failureCount,
                avgDurationMs = avgDurationMs,
                minDurationMs = if (minDurationMs == Long.MAX_VALUE) 0 else minDurationMs,
                maxDurationMs = maxDurationMs,
                totalDurationMs = totalDurationMs
            )
        }
    }

    /**
     * 可变的域名统计信息（内部使用）
     */
    private data class MutableHostnameStats(
        val hostname: String,
        var totalRequests: Long = 0,
        var successCount: Long = 0,
        var failureCount: Long = 0,
        var avgDurationMs: Double = 0.0,
        var totalDurationMs: Long = 0
    ) {
        fun toHostnameStats(): HostnameStats {
            return HostnameStats(
                hostname = hostname,
                totalRequests = totalRequests,
                successCount = successCount,
                failureCount = failureCount,
                avgDurationMs = avgDurationMs
            )
        }
    }
}

/**
 * 空实现（用于禁用打点）
 */
object NoOpDnsMetricsCollector : DnsMetricsCollector {
    override fun recordEvent(event: DnsEvent) {}
    override fun recordMetrics(metrics: DnsMetrics) {}
    override fun getAggregatedStats(): DnsAggregatedStats = DnsAggregatedStats()
    override fun clearStats() {}
}

