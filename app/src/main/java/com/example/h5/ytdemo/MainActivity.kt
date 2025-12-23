package com.example.h5.ytdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.h5.ytdemo.ui.theme.YTDemoTheme
import com.github.dnsresolver.dns.DnsManager
import com.github.dnsresolver.dns.SystemDnsInfoHelper
import com.github.dnsresolver.dns.metrics.DnsMetricsFacade
import com.github.dnsresolver.dns.metrics.LogDnsMetricsCollector
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private lateinit var content: () -> Unit

    /**
     * DNS Metrics Collector: Output metrics to Android Log
     * All DNS resolution events and metrics will be output to Android Log (tag: "DnsMetrics")
     *
     * The logging switch is controlled by the App via DnsMetricsFacade.setLoggingEnabled(...)
     */
    private val dnsMetricsCollector: LogDnsMetricsCollector by lazy {
        DnsMetricsFacade.createLogCollector(tag = "DnsMetrics")
    }

    /**
     * DNS 管理器：统一管理所有 DNS 解析器
     * 使用默认配置：系统 DNS 优先，然后按顺序尝试所有 DoH 提供商
     * 所有 DNS 解析指标会通过 LogDnsMetricsCollector 输出到日志
     */
    private val dnsManager: DnsManager by lazy {
        DnsManager.createDefault(
            metricsCollector = dnsMetricsCollector
        )
    }

    /**
     * 级联 DNS 解析器：按顺序尝试多个 DNS 解析器
     * 1) 系统 DNS（优先）
     * 2) Cloudflare DoH（回退）
     * 3) Google DoH（回退）
     * 4) Quad9 DoH（回退）
     * 5) Wikimedia DNS DoH（最后回退）
     */
    private val multiDns: Dns by lazy {
        dnsManager.createCascadingResolver()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let App control DNS metrics logging switch via facade
        // Here we enable logging only in debug builds
        DnsMetricsFacade.setLoggingEnabled(BuildConfig.DEBUG)

        enableEdgeToEdge()

        setContent {
            YTDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        onSendMusicRequest = { proxy, user, pass, onResult ->
                            sendTestRequest(proxy, user, pass, onResult)
                        },
                        onSendIpInfoRequest = { proxy, user, pass, onResult ->
                            sendIpInfoRequest(proxy, user, pass, onResult)
                        },
                        onTestDoh = { domain, onResult ->
                            testDoh(domain, onResult)
                        },
                        onGetSystemDns = { onResult ->
                            getSystemDnsInfo(onResult)
                        }
                    )
                }
            }
        }
    }

    // 注意：当使用 AdjustDnsMetricsCollector 时，需要添加以下生命周期回调
    /*
    override fun onResume() {
        super.onResume()
        Adjust.onResume()
    }

    override fun onPause() {
        super.onPause()
        Adjust.onPause()
    }
    */

    /**
     * 构建带可选代理与认证的 Ktor HttpClient（OkHttp 引擎）
     */
    private fun buildClientWithProxy(
        proxy: String?,
        proxyUser: String?,
        proxyPass: String?
    ): HttpClient {
        return HttpClient(OkHttp) {
            install(ContentNegotiation)

            engine {
                config {
                    // 所有请求的 DNS 解析按顺序尝试：系统 DNS → Cloudflare DoH → Google DoH → Quad9 DoH → Wikimedia DNS DoH
                    dns(multiDns)

                    if (!proxy.isNullOrBlank()) {
                        val parts = proxy.split(":")
                        if (parts.size == 2) {
                            val host = parts[0]
                            val port = parts[1].toIntOrNull() ?: 0
                            if (host.isNotBlank() && port > 0) {
                                val proxyObj = Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
                                proxy(proxyObj)

                                if (!proxyUser.isNullOrBlank() && !proxyPass.isNullOrBlank()) {
                                    val credential = Credentials.basic(proxyUser, proxyPass)
                                    addNetworkInterceptor { chain ->
                                        val newReq = chain.request()
                                            .newBuilder()
                                            .header("Proxy-Authorization", credential)
                                            .build()
                                        chain.proceed(newReq)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 通过 Cloudflare DoH 接口测试 DNS 解析:
     * https://cloudflare-dns.com/dns-query
     */
    private fun testDoh(
        domain: String,
        onResult: (String) -> Unit
    ) {
        thread {
            try {
                // 1) 先直接调用 Cloudflare DoH 的 JSON 接口，拿到解析结果（证明由三方 DNS 解析）
                // 使用 bootstrap DNS (1.1.1.1, 1.0.0.1) 避免系统 DNS 解析 cloudflare-dns.com
                // 注意：这里不能使用 DoH 来解析 cloudflare-dns.com 本身（会循环依赖），
                // 所以使用 bootstrap DNS 直接解析 cloudflare-dns.com
                val bootstrapDns: Dns = object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        return if (hostname == "cloudflare-dns.com") {
                            // 使用 Cloudflare DoH 的已知 IP，避免依赖系统 DNS
                            listOf(
                                InetAddress.getByName("104.16.132.229"),
                                InetAddress.getByName("104.16.133.229")
                            )
                        } else {
                            Dns.SYSTEM.lookup(hostname)
                        }
                    }
                }
                
                val client = OkHttpClient.Builder()
                    .dns(bootstrapDns)
                    .build()
                val url =
                    "https://cloudflare-dns.com/dns-query?name=${domain}&type=A&ct=application/dns-json"

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .build()

                val response = client.newCall(request).execute()

                val status = response.code
                val headers = response.headers.toString()
                // 使用 UTF-8 对 DoH 返回内容进行解码
                val bodyBytes = response.body?.bytes()
                val bodyStr = bodyBytes?.toString(Charsets.UTF_8).orEmpty()

                // 2) 可选：如果是测试 music.youtube.com，再用「multiDns（系统 DNS → Cloudflare DoH → Google DoH → Quad9 DoH → Wikimedia DNS DoH）」去发起真正的 HTTPS 请求
                var musicStatus: Int? = null
                var musicHeaders: String? = null
                var musicBody: String? = null

                if (domain.equals("music.youtube.com", ignoreCase = true)) {
                    // 这里把 multiDns 挂到 OkHttp 上，
                    // 按顺序尝试：系统 DNS → Cloudflare DoH → Google DoH → Quad9 DoH → Wikimedia DNS DoH，再发起请求
                    val dohClient = OkHttpClient.Builder()
                        .dns(multiDns)
                        .build()

                    val musicUrl =
                        "https://music.youtube.com/youtubei/v1/browse?key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

                    val jsonBody = """
                        {
                          "context": {
                            "client": {
                              "visitorData": "CgtTa204bnp3OTctOCjY4c-vBjIKCgJVUxIEGgAgFg%3D%3D",
                              "hl": "en",
                              "gl": "NG",
                              "clientName": "WEB_REMIX",
                              "clientVersion": "1.20250903.03.00",
                              "originalUrl": "https://music.youtube.com/",
                              "platform": "DESKTOP"
                            }
                          },
                          "browseId": "FEmusic_home"
                        }
                    """.trimIndent()

                    val mediaType = "application/json".toMediaType()
                    val body = jsonBody.toRequestBody(mediaType)

                    val musicRequest = Request.Builder()
                        .url(musicUrl)
                        .post(body)
                        .header("host", "music.youtube.com")
                        .header("origin", "https://music.youtube.com")
                        .header("referer", "https://music.youtube.com/")
                        .header(
                            "x-goog-visitor-id",
                            "CgtTa204bnp3OTctOCjY4c-vBjIKCgJVUxIEGgAgFg%3D%3D"
                        )
                        .header("x-youtube-client-name", "67")
                        .header("content-type", "application/json")
                        .header("x-youtube-client-version", "1.20250903.03.00")
                        .header("accept-language", "en, en;q=0.9")
                        .header("accept-encoding", "gzip")
                        .header("user-agent", "okhttp/5.2.1")
                        .build()

                    val musicResponse = dohClient.newCall(musicRequest).execute()

                    musicStatus = musicResponse.code
                    musicHeaders = musicResponse.headers.toString()
                    // 使用 UTF-8 对 music.youtube.com 返回内容进行解码
                    val musicBytes = musicResponse.body?.bytes()
                    musicBody = musicBytes?.toString(Charsets.UTF_8).orEmpty()
                }

                val result = buildString {
                    appendLine("DoH 状态码: $status")
                    appendLine("DoH 响应头:")
                    appendLine(headers.trim())
                    appendLine("----------")
                    appendLine("DoH 响应体前 2000 字符:")
                    appendLine(bodyStr.take(2000))

                    if (musicStatus != null) {
                        appendLine()
                        appendLine("==========")
                        appendLine("使用 DNS (系统 DNS → Cloudflare DoH → Google DoH → Quad9 DoH → Wikimedia DNS DoH) 解析后，对 music.youtube.com 发起的请求结果:")
                        appendLine("HTTP 状态码: $musicStatus")
                        appendLine("HTTP 响应头:")
                        appendLine(musicHeaders?.trim().orEmpty())
                        appendLine("----------")
                        appendLine("HTTP 响应体前 2000 字符:")
                        appendLine(musicBody?.take(2000).orEmpty())
                    }
                }

                runOnUiThread {
                    onResult(result)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    onResult("DoH 查询异常: ${e.message}")
                }
            }
        }
    }

    /**
     * 将 Python 脚本中的测试请求转换为 Android/OkHttp 实现
     */
    private fun sendTestRequest(
        proxy: String?,
        proxyUser: String?,
        proxyPass: String?,
        onResult: (String) -> Unit
    ) {
        thread {
            try {
                val url =
                    "https://music.youtube.com/youtubei/v1/browse?key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

                val client = buildClientWithProxy(proxy, proxyUser, proxyPass)

                val jsonBody = """
                    {
                      "context": {
                        "client": {
                          "visitorData": "CgtTa204bnp3OTctOCjY4c-vBjIKCgJVUxIEGgAgFg%3D%3D",
                          "hl": "en",
                          "gl": "NG",
                          "clientName": "WEB_REMIX",
                          "clientVersion": "1.20250903.03.00",
                          "originalUrl": "https://music.youtube.com/",
                          "platform": "DESKTOP"
                        }
                      },
                      "browseId": "FEmusic_home"
                    }
                """.trimIndent()

                val result = runBlocking {
                    val response = client.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(jsonBody)
                        header("host", "music.youtube.com")
                        header("origin", "https://music.youtube.com")
                        header("referer", "https://music.youtube.com/")
                        header("x-goog-visitor-id", "CgtTa204bnp3OTctOCjY4c-vBjIKCgJVUxIEGgAgFg%3D%3D")
                        header("x-youtube-client-name", "67")
                        header("content-type", "application/json")
                        header("x-youtube-client-version", "1.20250903.03.00")
                        header("accept-language", "en, en;q=0.9")
                        header("accept-encoding", "gzip")
                        header("user-agent", "okhttp/5.2.1")
                    }

                    val status = response.status.value
                    val headers = response.headers.toString()
                    val bodyStr = response.bodyAsText()

                    client.close()

                    buildString {
                        appendLine("状态码: $status")
                        appendLine("响应头:")
                        appendLine(headers.trim())
                        appendLine("----------")
                        appendLine("响应体前 2000 字符:")
                        appendLine(bodyStr.take(2000))
                    }
                }

                runOnUiThread {
                    onResult(result)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    onResult("请求异常: ${e.message}")
                }
            }
        }
    }

    /**
     * 获取系统 DNS 信息
     */
    private fun getSystemDnsInfo(onResult: (String) -> Unit) {
        thread {
            try {
                val dnsInfo = SystemDnsInfoHelper.getSystemDnsInfo(this@MainActivity)
                val allNetworksInfo = SystemDnsInfoHelper.getAllNetworksDnsInfo(this@MainActivity)

                val result = buildString {
                    appendLine("=".repeat(60))
                    appendLine("系统 DNS 信息")
                    appendLine("=".repeat(60))
                    appendLine()

                    if (dnsInfo != null) {
                        appendLine("当前活动网络:")
                        appendLine(SystemDnsInfoHelper.formatDnsInfo(dnsInfo))
                        appendLine()
                    } else {
                        appendLine("无法获取当前活动网络的 DNS 信息")
                        appendLine("（可能需要更高权限或 Android 版本限制）")
                        appendLine()
                    }

                    if (allNetworksInfo.isNotEmpty()) {
                        appendLine("所有可用网络:")
                        appendLine("-".repeat(60))
                        allNetworksInfo.forEachIndexed { index, info ->
                            appendLine("网络 ${index + 1}:")
                            appendLine(SystemDnsInfoHelper.formatDnsInfo(info))
                            appendLine()
                        }
                    } else {
                        appendLine("未找到其他可用网络")
                    }

                    appendLine("=".repeat(60))
                }

                runOnUiThread {
                    onResult(result)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    onResult("获取系统 DNS 信息失败: ${e.message}\n\n${e.stackTraceToString()}")
                }
            }
        }
    }

    /**
     * 对应 curl:
     * curl -x 149.88.186.237:2333 -U "t18626423758:mogu2018" ipinfo.io
     */
    private fun sendIpInfoRequest(
        proxy: String?,
        proxyUser: String?,
        proxyPass: String?,
        onResult: (String) -> Unit
    ) {
        thread {
            try {
                val url = "https://ipinfo.io"

                val client = buildClientWithProxy(proxy, proxyUser, proxyPass)

                val result = runBlocking {
                    val response = client.get(url) {
                        header("User-Agent", "okhttp/5.2.1")
                    }

                    val status = response.status.value
                    val headers = response.headers.toString()
                    val bodyStr = response.bodyAsText()

                    client.close()

                    buildString {
                        appendLine("状态码: $status")
                        appendLine("响应头:")
                        appendLine(headers.trim())
                        appendLine("----------")
                        appendLine("响应体前 2000 字符:")
                        appendLine(bodyStr.take(2000))
                    }
                }

                runOnUiThread {
                    onResult(result)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    onResult("请求异常: ${e.message}")
                }
            }
        }
    }
}

@Composable
private fun MainScreen(
    modifier: Modifier = Modifier,
    onSendMusicRequest: (
        proxy: String?,
        proxyUser: String?,
        proxyPass: String?,
        onResult: (String) -> Unit
    ) -> Unit,
    onSendIpInfoRequest: (
        proxy: String?,
        proxyUser: String?,
        proxyPass: String?,
        onResult: (String) -> Unit
    ) -> Unit,
    onTestDoh: (
        domain: String,
        onResult: (String) -> Unit
    ) -> Unit,
    onGetSystemDns: (
        onResult: (String) -> Unit
    ) -> Unit
) {
    var currentTab by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { currentTab = 0 },
                modifier = Modifier.weight(1f)
            ) {
                Text("HTTP 请求测试")
            }
            Button(
                onClick = { currentTab = 1 },
                modifier = Modifier.weight(1f)
            ) {
                Text("DoH 测试")
            }
        }

        when (currentTab) {
            0 -> RequestScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp),
                onSendMusicRequest = onSendMusicRequest,
                onSendIpInfoRequest = onSendIpInfoRequest
            )

            1 -> DohTestScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp),
                onTestDoh = onTestDoh,
                onGetSystemDns = onGetSystemDns
            )
        }
    }
}

@Composable
private fun RequestScreen(
    modifier: Modifier = Modifier,
    onSendMusicRequest: (
        proxy: String?,
        proxyUser: String?,
        proxyPass: String?,
        onResult: (String) -> Unit
    ) -> Unit,
    onSendIpInfoRequest: (
        proxy: String?,
        proxyUser: String?,
        proxyPass: String?,
        onResult: (String) -> Unit
    ) -> Unit
) {
    var proxy by remember { mutableStateOf("149.88.186.237:2333") }
    var proxyUser by remember { mutableStateOf("t18626423758") }
    var proxyPass by remember { mutableStateOf("mogu2018") }
    var result by remember { mutableStateOf("点击下方按钮开始请求") }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = proxy,
            onValueChange = { proxy = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("代理 ip:port（留空则直连）") }
        )

        OutlinedTextField(
            value = proxyUser,
            onValueChange = { proxyUser = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("代理用户名（可选）") }
        )

        OutlinedTextField(
            value = proxyPass,
            onValueChange = { proxyPass = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("代理密码（可选）") }
        )

        Button(
            onClick = {
                val p = proxy.ifBlank { null }
                val u = proxyUser.ifBlank { null }
                val pw = proxyPass.ifBlank { null }

                result = "请求 YouTube Music 中..."
                onSendMusicRequest(p, u, pw) { r ->
                    result = r
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("发送 YouTube Music 请求")
        }

        Button(
            onClick = {
                val p = proxy.ifBlank { null }
                val u = proxyUser.ifBlank { null }
                val pw = proxyPass.ifBlank { null }

                result = "请求 ipinfo.io 中..."
                onSendIpInfoRequest(p, u, pw) { r ->
                    result = r
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("发送 ipinfo.io 请求")
        }

        Text(
            text = result,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .verticalScroll(scrollState)
        )
    }
}

@Composable
private fun DohTestScreen(
    modifier: Modifier = Modifier,
    onTestDoh: (
        domain: String,
        onResult: (String) -> Unit
    ) -> Unit,
    onGetSystemDns: (
        onResult: (String) -> Unit
    ) -> Unit
) {
    var domain by remember { mutableStateOf("music.youtube.com") }
    var result by remember { mutableStateOf("输入域名后，点击下方按钮开始 DoH 查询") }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = domain,
            onValueChange = { domain = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("要解析的域名（例如: music.youtube.com）") }
        )

        Button(
            onClick = {
                val d = domain.ifBlank { "music.youtube.com" }
                result = "通过 Cloudflare DoH 查询中..."
                onTestDoh(d) { r ->
                    result = r
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("使用 DoH 解析域名")
        }

        Button(
            onClick = {
                result = "正在获取系统 DNS 信息..."
                onGetSystemDns { r ->
                    result = r
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("获取系统 DNS 信息")
        }

        Text(
            text = result,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .verticalScroll(scrollState)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RequestScreenPreview() {
    YTDemoTheme {
        RequestScreen(
            onSendMusicRequest = { _, _, _, onResult ->
                onResult("预览模式下的 YouTube Music 示例响应")
            },
            onSendIpInfoRequest = { _, _, _, onResult ->
                onResult("预览模式下的 ipinfo.io 示例响应")
            }
        )
    }
}