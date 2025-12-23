package com.example.h5.ytdemo.dns

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * DNS 解析器工厂
 * 用于创建不同类型的 DNS 解析器，便于测试和移植
 */
object DnsResolverFactory {

    /**
     * 创建 DoH (DNS over HTTPS) 解析器
     *
     * @param provider DNS 提供商配置
     * @param bootstrapClient 用于发起 DoH 请求的基础 OkHttpClient（可选，默认创建新的）
     * @return DoH DNS 解析器
     */
    fun createDohResolver(
        provider: DnsProvider,
        bootstrapClient: OkHttpClient? = null
    ): Dns {
        val client = bootstrapClient ?: OkHttpClient.Builder()
            .cache(null)
            .build()

        return DnsOverHttps.Builder()
            .client(client)
            .url(provider.dohUrl.toHttpUrl())
            .bootstrapDnsHosts(*provider.getBootstrapAddresses().toTypedArray())
            .build()
    }

    /**
     * 创建系统 DNS 解析器（包装器，便于统一接口）
     */
    fun createSystemResolver(): Dns {
        return Dns.SYSTEM
    }
}

