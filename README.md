# YTDemo – DNS over HTTPS & Multi-Resolver Demo

This project contains:

- An **Android demo app** showing:
  - HTTP request testing (YouTube Music / ipinfo.io)
  - DNS over HTTPS (DoH) resolution tests
  - System DNS information display
- A reusable **DNS library module** (`dns`) that can be extracted and open-sourced independently.

---

## Modules

- `app`
  - Jetpack Compose UI
  - Uses Ktor Client (OkHttp engine) for HTTP requests
  - Uses `:dns` module for all DNS resolution
  - Screens:
    - **HTTP Request Test** (YouTube Music / ipinfo.io, optional proxy)
    - **DoH Test** (Cloudflare DoH + multi-resolver)
    - **System DNS Info** (active network + all networks)

- `dns`
  - Android Library module
  - Namespace: `com.github.dnsresolver.dns`
  - Designed to be reusable and easy to open-source or integrate in other apps

---

## Features

### 1. HTTP Request Test (with optional proxy)

Entry: `MainActivity` → Tab “HTTP 请求测试”

- Uses **Ktor Client with OkHttp engine** to send requests to:
  - `https://music.youtube.com/youtubei/v1/browse`
  - `https://ipinfo.io`
- Optional HTTP proxy:
  - Address: `ip:port`
  - Optional username / password (Basic auth via `Proxy-Authorization`)
- All DNS resolution for these requests goes through the custom multi-resolver from `:dns`.

### 2. DNS over HTTPS (DoH) Test

Entry: `MainActivity` → Tab “DoH 测试”

- Directly calls Cloudflare DoH JSON API:
  - `https://cloudflare-dns.com/dns-query?name=<domain>&type=A&ct=application/dns-json`
- Uses **bootstrap DNS** (e.g. `1.1.1.1`, `1.0.0.1`) to resolve `cloudflare-dns.com` itself, avoiding dependence on system DNS.
- For `music.youtube.com`:
  1. Query A records via DoH.
  2. Use the multi-resolver (System DNS → Cloudflare DoH → Google DoH → Quad9 DoH → Wikimedia DoH) to send a real HTTPS request and verify connectivity.

### 3. System DNS Information

- Uses `SystemDnsInfoHelper` (in `dns` module) to read:
  - DNS servers of the **active network**
  - DNS servers of **all networks** (Android 6.0+)
- Provides formatted output:
  - `formatDnsInfo(info: SystemDnsInfo): String`
  - `formatAllDnsInfo(list: List<SystemDnsInfo>): String`

---

## DNS Library (`dns` module)

Package (internal): `com.github.dnsresolver.dns`

### Core Classes

- `DnsProvider`
  - Preconfigured DoH providers:
    - Cloudflare, Google, Quad9, Wikimedia
  - Each provider contains:
    - DoH URL (e.g. `https://cloudflare-dns.com/dns-query`)
    - Bootstrap IP list (so DoH host does not depend on system DNS)

- `DnsResolverFactory`
  - `createDohResolver(provider: DnsProvider, bootstrapClient: OkHttpClient? = null): Dns`
  - `createSystemResolver(): Dns`
  - Uses `okhttp-dnsoverhttps` to build DoH resolvers.

- `MultiDnsResolver`
  - Implements OkHttp `Dns`.
  - Tries multiple resolvers in order until one succeeds or all fail.

- `MetricsMultiDnsResolver`
  - Wraps `MultiDnsResolver` and emits detailed metrics via `DnsMetricsCollector`.

- `DnsManager`
  - High-level entry point:
    - `createDefault(...)`: System DNS first, then DoH providers in order.
    - `createDohOnly(...)`: DoH only, no system DNS.
    - `createCustom(...)`: Custom provider list / system-first flag.
    - `createCascadingResolver(...)`: Returns a multi-resolver (plain or metrics-enabled).

- `SystemDnsInfoHelper`
  - `getSystemDnsInfo(context): SystemDnsInfo?`
  - `getAllNetworksDnsInfo(context): List<SystemDnsInfo>`
  - `formatDnsInfo(info)` / `formatAllDnsInfo(list)` for display.

### Metrics & Logging (`com.github.dnsresolver.dns.metrics`)

- **Data types**
  - `DnsEvent`, `DnsEventType`
  - `DnsMetrics`, `ResolverStats`, `HostnameStats`
  - `DnsAggregatedStats`

- **Collector interface & implementations**
  - `DnsMetricsCollector`
  - `DefaultDnsMetricsCollector` – in-memory stats, suitable for analysis / exporting.
  - `NoOpDnsMetricsCollector` – disables metrics.
  - `LogDnsMetricsCollector` – logs metrics to Logcat (`Log.d`) in a readable format.

- **Logging facade**
  - `DnsMetricsFacade`
    - `setLoggingEnabled(enabled: Boolean)`
    - `isLoggingEnabled(): Boolean`
    - `createLogCollector(tag: String = "DnsMetrics"): LogDnsMetricsCollector`
  - This lets **library users** control whether logging is enabled, instead of hard‑coding `BuildConfig.DEBUG` inside the library.

Example usage in the app:

```kotlin
// Control logging (e.g. only in debug builds)
DnsMetricsFacade.setLoggingEnabled(BuildConfig.DEBUG)

// Create metrics collector
private val dnsMetricsCollector: LogDnsMetricsCollector by lazy {
    DnsMetricsFacade.createLogCollector(tag = "DnsMetrics")
}

// Create DnsManager with metrics
private val dnsManager: DnsManager by lazy {
    DnsManager.createDefault(metricsCollector = dnsMetricsCollector)
}
```

---

## Using the `dns` Module in Other Projects

### 1. Copy the module

Copy the `YTDDEMO/dns` directory into the root of your target project.

### 2. Register the module

In `settings.gradle.kts`:

```kotlin
include(":dns")
```

### 3. Add dependencies in your app module

```kotlin
dependencies {
    implementation(project(":dns"))

    // Required by dns module
    implementation(libs.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
}
```

If you want to use Ktor + OkHttp engine like the demo app:

```kotlin
implementation(libs.ktor.client.core)
implementation(libs.ktor.client.okhttp)
implementation(libs.ktor.client.content.negotiation)
implementation(libs.ktor.serialization.kotlinx.json)
```

### 4. Basic usage

```kotlin
// Decide logging strategy (e.g., only in debug builds)
DnsMetricsFacade.setLoggingEnabled(BuildConfig.DEBUG)

// Create DNS manager
val dnsManager = DnsManager.createDefault(
    metricsCollector = DnsMetricsFacade.createLogCollector("DnsMetrics")
)

// Obtain cascading resolver
val multiDns = dnsManager.createCascadingResolver()
```

Use with **OkHttp**:

```kotlin
val client = OkHttpClient.Builder()
    .dns(multiDns)
    .build()
```

Use with **Ktor Client + OkHttp engine**:

```kotlin
val client = HttpClient(OkHttp) {
    engine {
        config {
            dns(multiDns)
        }
    }
}
```

---

## Development & Build

### Requirements

- Android Studio (version that supports Kotlin 2.0 and Compose BOM 2024.09)
- JDK 11
- Android Gradle Plugin 8.1+

### Steps

1. Open the `YTDDEMO` folder in Android Studio.
2. Sync Gradle: **File ▸ Sync Project with Gradle Files**.
3. Build and run the `app` module on a device or emulator.

---

## License

You can add your preferred open-source license here, for example:

- MIT License
- Apache License 2.0


