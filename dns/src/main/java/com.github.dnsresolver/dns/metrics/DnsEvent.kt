package com.github.dnsresolver.dns.metrics

import java.net.InetAddress

/**
 * DNS 解析事件类型
 */
enum class DnsEventType {
    /**
     * 开始解析
     */
    RESOLVE_START,

    /**
     * 单个解析器尝试开始
     */
    RESOLVER_ATTEMPT_START,

    /**
     * 单个解析器尝试成功
     */
    RESOLVER_ATTEMPT_SUCCESS,

    /**
     * 单个解析器尝试失败
     */
    RESOLVER_ATTEMPT_FAILURE,

    /**
     * 解析成功
     */
    RESOLVE_SUCCESS,

    /**
     * 解析失败（所有解析器都失败）
     */
    RESOLVE_FAILURE
}

/**
 * DNS 解析事件
 */
data class DnsEvent(
    /**
     * 事件类型
     */
    val type: DnsEventType,

    /**
     * 要解析的域名
     */
    val hostname: String,

    /**
     * 解析器名称（如 "System", "Cloudflare", "Google" 等）
     */
    val resolverName: String? = null,

    /**
     * 解析结果（成功时）
     */
    val addresses: List<InetAddress>? = null,

    /**
     * 错误信息（失败时）
     */
    val error: Throwable? = null,

    /**
     * 耗时（毫秒）
     */
    val durationMs: Long? = null,

    /**
     * 尝试的解析器索引（从 0 开始）
     */
    val resolverIndex: Int? = null,

    /**
     * 总解析器数量
     */
    val totalResolvers: Int? = null,

    /**
     * 时间戳（毫秒）
     */
    val timestamp: Long = System.currentTimeMillis()
)


