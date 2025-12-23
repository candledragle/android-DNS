package com.example.h5.ytdemo.dns

import com.example.h5.ytdemo.dns.metrics.DnsMetricsCollector
import com.example.h5.ytdemo.dns.metrics.NoOpDnsMetricsCollector
import okhttp3.Dns
import okhttp3.OkHttpClient

/**
 * DNS 管理器
 * 统一管理所有 DNS 解析器，提供便捷的访问接口
 * 便于单元测试和移植到其他框架（如 Ktor）
 */
class DnsManager(
    private val resolverFactory: DnsResolverFactory = DnsResolverFactory,
    private val useSystemDnsFirst: Boolean = true,
    private val dohProviders: List<DnsProvider> = DnsProvider.getAllProviders(),
    private val metricsCollector: DnsMetricsCollector = NoOpDnsMetricsCollector
) {
    // 缓存已创建的 DoH 解析器
    private val dohResolvers: Map<DnsProvider, Dns> by lazy {
        dohProviders.associateWith { provider ->
            resolverFactory.createDohResolver(provider)
        }
    }

    /**
     * 获取系统 DNS 解析器
     */
    val systemResolver: Dns by lazy {
        resolverFactory.createSystemResolver()
    }

    /**
     * 获取指定提供商的 DoH 解析器
     */
    fun getDohResolver(provider: DnsProvider): Dns {
        return dohResolvers[provider]
            ?: throw IllegalArgumentException("未找到 DNS 提供商: ${provider.name}")
    }

    /**
     * 获取所有 DoH 解析器
     */
    fun getAllDohResolvers(): Map<DnsProvider, Dns> {
        return dohResolvers.toMap()
    }

    /**
     * 创建级联 DNS 解析器
     * 按配置的顺序尝试多个 DNS 解析器
     *
     * @param customOrder 自定义解析器顺序（可选，默认使用 useSystemDnsFirst 和 dohProviders）
     * @param enableMetrics 是否启用打点（默认使用构造时传入的 metricsCollector）
     * @return 级联 DNS 解析器
     */
    fun createCascadingResolver(
        customOrder: List<Dns>? = null,
        enableMetrics: Boolean = true
    ): Dns {
        val resolvers = customOrder ?: buildDefaultResolverOrder()
        val resolverNames = null ?: buildDefaultResolverNames()

        return if (enableMetrics && metricsCollector != NoOpDnsMetricsCollector) {
            MetricsMultiDnsResolver(resolvers, resolverNames, metricsCollector)
        } else {
            MultiDnsResolver(resolvers)
        }
    }

    /**
     * 构建默认的解析器名称列表
     */
    private fun buildDefaultResolverNames(): List<String> {
        val names = mutableListOf<String>()

        if (useSystemDnsFirst) {
            names.add("System")
        }

        dohProviders.forEach { provider ->
            names.add(provider.name)
        }

        if (!useSystemDnsFirst) {
            names.add("System")
        }

        return names
    }

    /**
     * 构建默认的解析器顺序
     */
    private fun buildDefaultResolverOrder(): List<Dns> {
        val resolvers = mutableListOf<Dns>()

        // 根据配置决定是否优先使用系统 DNS
        if (useSystemDnsFirst) {
            resolvers.add(systemResolver)
        }

        // 添加所有 DoH 解析器
        dohProviders.forEach { provider ->
            resolvers.add(getDohResolver(provider))
        }

        // 如果系统 DNS 不在最前面，则添加到末尾
        if (!useSystemDnsFirst) {
            resolvers.add(systemResolver)
        }

        return resolvers
    }

    /**
     * 创建仅使用 DoH 的解析器（不包含系统 DNS）
     */
    fun createDohOnlyResolver(enableMetrics: Boolean = true): Dns {
        val resolvers = dohProviders.map { getDohResolver(it) }
        val resolverNames = dohProviders.map { it.name }

        return if (enableMetrics && metricsCollector != NoOpDnsMetricsCollector) {
            MetricsMultiDnsResolver(resolvers, resolverNames, metricsCollector)
        } else {
            MultiDnsResolver(resolvers)
        }
    }

    /**
     * 获取指标收集器
     */
    fun getMetricsCollector(): DnsMetricsCollector = metricsCollector

    companion object {
        /**
         * 创建默认的 DNS 管理器实例
         * 系统 DNS 优先，然后按顺序尝试所有 DoH 提供商
         *
         * @param metricsCollector 指标收集器（可选，默认不收集）
         */
        fun createDefault(metricsCollector: DnsMetricsCollector = NoOpDnsMetricsCollector): DnsManager {
            return DnsManager(metricsCollector = metricsCollector)
        }

        /**
         * 创建仅使用 DoH 的 DNS 管理器（不包含系统 DNS）
         *
         * @param metricsCollector 指标收集器（可选，默认不收集）
         */
        fun createDohOnly(metricsCollector: DnsMetricsCollector = NoOpDnsMetricsCollector): DnsManager {
            return DnsManager(
                useSystemDnsFirst = false,
                metricsCollector = metricsCollector
            )
        }

        /**
         * 创建自定义的 DNS 管理器
         *
         * @param useSystemDnsFirst 是否优先使用系统 DNS
         * @param providers DNS 提供商列表
         * @param metricsCollector 指标收集器（可选，默认不收集）
         */
        fun createCustom(
            useSystemDnsFirst: Boolean = true,
            providers: List<DnsProvider> = DnsProvider.getAllProviders(),
            metricsCollector: DnsMetricsCollector = NoOpDnsMetricsCollector
        ): DnsManager {
            return DnsManager(
                useSystemDnsFirst = useSystemDnsFirst,
                dohProviders = providers,
                metricsCollector = metricsCollector
            )
        }
    }
}

