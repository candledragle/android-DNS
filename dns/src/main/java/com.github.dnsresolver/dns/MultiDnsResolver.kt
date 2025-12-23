package com.example.h5.ytdemo.dns

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * 多级 DNS 解析器
 * 按顺序尝试多个 DNS 解析器，直到成功或全部失败
 *
 * @param resolvers DNS 解析器列表，按优先级排序
 */
class MultiDnsResolver(
    private val resolvers: List<Dns>
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val errors = mutableListOf<Exception>()

        for (resolver in resolvers) {
            try {
                val result = resolver.lookup(hostname)
                if (result.isNotEmpty()) {
                    return result
                }
            } catch (e: Exception) {
                errors.add(e)
                // 继续尝试下一个解析器
            }
        }

        // 所有解析器都失败，抛出异常
        val errorMessage = buildString {
            append("所有 DNS 解析器都失败，无法解析: $hostname")
            if (errors.isNotEmpty()) {
                append("\n错误列表:")
                errors.forEachIndexed { index, error ->
                    append("\n  ${index + 1}. ${error.javaClass.simpleName}: ${error.message}")
                }
            }
        }
        throw UnknownHostException(errorMessage)
    }

    /**
     * 获取解析器数量
     */
    fun getResolverCount(): Int = resolvers.size

    /**
     * 获取所有解析器（用于测试）
     */
    fun getResolvers(): List<Dns> = resolvers.toList()
}


