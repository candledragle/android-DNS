package com.github.dnsresolver.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * 协程友好的 DNS 适配器
 * 将 DNS 管理器适配为协程接口，便于在 Kotlin 协程环境中使用
 * 不依赖任何特定框架（如 Ktor），可在任何支持协程的项目中使用
 *
 * 使用示例：
 * ```kotlin
 * val dnsManager = DnsManager.createDefault()
 * val dnsAdapter = CoroutineDnsAdapter(dnsManager)
 *
 * // 在协程中使用
 * lifecycleScope.launch {
 *     val addresses = dnsAdapter.resolve("example.com")
 *     val firstIp = dnsAdapter.resolveFirst("example.com")
 *     val ipv4 = dnsAdapter.resolveIPv4("example.com")
 * }
 * ```
 */
class CoroutineDnsAdapter(
    private val dnsManager: DnsManager
) {
    /**
     * 创建级联 DNS 解析器
     */
    private val cascadingResolver: Dns by lazy {
        dnsManager.createCascadingResolver()
    }

    /**
     * 解析域名
     *
     * @param hostname 要解析的域名
     * @return IP 地址列表
     */
    suspend fun resolve(hostname: String): List<InetAddress> = withContext(Dispatchers.IO) {
        try {
            cascadingResolver.lookup(hostname)
        } catch (e: Exception) {
            // UnknownHostException 在标准库中没有 (String, Throwable) 构造函数，
            // 这里通过 initCause 关联原始异常，避免构造器重载歧义
            val ex = UnknownHostException("无法解析域名: $hostname")
            ex.initCause(e)
            throw ex
        }
    }

    /**
     * 解析域名并返回第一个 IP 地址
     */
    suspend fun resolveFirst(hostname: String): InetAddress {
        val addresses = resolve(hostname)
        if (addresses.isEmpty()) {
            throw UnknownHostException("域名解析结果为空: $hostname")
        }
        return addresses[0]
    }

    /**
     * 解析域名并返回 IPv4 地址（如果可用）
     */
    suspend fun resolveIPv4(hostname: String): InetAddress? {
        val addresses = resolve(hostname)
        return addresses.firstOrNull { it.address.size == 4 } // IPv4 地址长度为 4 字节
    }

    /**
     * 解析域名并返回 IPv6 地址（如果可用）
     */
    suspend fun resolveIPv6(hostname: String): InetAddress? {
        val addresses = resolve(hostname)
        return addresses.firstOrNull { it.address.size == 16 } // IPv6 地址长度为 16 字节
    }
}


