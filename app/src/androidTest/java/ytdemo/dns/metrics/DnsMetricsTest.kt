package ytdemo.dns.metrics

import com.github.dnsresolver.dns.DnsManager
import com.github.dnsresolver.dns.metrics.DefaultDnsMetricsCollector
import com.github.dnsresolver.dns.metrics.DnsEvent
import com.github.dnsresolver.dns.metrics.DnsEventType
import com.github.dnsresolver.dns.metrics.DnsMetrics
import com.github.dnsresolver.dns.metrics.DnsMetricsReporter
import com.github.dnsresolver.dns.metrics.NoOpDnsMetricsCollector
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * DNS 打点功能单元测试
 */
class DnsMetricsTest {

    private lateinit var metricsCollector: DefaultDnsMetricsCollector
    private lateinit var dnsManager: DnsManager

    @Before
    fun setUp() {
        metricsCollector = DefaultDnsMetricsCollector()
        dnsManager = DnsManager.createDefault(metricsCollector)
    }

    @Test
    fun testRecordEvent() {
        val event = DnsEvent(
            type = DnsEventType.RESOLVE_START,
            hostname = "example.com"
        )

        metricsCollector.recordEvent(event)

        val stats = metricsCollector.getAggregatedStats()
        assertEquals(1, stats.totalRequests)
    }

    @Test
    fun testRecordMetrics() {
        val metrics = DnsMetrics(
            hostname = "example.com",
            success = true,
            totalDurationMs = 100,
            usedResolver = "Cloudflare",
            attemptedResolvers = listOf("System", "Cloudflare"),
            resolverDurations = mapOf("System" to 50L, "Cloudflare" to 50L),
            resolverResults = mapOf("System" to false, "Cloudflare" to true)
        )

        metricsCollector.recordMetrics(metrics)

        val stats = metricsCollector.getAggregatedStats()
        assertEquals(1, stats.totalRequests)
        assertEquals(1, stats.totalSuccess)
        assertEquals(0, stats.totalFailure)

        val cloudflareStats = stats.resolverStats["Cloudflare"]
        assertNotNull(cloudflareStats)
        assertEquals(1, cloudflareStats!!.totalRequests)
        assertEquals(1, cloudflareStats.successCount)
    }

    @Test
    fun testAggregatedStats() {
        // 模拟多次解析
        repeat(10) {
            val metrics = DnsMetrics(
                hostname = "example.com",
                success = it % 2 == 0, // 一半成功，一半失败
                totalDurationMs = (it + 1) * 10L,
                usedResolver = if (it % 2 == 0) "Cloudflare" else "Google"
            )
            metricsCollector.recordMetrics(metrics)
        }

        val stats = metricsCollector.getAggregatedStats()
        assertEquals(10, stats.totalRequests)
        assertEquals(5, stats.totalSuccess)
        assertEquals(5, stats.totalFailure)
    }

    @Test
    fun testClearStats() {
        metricsCollector.recordMetrics(
            DnsMetrics(
                hostname = "example.com",
                success = true,
                totalDurationMs = 100
            )
        )

        metricsCollector.clearStats()

        val stats = metricsCollector.getAggregatedStats()
        assertEquals(0, stats.totalRequests)
        assertEquals(0, stats.totalSuccess)
        assertEquals(0, stats.totalFailure)
        assertTrue(stats.resolverStats.isEmpty())
    }

    @Test
    fun testMetricsReporter() {
        // 添加一些测试数据
        metricsCollector.recordMetrics(
            DnsMetrics(
                hostname = "example.com",
                success = true,
                totalDurationMs = 100,
                usedResolver = "Cloudflare"
            )
        )

        val stats = metricsCollector.getAggregatedStats()
        val report = DnsMetricsReporter.generateTextReport(stats)

        assertTrue(report.contains("DNS 解析统计报告"))
        assertTrue(report.contains("example.com"))
        assertTrue(report.contains("Cloudflare"))
    }

    @Test
    fun testNoOpCollector() {
        val noOpCollector = NoOpDnsMetricsCollector

        noOpCollector.recordEvent(
            DnsEvent(
                type = DnsEventType.RESOLVE_START,
                hostname = "example.com"
            )
        )

        val stats = noOpCollector.getAggregatedStats()
        assertEquals(0, stats.totalRequests)
    }
}

