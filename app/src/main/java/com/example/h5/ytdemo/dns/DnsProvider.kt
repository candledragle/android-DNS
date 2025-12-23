package com.example.h5.ytdemo.dns

import java.net.InetAddress

/**
 * DNS 提供商配置
 * 定义不同 DNS 服务的 DoH 端点和 Bootstrap IP 地址
 */
data class DnsProvider(
    val name: String,
    val dohUrl: String,
    val bootstrapIps: List<String>
) {
    companion object {
        val CLOUDFLARE = DnsProvider(
            name = "Cloudflare",
            dohUrl = "https://cloudflare-dns.com/dns-query",
            bootstrapIps = listOf("1.1.1.1", "1.0.0.1")
        )

        val GOOGLE = DnsProvider(
            name = "Google",
            dohUrl = "https://dns.google/dns-query",
            bootstrapIps = listOf("8.8.8.8", "8.8.4.4")
        )

        val QUAD9 = DnsProvider(
            name = "Quad9",
            dohUrl = "https://dns.quad9.net/dns-query",
            bootstrapIps = listOf("9.9.9.9", "149.112.112.112")
        )

        val WIKIMEDIA = DnsProvider(
            name = "Wikimedia",
            dohUrl = "https://wikimedia-dns.org/dns-query",
            bootstrapIps = listOf("185.71.138.138", "185.71.139.139")
        )

        /**
         * 获取所有可用的 DNS 提供商
         */
        fun getAllProviders(): List<DnsProvider> {
            return listOf(CLOUDFLARE, GOOGLE, QUAD9, WIKIMEDIA)
        }
    }

    /**
     * 将 bootstrap IP 字符串列表转换为 InetAddress 列表
     */
    fun getBootstrapAddresses(): List<InetAddress> {
        return bootstrapIps.map { InetAddress.getByName(it) }
    }
}

