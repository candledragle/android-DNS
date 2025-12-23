package com.example.h5.ytdemo.dns.metrics

/**
 * DNS 指标报告器
 * 用于格式化输出 DNS 统计信息，便于调试和监控
 */
object DnsMetricsReporter {

    /**
     * 生成文本格式的统计报告
     */
    fun generateTextReport(stats: DnsAggregatedStats): String {
        val sb = StringBuilder()

        sb.appendLine("=".repeat(60))
        sb.appendLine("DNS 解析统计报告")
        sb.appendLine("=".repeat(60))
        sb.appendLine()

        // 总体统计
        sb.appendLine("总体统计:")
        sb.appendLine("  总请求数: ${stats.totalRequests}")
        sb.appendLine("  成功数: ${stats.totalSuccess}")
        sb.appendLine("  失败数: ${stats.totalFailure}")
        if (stats.totalRequests > 0) {
            val successRate = (stats.totalSuccess.toDouble() / stats.totalRequests * 100).let {
                String.format("%.2f", it)
            }
            sb.appendLine("  成功率: $successRate%")
        }
        sb.appendLine()

        // 解析器统计
        if (stats.resolverStats.isNotEmpty()) {
            sb.appendLine("解析器统计:")
            stats.resolverStats.values.forEach { resolverStat ->
                sb.appendLine("  ${resolverStat.resolverName}:")
                sb.appendLine("    总请求数: ${resolverStat.totalRequests}")
                sb.appendLine("    成功数: ${resolverStat.successCount}")
                sb.appendLine("    失败数: ${resolverStat.failureCount}")
                sb.appendLine("    成功率: ${String.format("%.2f", resolverStat.successRate * 100)}%")
                sb.appendLine("    平均耗时: ${String.format("%.2f", resolverStat.avgDurationMs)} ms")
                if (resolverStat.minDurationMs != Long.MAX_VALUE) {
                    sb.appendLine("    最小耗时: ${resolverStat.minDurationMs} ms")
                }
                sb.appendLine("    最大耗时: ${resolverStat.maxDurationMs} ms")
                sb.appendLine()
            }
        }

        // 域名统计（Top 10）
        if (stats.hostnameStats.isNotEmpty()) {
            sb.appendLine("域名统计 (Top 10):")
            val topHostnames = stats.hostnameStats.values
                .sortedByDescending { it.totalRequests }
                .take(10)

            topHostnames.forEach { hostnameStat ->
                sb.appendLine("  ${hostnameStat.hostname}:")
                sb.appendLine("    总请求数: ${hostnameStat.totalRequests}")
                sb.appendLine("    成功数: ${hostnameStat.successCount}")
                sb.appendLine("    失败数: ${hostnameStat.failureCount}")
                if (hostnameStat.totalRequests > 0) {
                    val successRate = (hostnameStat.successCount.toDouble() / hostnameStat.totalRequests * 100).let {
                        String.format("%.2f", it)
                    }
                    sb.appendLine("    成功率: $successRate%")
                }
                sb.appendLine("    平均耗时: ${String.format("%.2f", hostnameStat.avgDurationMs)} ms")
                sb.appendLine()
            }
        }

        sb.appendLine("=".repeat(60))

        return sb.toString()
    }

    /**
     * 生成 JSON 格式的统计报告
     */
    fun generateJsonReport(stats: DnsAggregatedStats): String {
        // 简单的 JSON 格式化（实际项目中可以使用 Gson 等库）
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"totalRequests\": ${stats.totalRequests},")
        sb.appendLine("  \"totalSuccess\": ${stats.totalSuccess},")
        sb.appendLine("  \"totalFailure\": ${stats.totalFailure},")
        sb.appendLine("  \"resolverStats\": {")
        
        val resolverEntries = stats.resolverStats.entries.joinToString(",\n") { (name, stat) ->
            """
            "${name}": {
              "totalRequests": ${stat.totalRequests},
              "successCount": ${stat.successCount},
              "failureCount": ${stat.failureCount},
              "successRate": ${String.format("%.2f", stat.successRate)},
              "avgDurationMs": ${String.format("%.2f", stat.avgDurationMs)},
              "minDurationMs": ${stat.minDurationMs},
              "maxDurationMs": ${stat.maxDurationMs}
            }""".trimIndent()
        }
        sb.appendLine(resolverEntries)
        sb.appendLine("  }")
        sb.appendLine("}")

        return sb.toString()
    }
}

