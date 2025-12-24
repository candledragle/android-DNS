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
import androidx.compose.foundation.layout.heightIn
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
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.toByteArray
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

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

    /**
     * 为 ExoPlayer 创建带自定义 DNS 的 OkHttpClient
     */
    private val exoPlayerOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(multiDns)
            .build()
    }

    /**
     * 为 ExoPlayer 创建数据源工厂（使用自定义 DNS）
     */
    private val exoPlayerDataSourceFactory: OkHttpDataSource.Factory by lazy {
        OkHttpDataSource.Factory(exoPlayerOkHttpClient)
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
                        },
                        exoPlayerOkHttpClient = exoPlayerOkHttpClient,
                        exoPlayerDataSourceFactory = exoPlayerDataSourceFactory
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放 ExoPlayer 的 OkHttpClient 资源（如果需要）
        // 注意：exoPlayerOkHttpClient 是 lazy 初始化的，只有在使用 ExoPlayer 时才会创建
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

                // 2) 可选：如果是测试 music.youtube.com，再用「multiDns（系统 DNS → Cloudflare DoH → Google DoH → Quad9 DoH → Wikimedia DNS DoH）」去发起真正的 HTTPS 请求
                var musicStatus: Int? = null
                var musicHeaders: String? = null
                var musicBody: String? = null

                if (domain.equals("music.youtube.com", ignoreCase = true)) {
                    // 这里使用 Ktor Client + OkHttp 引擎，并将 multiDns 挂到 OkHttp 上，
                    // 按顺序尝试：系统 DNS → Cloudflare DoH → Google DoH → Quad9 DoH → Wikimedia DNS DoH，再发起请求
                    runBlocking {
                        val dohClient = HttpClient(OkHttp) {
                            engine {
                                config {
                                    dns(multiDns)
                                }
                            }
                        }

                        try {
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

                            val musicResponse = dohClient.post(musicUrl) {
                                contentType(ContentType.Application.Json)
                                setBody(jsonBody)
                                header("host", "music.youtube.com")
                                header("origin", "https://music.youtube.com")
                                header("referer", "https://music.youtube.com/")
                                header(
                                    "x-goog-visitor-id",
                                    "CgtSa204bnp3OTctOCjY4c-vBjIKCgJVUxIEGgAgFg%3D%3D"
                                )
                                header("x-youtube-client-name", "67")
                                header("content-type", "application/json")
                                header("x-youtube-client-version", "1.20250903.03.00")
                                header("accept-language", "en, en;q=0.9")
                                header("accept-encoding", "gzip")
                                header("user-agent", "okhttp/5.2.1")
                            }

                            musicStatus = musicResponse.status.value
                            musicHeaders = musicResponse.headers.toString()

                            // 以二进制读取再按 UTF-8 解码，避免 charset 解析错误
                            val musicBytes = musicResponse.bodyAsChannel().toByteArray()
                            musicBody = runCatching { musicBytes.toString(Charsets.UTF_8) }
                                .getOrElse { "Failed to decode body as UTF-8 (size=${musicBytes.size})" }
                        } finally {
                            // 确保响应已读取完毕再关闭 client
                            dohClient.close()
                        }
                    }
                }

                val result = buildString {

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
                e.printStackTrace()
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
                        header(
                            "x-goog-visitor-id",
                            "CgtTa204bnp3OTctOCjY4c-vBjIKCgJVUxIEGgAgFg%3D%3D"
                        )
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
    ) -> Unit,
    exoPlayerOkHttpClient: OkHttpClient,
    exoPlayerDataSourceFactory: OkHttpDataSource.Factory
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
            Button(
                onClick = { currentTab = 2 },
                modifier = Modifier.weight(1f)
            ) {
                Text("ExoPlayer 测试")
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

            2 -> ExoPlayerTestScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp),
                exoPlayerDataSourceFactory = exoPlayerDataSourceFactory
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

@Composable
private fun ExoPlayerTestScreen(
    modifier: Modifier = Modifier,
    exoPlayerDataSourceFactory: OkHttpDataSource.Factory
) {
    var videoUrl by remember { mutableStateOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4") }
    var statusText by remember { mutableStateOf("输入视频 URL 后点击播放") }
    val scrollState = rememberScrollState()

    // 创建 ExoPlayer 实例
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(exoPlayerDataSourceFactory)

            )
            .build()
    }

    // 管理 ExoPlayer 生命周期
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    player.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // 不自动播放，由用户控制
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    // 监听播放器状态
    LaunchedEffect(player) {
        player.addListener(object : Player.Listener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                val state = when (playbackState) {
                    Player.STATE_IDLE -> "Idle"
                    Player.STATE_BUFFERING -> "Buffering..."
                    Player.STATE_READY -> if (playWhenReady) "Playing" else "Paused"
                    Player.STATE_ENDED -> "Ended"
                    else -> "Unknown"
                }
                statusText = "Player State: $state"
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                statusText = "Error: ${error.message}\n${error.cause?.message}"
            }
        })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = videoUrl,
            onValueChange = { videoUrl = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp),
            label = { Text("视频 URL") },
            placeholder = { Text("例如: https://example.com/video.mp4") },
            maxLines = 4
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    try {
                        statusText = "Loading video..."
                        val mediaItem = MediaItem.fromUri(videoUrl)
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.play()
                        statusText = "Playing: $videoUrl"
                    } catch (e: Exception) {
                        statusText = "Error: ${e.message}"
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("播放")
            }

            Button(
                onClick = {
                    if (player.isPlaying) {
                        player.pause()
                        statusText = "Paused"
                    } else {
                        player.play()
                        statusText = "Playing"
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (player.isPlaying) "暂停" else "继续")
            }

            Button(
                onClick = {
                    player.stop()
                    statusText = "Stopped"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("停止")
            }
        }

        // ExoPlayer 视频播放器视图
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Text(
            text = statusText,
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