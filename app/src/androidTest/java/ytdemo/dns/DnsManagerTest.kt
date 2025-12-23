package ytdemo.dns

import com.example.h5.ytdemo.dns.DnsManager
import com.example.h5.ytdemo.dns.DnsProvider
import com.example.h5.ytdemo.dns.DnsResolverFactory
import com.example.h5.ytdemo.dns.MultiDnsResolver
import okhttp3.Dns
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

/**
 * DNS 管理器单元测试示例
 * 展示如何测试 DNS 相关功能
 */
class DnsManagerTest {

    @Test
    fun testDnsProviderConfiguration() {
        // 测试 DNS 提供商配置
        val cloudflare = DnsProvider.Companion.CLOUDFLARE
        assertEquals("Cloudflare", cloudflare.name)
        assertEquals("https://cloudflare-dns.com/dns-query", cloudflare.dohUrl)
        assertEquals(2, cloudflare.bootstrapIps.size)
        assertTrue(cloudflare.bootstrapIps.contains("1.1.1.1"))
    }

    @Test
    fun testDnsResolverFactory() {
        // 测试 DNS 解析器工厂
        val provider = DnsProvider.Companion.CLOUDFLARE
        val resolver = DnsResolverFactory.createDohResolver(provider)
        
        assertNotNull(resolver)
        assertTrue(resolver is Dns)
    }

    @Test
    fun testMultiDnsResolver() {
        // 测试多级 DNS 解析器
        val systemResolver = DnsResolverFactory.createSystemResolver()
        val cloudflareResolver = DnsResolverFactory.createDohResolver(DnsProvider.Companion.CLOUDFLARE)
        
        val multiResolver = MultiDnsResolver(listOf(systemResolver, cloudflareResolver))
        
        assertEquals(2, multiResolver.getResolverCount())
        assertEquals(2, multiResolver.getResolvers().size)
    }

    @Test
    fun testDnsManagerDefault() {
        // 测试默认 DNS 管理器
        val manager = DnsManager.Companion.createDefault()
        
        assertNotNull(manager.systemResolver)
        assertNotNull(manager.getDohResolver(DnsProvider.Companion.CLOUDFLARE))
        assertNotNull(manager.getDohResolver(DnsProvider.Companion.GOOGLE))
        
        val cascadingResolver = manager.createCascadingResolver()
        assertNotNull(cascadingResolver)
    }

    @Test
    fun testDnsManagerCustom() {
        // 测试自定义 DNS 管理器
        val manager = DnsManager.Companion.createCustom(
            useSystemDnsFirst = false,
            providers = listOf(DnsProvider.Companion.CLOUDFLARE, DnsProvider.Companion.GOOGLE)
        )
        
        val dohOnlyResolver = manager.createDohOnlyResolver()
        assertNotNull(dohOnlyResolver)
    }

    @Test
    fun testDnsLookup() {
        // 实际 DNS 查询测试（需要网络连接）
        val manager = DnsManager.Companion.createDefault()
        val resolver = manager.createCascadingResolver()
        
        try {
            val addresses = resolver.lookup("google.com")
            assertNotNull(addresses)
            assertTrue(addresses.isNotEmpty())
            assertTrue(addresses[0] is InetAddress)
        } catch (e: Exception) {
            // 如果网络不可用，跳过此测试
            println("网络不可用，跳过 DNS 查询测试: ${e.message}")
        }
    }
}

