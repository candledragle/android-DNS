package com.example.h5.ytdemo.dns.metrics

import android.content.Context
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustEvent

/**
 * Adjust DNS 指标收集器
 * 将 DNS 解析事件和指标发送到 Adjust 平台
 */
class AdjustDnsMetricsCollector(
    private val context: Context
) : DnsMetricsCollector {

    companion object {
        /**
         * Adjust Event Tokens（需要在 Adjust Dashboard 中创建对应的事件）
         * 
         * 配置步骤：
         * 1. 登录 Adjust Dashboard (https://app.adjust.com)
         * 2. 选择你的应用
         * 3. 进入 "Events" 页面
         * 4. 点击 "Create new event" 创建以下事件：
         *    - DNS 解析开始事件
         *    - DNS 解析成功事件
         *    - DNS 解析失败事件
         *    - DNS 解析器尝试事件
         * 5. 复制每个事件的 Event Token 并填入下方对应的常量
         */
        
        /**
         * DNS 解析开始事件 Token
         * 
         * 触发时机：每次开始解析域名时触发
         * 回调参数：
         *   - hostname: 要解析的域名
         *   - total_resolvers: 总共要尝试的解析器数量
         * 
         * 例如: "abc123"
         */
        private const val EVENT_TOKEN_DNS_RESOLVE_START = ""
        
        /**
         * DNS 解析成功事件 Token
         * 
         * 触发时机：域名解析成功时触发
         * 回调参数：
         *   - hostname: 解析的域名
         *   - resolver_name: 成功使用的解析器名称（如 "system", "cloudflare", "google" 等）
         *   - duration_ms: 解析耗时（毫秒）
         *   - resolver_index: 使用的解析器索引（从 0 开始）
         *   - address_count: 解析到的 IP 地址数量
         *   - ip_1, ip_2, ip_3: 前 3 个解析到的 IP 地址
         * 
         * 例如: "def456"
         */
        private const val EVENT_TOKEN_DNS_RESOLVE_SUCCESS = ""
        
        /**
         * DNS 解析失败事件 Token
         * 
         * 触发时机：所有解析器都尝试失败后触发
         * 回调参数：
         *   - hostname: 解析失败的域名
         *   - error_type: 错误类型（异常类名）
         *   - error_message: 错误消息
         *   - duration_ms: 总耗时（毫秒）
         *   - total_resolvers: 尝试的解析器总数
         * 
         * 例如: "ghi789"
         */
        private const val EVENT_TOKEN_DNS_RESOLVE_FAILURE = ""
        
        /**
         * DNS 解析器尝试事件 Token
         * 
         * 触发时机：每次尝试使用某个解析器时触发（包括开始、成功、失败）
         * 回调参数：
         *   - hostname: 要解析的域名
         *   - resolver_name: 尝试的解析器名称
         *   - event_type: 事件类型（RESOLVER_ATTEMPT_START/SUCCESS/FAILURE）
         *   - duration_ms: 该解析器的耗时（毫秒）
         *   - resolver_index: 解析器索引
         *   - error_type: 错误类型（失败时）
         *   - error_message: 错误消息（失败时）
         * 
         * 例如: "jkl012"
         */
        private const val EVENT_TOKEN_DNS_RESOLVER_ATTEMPT = ""
    }

    override fun recordEvent(event: DnsEvent) {
        when (event.type) {
            DnsEventType.RESOLVE_START -> {
                trackDnsResolveStart(event)
            }
            DnsEventType.RESOLVE_SUCCESS -> {
                trackDnsResolveSuccess(event)
            }
            DnsEventType.RESOLVE_FAILURE -> {
                trackDnsResolveFailure(event)
            }
            DnsEventType.RESOLVER_ATTEMPT_START,
            DnsEventType.RESOLVER_ATTEMPT_SUCCESS,
            DnsEventType.RESOLVER_ATTEMPT_FAILURE -> {
                trackDnsResolverAttempt(event)
            }
        }
    }

    override fun recordMetrics(metrics: DnsMetrics) {
        // 根据解析结果发送不同的事件
        if (metrics.success) {
            trackDnsResolveSuccessMetrics(metrics)
        } else {
            trackDnsResolveFailureMetrics(metrics)
        }
    }

    /**
     * 跟踪 DNS 解析开始事件
     */
    private fun trackDnsResolveStart(event: DnsEvent) {
        if (EVENT_TOKEN_DNS_RESOLVE_START.isNotEmpty()) {
            val adjustEvent = AdjustEvent(EVENT_TOKEN_DNS_RESOLVE_START)
            adjustEvent.addCallbackParameter("hostname", event.hostname)
            adjustEvent.addCallbackParameter("total_resolvers", event.totalResolvers?.toString() ?: "0")
            Adjust.trackEvent(adjustEvent)
        }
    }

    /**
     * 跟踪 DNS 解析成功事件
     */
    private fun trackDnsResolveSuccess(event: DnsEvent) {
        if (EVENT_TOKEN_DNS_RESOLVE_SUCCESS.isNotEmpty()) {
            val adjustEvent = AdjustEvent(EVENT_TOKEN_DNS_RESOLVE_SUCCESS)
            adjustEvent.addCallbackParameter("hostname", event.hostname)
            adjustEvent.addCallbackParameter("resolver_name", event.resolverName ?: "unknown")
            adjustEvent.addCallbackParameter("duration_ms", event.durationMs?.toString() ?: "0")
            adjustEvent.addCallbackParameter("resolver_index", event.resolverIndex?.toString() ?: "0")
            adjustEvent.addCallbackParameter("address_count", event.addresses?.size?.toString() ?: "0")
            
            // 添加 IP 地址信息（前 3 个）
            event.addresses?.take(3)?.forEachIndexed { index, address ->
                adjustEvent.addCallbackParameter("ip_${index + 1}", address.hostAddress)
            }
            
            Adjust.trackEvent(adjustEvent)
        }
    }

    /**
     * 跟踪 DNS 解析失败事件
     */
    private fun trackDnsResolveFailure(event: DnsEvent) {
        if (EVENT_TOKEN_DNS_RESOLVE_FAILURE.isNotEmpty()) {
            val adjustEvent = AdjustEvent(EVENT_TOKEN_DNS_RESOLVE_FAILURE)
            adjustEvent.addCallbackParameter("hostname", event.hostname)
            adjustEvent.addCallbackParameter("error_type", event.error?.javaClass?.simpleName ?: "unknown")
            adjustEvent.addCallbackParameter("error_message", event.error?.message ?: "unknown")
            adjustEvent.addCallbackParameter("duration_ms", event.durationMs?.toString() ?: "0")
            adjustEvent.addCallbackParameter("total_resolvers", event.totalResolvers?.toString() ?: "0")
            Adjust.trackEvent(adjustEvent)
        }
    }

    /**
     * 跟踪 DNS 解析器尝试事件
     */
    private fun trackDnsResolverAttempt(event: DnsEvent) {
        if (EVENT_TOKEN_DNS_RESOLVER_ATTEMPT.isNotEmpty()) {
            val adjustEvent = AdjustEvent(EVENT_TOKEN_DNS_RESOLVER_ATTEMPT)
            adjustEvent.addCallbackParameter("hostname", event.hostname)
            adjustEvent.addCallbackParameter("resolver_name", event.resolverName ?: "unknown")
            adjustEvent.addCallbackParameter("event_type", event.type.name)
            adjustEvent.addCallbackParameter("duration_ms", event.durationMs?.toString() ?: "0")
            adjustEvent.addCallbackParameter("resolver_index", event.resolverIndex?.toString() ?: "0")
            
            if (event.error != null) {
                adjustEvent.addCallbackParameter("error_type", event.error.javaClass.simpleName)
                adjustEvent.addCallbackParameter("error_message", event.error.message ?: "")
            }
            
            Adjust.trackEvent(adjustEvent)
        }
    }

    /**
     * 跟踪 DNS 解析成功指标
     */
    private fun trackDnsResolveSuccessMetrics(metrics: DnsMetrics) {
        if (EVENT_TOKEN_DNS_RESOLVE_SUCCESS.isNotEmpty()) {
            val adjustEvent = AdjustEvent(EVENT_TOKEN_DNS_RESOLVE_SUCCESS)
            adjustEvent.addCallbackParameter("hostname", metrics.hostname)
            adjustEvent.addCallbackParameter("used_resolver", metrics.usedResolver ?: "unknown")
            adjustEvent.addCallbackParameter("duration_ms", metrics.totalDurationMs.toString())
            adjustEvent.addCallbackParameter("address_count", metrics.addresses?.size?.toString() ?: "0")
            adjustEvent.addCallbackParameter("attempted_count", metrics.attemptedResolvers.size.toString())
            
            // 添加尝试的解析器列表
            metrics.attemptedResolvers.forEachIndexed { index, resolver ->
                adjustEvent.addCallbackParameter("attempted_${index + 1}", resolver)
            }
            
            // 添加每个解析器的耗时
            metrics.resolverDurations.forEach { (resolver, duration) ->
                adjustEvent.addCallbackParameter("${resolver}_duration_ms", duration.toString())
            }
            
            Adjust.trackEvent(adjustEvent)
        }
    }

    /**
     * 跟踪 DNS 解析失败指标
     */
    private fun trackDnsResolveFailureMetrics(metrics: DnsMetrics) {
        if (EVENT_TOKEN_DNS_RESOLVE_FAILURE.isNotEmpty()) {
            val adjustEvent = AdjustEvent(EVENT_TOKEN_DNS_RESOLVE_FAILURE)
            adjustEvent.addCallbackParameter("hostname", metrics.hostname)
            adjustEvent.addCallbackParameter("error_type", metrics.error?.javaClass?.simpleName ?: "unknown")
            adjustEvent.addCallbackParameter("error_message", metrics.error?.message ?: "unknown")
            adjustEvent.addCallbackParameter("duration_ms", metrics.totalDurationMs.toString())
            adjustEvent.addCallbackParameter("attempted_count", metrics.attemptedResolvers.size.toString())
            
            // 添加所有尝试的解析器
            metrics.attemptedResolvers.forEachIndexed { index, resolver ->
                adjustEvent.addCallbackParameter("attempted_${index + 1}", resolver)
                val success = metrics.resolverResults[resolver] ?: false
                adjustEvent.addCallbackParameter("${resolver}_success", if (success) "true" else "false")
            }
            
            Adjust.trackEvent(adjustEvent)
        }
    }

    override fun getAggregatedStats(): DnsAggregatedStats {
        // Adjust 不提供本地聚合统计，返回空统计
        // 统计数据在 Adjust Dashboard 中查看
        return DnsAggregatedStats()
    }

    override fun clearStats() {
        // Adjust 不需要清空本地统计，数据在服务器端
        // 此方法保留接口兼容性
    }
}

