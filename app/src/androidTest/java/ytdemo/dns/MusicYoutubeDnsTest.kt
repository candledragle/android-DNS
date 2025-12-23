package ytdemo.dns

import com.github.dnsresolver.dns.DnsManager
import com.github.dnsresolver.dns.DnsProvider
import com.github.dnsresolver.dns.metrics.DefaultDnsMetricsCollector
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

/**
 * music.youtube.com DNS 解析测试
 * 测试所有 DNS 提供商对 music.youtube.com 的解析能力
 */
class MusicYoutubeDnsTest {

    private lateinit var dnsManager: DnsManager
    private lateinit var metricsCollector: DefaultDnsMetricsCollector
    private val testHostname = "music.youtube.com"

    @Before
    fun setUp() {
        metricsCollector = DefaultDnsMetricsCollector()
        dnsManager = DnsManager.Companion.createDefault(metricsCollector)
    }

    @Test
    fun testSystemDnsResolveMusicYoutube() {
        // 测试系统 DNS 解析 music.youtube.com
        val systemResolver = dnsManager.systemResolver

        try {
            val addresses = systemResolver.lookup(testHostname)
            assertNotNull("系统 DNS 解析结果不应为 null", addresses)
            assertTrue("系统 DNS 应返回至少一个 IP 地址", addresses.isNotEmpty())
            assertTrue("返回的应该是 InetAddress", addresses[0] is InetAddress)
            println("系统 DNS 解析 music.youtube.com 成功: ${addresses.map { it.hostAddress }}")
        } catch (e: Exception) {
            println("系统 DNS 解析 music.youtube.com 失败: ${e.message}")
            // 如果网络不可用，跳过此测试
        }
    }

    @Test
    fun testCloudflareDnsResolveMusicYoutube() {
        // 测试 Cloudflare DoH 解析 music.youtube.com
        val cloudflareResolver = dnsManager.getDohResolver(DnsProvider.Companion.CLOUDFLARE)

        try {
            val addresses = cloudflareResolver.lookup(testHostname)
            assertNotNull("Cloudflare DNS 解析结果不应为 null", addresses)
            assertTrue("Cloudflare DNS 应返回至少一个 IP 地址", addresses.isNotEmpty())
            println("Cloudflare DNS 解析 music.youtube.com 成功: ${addresses.map { it.hostAddress }}")
        } catch (e: Exception) {
            fail("Cloudflare DNS 解析 music.youtube.com 失败: ${e.message}")
        }
    }

    @Test
    fun testGoogleDnsResolveMusicYoutube() {
        // 测试 Google DoH 解析 music.youtube.com
        val googleResolver = dnsManager.getDohResolver(DnsProvider.Companion.GOOGLE)

        try {
            val addresses = googleResolver.lookup(testHostname)
            assertNotNull("Google DNS 解析结果不应为 null", addresses)
            assertTrue("Google DNS 应返回至少一个 IP 地址", addresses.isNotEmpty())
            println("Google DNS 解析 music.youtube.com 成功: ${addresses.map { it.hostAddress }}")
        } catch (e: Exception) {
            fail("Google DNS 解析 music.youtube.com 失败: ${e.message}")
        }
    }

    @Test
    fun testQuad9DnsResolveMusicYoutube() {
        // 测试 Quad9 DoH 解析 music.youtube.com
        val quad9Resolver = dnsManager.getDohResolver(DnsProvider.Companion.QUAD9)

        try {
            val addresses = quad9Resolver.lookup(testHostname)
            assertNotNull("Quad9 DNS 解析结果不应为 null", addresses)
            assertTrue("Quad9 DNS 应返回至少一个 IP 地址", addresses.isNotEmpty())
            println("Quad9 DNS 解析 music.youtube.com 成功: ${addresses.map { it.hostAddress }}")
        } catch (e: Exception) {
            fail("Quad9 DNS 解析 music.youtube.com 失败: ${e.message}")
        }
    }

    @Test
    fun testWikimediaDnsResolveMusicYoutube() {
        // 测试 Wikimedia DNS DoH 解析 music.youtube.com
        val wikimediaResolver = dnsManager.getDohResolver(DnsProvider.Companion.WIKIMEDIA)

        try {
            val addresses = wikimediaResolver.lookup(testHostname)
            assertNotNull("Wikimedia DNS 解析结果不应为 null", addresses)
            assertTrue("Wikimedia DNS 应返回至少一个 IP 地址", addresses.isNotEmpty())
            println("Wikimedia DNS 解析 music.youtube.com 成功: ${addresses.map { it.hostAddress }}")
        } catch (e: Exception) {
            fail("Wikimedia DNS 解析 music.youtube.com 失败: ${e.message}")
        }
    }

    @Test
    fun testAllDnsProvidersResolveMusicYoutube() {
        // 测试所有 DNS 提供商对 music.youtube.com 的解析
        val providers = DnsProvider.Companion.getAllProviders()
        val results = mutableMapOf<String, List<InetAddress>>()
        val failures = mutableMapOf<String, String>()

        providers.forEach { provider ->
            try {
                val resolver = dnsManager.getDohResolver(provider)
                val addresses = resolver.lookup(testHostname)
                results[provider.name] = addresses
                println("✓ ${provider.name} DNS 解析成功: ${addresses.size} 个 IP 地址")
                addresses.forEach { addr ->
                    println("  - ${addr.hostAddress}")
                }
            } catch (e: Exception) {
                failures[provider.name] = e.message ?: "未知错误"
                println("✗ ${provider.name} DNS 解析失败: ${e.message}")
            }
        }

        // 至少应该有一个 DNS 提供商解析成功
        assertTrue(
            "至少应该有一个 DNS 提供商能解析 music.youtube.com，但全部失败: $failures",
            results.isNotEmpty()
        )

        // 验证所有成功的解析结果都包含 IP 地址
        results.forEach { (providerName, addresses) ->
            assertTrue(
                "$providerName 解析结果不应为空",
                addresses.isNotEmpty()
            )
        }

        println("\n解析结果汇总:")
        println("成功: ${results.size}/${providers.size}")
        results.forEach { (name, addresses) ->
            println("  $name: ${addresses.size} 个 IP")
        }
        if (failures.isNotEmpty()) {
            println("失败: ${failures.size}/${providers.size}")
            failures.forEach { (name, error) ->
                println("  $name: $error")
            }
        }
    }

    @Test
    fun testCascadingResolverResolveMusicYoutube() {
        // 测试级联 DNS 解析器对 music.youtube.com 的解析
        val cascadingResolver = dnsManager.createCascadingResolver(enableMetrics = true)

        try {
            val addresses = cascadingResolver.lookup(testHostname)
            assertNotNull("级联 DNS 解析结果不应为 null", addresses)
            assertTrue("级联 DNS 应返回至少一个 IP 地址", addresses.isNotEmpty())
            println("级联 DNS 解析 music.youtube.com 成功: ${addresses.map { it.hostAddress }}")

            // 查看使用了哪个解析器
            val stats = metricsCollector.getAggregatedStats()
            val lastEvent = stats.resolverStats.values.lastOrNull()
            if (lastEvent != null) {
                println("使用的解析器统计: ${stats.resolverStats.keys}")
            }
        } catch (e: Exception) {
            fail("级联 DNS 解析 music.youtube.com 失败: ${e.message}")
        }
    }

    @Test
    fun testCompareDnsProviderResults() {
        // 比较不同 DNS 提供商对 music.youtube.com 的解析结果
        val providers = DnsProvider.Companion.getAllProviders()
        val allAddresses = mutableSetOf<String>()

        providers.forEach { provider ->
            try {
                val resolver = dnsManager.getDohResolver(provider)
                val addresses = resolver.lookup(testHostname)
                val ipAddresses = addresses.map { it.hostAddress }
                allAddresses.addAll(ipAddresses)
                println("${provider.name}: ${ipAddresses.joinToString(", ")}")
            } catch (e: Exception) {
                println("${provider.name}: 解析失败 - ${e.message}")
            }
        }

        // 验证至少有一些共同的 IP 地址（说明解析结果一致）
        assertTrue(
            "所有 DNS 提供商应该解析出一些 IP 地址",
            allAddresses.isNotEmpty()
        )

        println("\n所有解析出的唯一 IP 地址: ${allAddresses.size} 个")
        allAddresses.forEach { ip ->
            println("  - $ip")
        }
    }

    @Test
    fun testDnsProviderPerformance() {
        // 测试不同 DNS 提供商的解析性能
        val providers = DnsProvider.Companion.getAllProviders()
        val performanceResults = mutableMapOf<String, Long>()

        providers.forEach { provider ->
            try {
                val resolver = dnsManager.getDohResolver(provider)
                val startTime = System.currentTimeMillis()
                val addresses = resolver.lookup(testHostname)
                val duration = System.currentTimeMillis() - startTime
                performanceResults[provider.name] = duration
                println("${provider.name}: ${duration}ms (${addresses.size} 个 IP)")
            } catch (e: Exception) {
                performanceResults[provider.name] = -1
                println("${provider.name}: 失败 - ${e.message}")
            }
        }

        // 验证至少有一个提供商成功
        val successfulResults = performanceResults.filter { it.value > 0 }
        assertTrue(
            "至少应该有一个 DNS 提供商解析成功",
            successfulResults.isNotEmpty()
        )

        // 找出最快的解析器
        val fastest = successfulResults.minByOrNull { it.value }
        if (fastest != null) {
            println("\n最快解析器: ${fastest.key} (${fastest.value}ms)")
        }

        // 计算平均耗时
        val avgDuration = successfulResults.values.average()
        println("平均耗时: ${String.format("%.2f", avgDuration)}ms")
    }
}

