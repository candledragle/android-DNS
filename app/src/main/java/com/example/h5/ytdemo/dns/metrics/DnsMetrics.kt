package com.example.h5.ytdemo.dns.metrics

import java.net.InetAddress

/**
 * DNS 解析指标数据
 */
data class DnsMetrics(
    /**
     * 域名
     */
    val hostname: String,

    /**
     * 是否成功
     */
    val success: Boolean,

    /**
     * 总耗时（毫秒）
     */
    val totalDurationMs: Long,

    /**
     * 使用的解析器名称
     */
    val usedResolver: String? = null,

    /**
     * 解析结果（成功时）
     */
    val addresses: List<InetAddress>? = null,

    /**
     * 错误信息（失败时）
     */
    val error: Throwable? = null,

    /**
     * 尝试的解析器列表（按尝试顺序）
     */
    val attemptedResolvers: List<String> = emptyList(),

    /**
     * 每个解析器的耗时（毫秒）
     */
    val resolverDurations: Map<String, Long> = emptyMap(),

    /**
     * 每个解析器的结果（成功/失败）
     */
    val resolverResults: Map<String, Boolean> = emptyMap(),

    /**
     * 时间戳
     */
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * DNS 解析器统计信息
 */
data class ResolverStats(
    /**
     * 解析器名称
     */
    val resolverName: String,

    /**
     * 总请求数
     */
    val totalRequests: Long = 0,

    /**
     * 成功数
     */
    val successCount: Long = 0,

    /**
     * 失败数
     */
    val failureCount: Long = 0,

    /**
     * 平均耗时（毫秒）
     */
    val avgDurationMs: Double = 0.0,

    /**
     * 最小耗时（毫秒）
     */
    val minDurationMs: Long = Long.MAX_VALUE,

    /**
     * 最大耗时（毫秒）
     */
    val maxDurationMs: Long = 0,

    /**
     * 总耗时（毫秒）
     */
    val totalDurationMs: Long = 0
) {
    /**
     * 成功率（0.0 - 1.0）
     */
    val successRate: Double
        get() = if (totalRequests > 0) successCount.toDouble() / totalRequests else 0.0
}

/**
 * 聚合统计信息
 */
data class DnsAggregatedStats(
    /**
     * 总请求数
     */
    val totalRequests: Long = 0,

    /**
     * 总成功数
     */
    val totalSuccess: Long = 0,

    /**
     * 总失败数
     */
    val totalFailure: Long = 0,

    /**
     * 每个解析器的统计信息
     */
    val resolverStats: Map<String, ResolverStats> = emptyMap(),

    /**
     * 按域名分组的统计信息
     */
    val hostnameStats: Map<String, HostnameStats> = emptyMap()
)

/**
 * 域名统计信息
 */
data class HostnameStats(
    /**
     * 域名
     */
    val hostname: String,

    /**
     * 总请求数
     */
    val totalRequests: Long = 0,

    /**
     * 成功数
     */
    val successCount: Long = 0,

    /**
     * 失败数
     */
    val failureCount: Long = 0,

    /**
     * 平均耗时（毫秒）
     */
    val avgDurationMs: Double = 0.0
)

