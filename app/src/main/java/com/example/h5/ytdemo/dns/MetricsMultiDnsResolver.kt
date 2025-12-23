package com.example.h5.ytdemo.dns

import com.example.h5.ytdemo.dns.metrics.*
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * 带打点的多级 DNS 解析器
 * 在 MultiDnsResolver 基础上增加指标收集功能
 *
 * @param resolvers DNS 解析器列表，按优先级排序
 * @param resolverNames 解析器名称列表，与 resolvers 一一对应
 * @param metricsCollector 指标收集器
 */
class MetricsMultiDnsResolver(
    private val resolvers: List<Dns>,
    private val resolverNames: List<String>,
    private val metricsCollector: DnsMetricsCollector = NoOpDnsMetricsCollector
) : Dns {

    init {
        require(resolvers.size == resolverNames.size) {
            "解析器列表和名称列表长度必须一致"
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val startTime = System.currentTimeMillis()
        val attemptedResolvers = mutableListOf<String>()
        val resolverDurations = mutableMapOf<String, Long>()
        val resolverResults = mutableMapOf<String, Boolean>()

        // 记录开始事件
        metricsCollector.recordEvent(
            DnsEvent(
                type = DnsEventType.RESOLVE_START,
                hostname = hostname,
                totalResolvers = resolvers.size
            )
        )

        val errors = mutableListOf<Exception>()

        for ((index, resolver) in resolvers.withIndex()) {
            val resolverName = resolverNames[index]
            val resolverStartTime = System.currentTimeMillis()

            attemptedResolvers.add(resolverName)

            // 记录解析器尝试开始
            metricsCollector.recordEvent(
                DnsEvent(
                    type = DnsEventType.RESOLVER_ATTEMPT_START,
                    hostname = hostname,
                    resolverName = resolverName,
                    resolverIndex = index,
                    totalResolvers = resolvers.size
                )
            )

            try {
                val result = resolver.lookup(hostname)
                val resolverDuration = System.currentTimeMillis() - resolverStartTime
                resolverDurations[resolverName] = resolverDuration
                resolverResults[resolverName] = true

                if (result.isNotEmpty()) {
                    val totalDuration = System.currentTimeMillis() - startTime

                    // 记录解析器尝试成功
                    metricsCollector.recordEvent(
                        DnsEvent(
                            type = DnsEventType.RESOLVER_ATTEMPT_SUCCESS,
                            hostname = hostname,
                            resolverName = resolverName,
                            addresses = result,
                            durationMs = resolverDuration,
                            resolverIndex = index,
                            totalResolvers = resolvers.size
                        )
                    )

                    // 记录整体解析成功
                    metricsCollector.recordEvent(
                        DnsEvent(
                            type = DnsEventType.RESOLVE_SUCCESS,
                            hostname = hostname,
                            resolverName = resolverName,
                            addresses = result,
                            durationMs = totalDuration,
                            totalResolvers = resolvers.size
                        )
                    )

                    // 记录完整指标
                    metricsCollector.recordMetrics(
                        DnsMetrics(
                            hostname = hostname,
                            success = true,
                            totalDurationMs = totalDuration,
                            usedResolver = resolverName,
                            addresses = result,
                            attemptedResolvers = attemptedResolvers.toList(),
                            resolverDurations = resolverDurations.toMap(),
                            resolverResults = resolverResults.toMap()
                        )
                    )

                    return result
                }
            } catch (e: Exception) {
                errors.add(e)
                val resolverDuration = System.currentTimeMillis() - resolverStartTime
                resolverDurations[resolverName] = resolverDuration
                resolverResults[resolverName] = false

                // 记录解析器尝试失败
                metricsCollector.recordEvent(
                    DnsEvent(
                        type = DnsEventType.RESOLVER_ATTEMPT_FAILURE,
                        hostname = hostname,
                        resolverName = resolverName,
                        error = e,
                        durationMs = resolverDuration,
                        resolverIndex = index,
                        totalResolvers = resolvers.size
                    )
                )
            }
        }

        // 所有解析器都失败
        val totalDuration = System.currentTimeMillis() - startTime
        val errorMessage = buildString {
            append("所有 DNS 解析器都失败，无法解析: $hostname")
            if (errors.isNotEmpty()) {
                append("\n错误列表:")
                errors.forEachIndexed { index, error ->
                    append("\n  ${index + 1}. ${error.javaClass.simpleName}: ${error.message}")
                }
            }
        }
        val finalError = UnknownHostException(errorMessage)

        // 记录整体解析失败
        metricsCollector.recordEvent(
            DnsEvent(
                type = DnsEventType.RESOLVE_FAILURE,
                hostname = hostname,
                error = finalError,
                durationMs = totalDuration,
                totalResolvers = resolvers.size
            )
        )

        // 记录完整指标
        metricsCollector.recordMetrics(
            DnsMetrics(
                hostname = hostname,
                success = false,
                totalDurationMs = totalDuration,
                error = finalError,
                attemptedResolvers = attemptedResolvers.toList(),
                resolverDurations = resolverDurations.toMap(),
                resolverResults = resolverResults.toMap()
            )
        )

        throw finalError
    }

    /**
     * 获取解析器数量
     */
    fun getResolverCount(): Int = resolvers.size

    /**
     * 获取所有解析器（用于测试）
     */
    fun getResolvers(): List<Dns> = resolvers.toList()
}

