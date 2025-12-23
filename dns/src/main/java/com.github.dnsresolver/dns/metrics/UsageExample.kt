package com.example.h5.ytdemo.dns.metrics

import com.github.dnsresolver.dns.DnsManager
import com.github.dnsresolver.dns.metrics.DefaultDnsMetricsCollector

/**
 * 用法示例
 * 展示如何在代码中集成 DnsManager 和 MetricsCollector
 */
object UsageExample {

    /**
     * 使用默认配置（系统 DNS + 多个 DoH 提供商）
     */
    fun createDefaultManagerWithMetrics(): DnsManager {
        val collector = DefaultDnsMetricsCollector()
        return DnsManager.createDefault(metricsCollector = collector)
    }
}


