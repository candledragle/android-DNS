package com.example.h5.ytdemo.dns.metrics

import com.github.dnsresolver.dns.DnsManager
import com.github.dnsresolver.dns.metrics.DefaultDnsMetricsCollector
import com.github.dnsresolver.dns.metrics.DnsAggregatedStats
import com.github.dnsresolver.dns.metrics.DnsEvent
import com.github.dnsresolver.dns.metrics.DnsMetrics
import com.github.dnsresolver.dns.metrics.DnsMetricsCollector
import com.github.dnsresolver.dns.metrics.DnsMetricsReporter
import okhttp3.OkHttpClient

/**
 * DNS 打点功能使用示例
 * 
 * 此文件仅作为示例，展示如何使用 DNS 打点功能
 */
object DnsMetricsUsageExample {

    /**
     * 示例 1: 启用打点的基本用法
     */
    fun example1_BasicUsage() {
        // 创建带打点的 DNS 管理器
        val metricsCollector = DefaultDnsMetricsCollector()
        val dnsManager = DnsManager.createDefault(metricsCollector)

        // 创建带打点的解析器
        val resolver = dnsManager.createCascadingResolver(enableMetrics = true)

        // 在 OkHttp 中使用
        val client = OkHttpClient.Builder()
            .dns(resolver)
            .build()

        // 使用 client 进行网络请求...
        // DNS 解析会自动记录指标

        // 查看统计信息
        val stats = metricsCollector.getAggregatedStats()
        println(DnsMetricsReporter.generateTextReport(stats))
    }

    /**
     * 示例 2: 自定义指标收集器（例如上报到服务器）
     */
    fun example2_CustomCollector() {
        // 创建自定义指标收集器
        val customCollector = object : DnsMetricsCollector {
            override fun recordEvent(event: DnsEvent) {
                // 可以在这里上报事件到服务器
                // 例如：Analytics.track("dns_resolve", event)
                println("DNS Event: ${event.type} for ${event.hostname}")
            }

            override fun recordMetrics(metrics: DnsMetrics) {
                // 可以在这里上报指标到服务器
                // 例如：Metrics.record("dns_resolve_duration", metrics.totalDurationMs)
                println("DNS Metrics: ${metrics.hostname} - ${if (metrics.success) "Success" else "Failure"}")
            }

            override fun getAggregatedStats(): DnsAggregatedStats {
                // 返回空统计（如果不需要本地聚合）
                return DnsAggregatedStats()
            }

            override fun clearStats() {
                // 清空逻辑
            }
        }

        val dnsManager = DnsManager.createDefault(customCollector)
        val resolver = dnsManager.createCascadingResolver()
        // 使用 resolver...
    }

    /**
     * 示例 3: 禁用打点（性能最优）
     */
    fun example3_DisableMetrics() {
        // 不传入 metricsCollector 或使用 NoOpDnsMetricsCollector
        val dnsManager = DnsManager.createDefault() // 默认不收集指标
        val resolver = dnsManager.createCascadingResolver(enableMetrics = false)
        // 使用 resolver...
    }

    /**
     * 示例 4: 定期查看统计信息
     */
    fun example4_PeriodicStats() {
        val metricsCollector = DefaultDnsMetricsCollector()
        val dnsManager = DnsManager.createDefault(metricsCollector)
        val resolver = dnsManager.createCascadingResolver()

        // 使用 resolver 进行网络请求...

        // 定期（例如每分钟）查看统计信息
        // Timer.schedule(period = 60000) {
        val stats = metricsCollector.getAggregatedStats()
        val report = DnsMetricsReporter.generateTextReport(stats)
        println(report)

        // 可以上报到服务器
        // uploadStatsToServer(stats)
        // }
    }

    /**
     * 示例 5: 查看特定解析器的性能
     */
    fun example5_ResolverPerformance() {
        val metricsCollector = DefaultDnsMetricsCollector()
        val dnsManager = DnsManager.createDefault(metricsCollector)
        val resolver = dnsManager.createCascadingResolver()

        // 使用 resolver 进行网络请求...

        // 查看 Cloudflare 解析器的性能
        val stats = metricsCollector.getAggregatedStats()
        val cloudflareStats = stats.resolverStats["Cloudflare"]
        cloudflareStats?.let {
            println("Cloudflare DNS 性能:")
            println("  成功率: ${String.format("%.2f", it.successRate * 100)}%")
            println("  平均耗时: ${String.format("%.2f", it.avgDurationMs)} ms")
        }
    }
}

