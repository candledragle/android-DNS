package com.github.dnsresolver.dns

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Build

/**
 * 系统 DNS 信息
 */
data class SystemDnsInfo(
    /**
     * DNS 服务器列表
     */
    val dnsServers: List<String>,

    /**
     * 网络类型（如 "WIFI", "MOBILE" 等）
     */
    val networkType: String? = null,

    /**
     * 是否使用 VPN
     */
    val isVpn: Boolean = false,

    /**
     * 网络接口名称
     */
    val interfaceName: String? = null
)

/**
 * 系统 DNS 信息获取工具
 * 用于获取 Android 系统当前配置的 DNS 服务器信息
 */
object SystemDnsInfoHelper {

    /**
     * 获取系统 DNS 信息
     *
     * @param context Android Context
     * @return 系统 DNS 信息，如果无法获取则返回 null
     */
    fun getSystemDnsInfo(context: Context): SystemDnsInfo? {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0+ 使用新 API
                getSystemDnsInfoModern(connectivityManager)
            } else {
                // Android 5.x 及以下使用旧方法
                getSystemDnsInfoLegacy()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Android 6.0+ 获取 DNS 信息的方法
     */
    @Suppress("DEPRECATION")
    private fun getSystemDnsInfoModern(connectivityManager: ConnectivityManager): SystemDnsInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork ?: return null
            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null

            // 获取网络类型
            val networkType = when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE"
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                else -> "UNKNOWN"
            }

            val isVpn = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

            // 获取 LinkProperties（包含 DNS 信息）
            val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(activeNetwork)
            val dnsServers = linkProperties?.dnsServers?.map { it.hostAddress } ?: emptyList()
            val interfaceName = linkProperties?.interfaceName

            return SystemDnsInfo(
                dnsServers = dnsServers,
                networkType = networkType,
                isVpn = isVpn,
                interfaceName = interfaceName
            )
        }
        return null
    }

    /**
     * Android 5.x 及以下获取 DNS 信息的方法（通过系统属性）
     */
    private fun getSystemDnsInfoLegacy(): SystemDnsInfo? {
        val dnsServers = mutableListOf<String>()

        // 尝试通过系统属性获取 DNS
        try {
            val process = Runtime.getRuntime().exec("getprop")
            val reader = process.inputStream.bufferedReader()
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.contains("net.dns")) {
                        val value = line.substringAfter(":").trim().removeSurrounding("[", "]")
                        if (value.isNotEmpty() && value != "null") {
                            dnsServers.add(value)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略错误
        }

        return if (dnsServers.isNotEmpty()) {
            SystemDnsInfo(
                dnsServers = dnsServers,
                networkType = "UNKNOWN",
                isVpn = false
            )
        } else {
            null
        }
    }

    /**
     * 获取所有可用网络的 DNS 信息（Android 6.0+ 有效）
     *
     * @param context Android Context
     * @return 所有网络的 DNS 信息列表
     */
    fun getAllNetworksDnsInfo(context: Context): List<SystemDnsInfo> {
        val result = mutableListOf<SystemDnsInfo>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

                val allNetworks = connectivityManager.allNetworks
                for (network in allNetworks) {
                    val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                    val linkProperties = connectivityManager.getLinkProperties(network)

                    if (networkCapabilities != null && linkProperties != null) {
                        val networkType = when {
                            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE"
                            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                            else -> "UNKNOWN"
                        }

                        val dnsServers = linkProperties.dnsServers.map { it.hostAddress }
                        val isVpn = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

                        if (dnsServers.isNotEmpty()) {
                            result.add(
                                SystemDnsInfo(
                                    dnsServers = dnsServers,
                                    networkType = networkType,
                                    isVpn = isVpn,
                                    interfaceName = linkProperties.interfaceName
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // ignore errors, return what we have
            }
        }

        return result
    }

    /**
     * Format a single SystemDnsInfo instance into a human‑readable string.
     */
    fun formatDnsInfo(info: SystemDnsInfo): String {
        val sb = StringBuilder()
        sb.appendLine("Network type: ${info.networkType ?: "UNKNOWN"}")
        sb.appendLine("Is VPN: ${if (info.isVpn) "YES" else "NO"}")
        if (info.interfaceName != null) {
            sb.appendLine("Interface: ${info.interfaceName}")
        }
        sb.appendLine("DNS servers:")
        if (info.dnsServers.isEmpty()) {
            sb.appendLine("  (none)")
        } else {
            info.dnsServers.forEachIndexed { index, dns ->
                sb.appendLine("  ${index + 1}. $dns")
            }
        }
        return sb.toString()
    }

    /**
     * Format a list of SystemDnsInfo instances into a human‑readable string.
     */
    fun formatAllDnsInfo(list: List<SystemDnsInfo>): String {
        if (list.isEmpty()) return "No DNS info available"

        val sb = StringBuilder()
        list.forEachIndexed { index, info ->
            sb.appendLine("===== Network #${index + 1} =====")
            sb.appendLine(formatDnsInfo(info))
        }
        return sb.toString()
    }
}


