package io.stillpage.app.help

import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.constant.AppLog
import io.stillpage.app.help.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 智能抓取策略管理器
 * 优化抓取性能，提供智能调度和负载均衡
 */
object SmartFetchStrategy {

    // 书源性能评级
    data class SourcePerformance(
        val bookSource: BookSource,
        val responseTime: Long = 0L,
        val successRate: Float = 1.0f,
        val lastUpdateTime: Long = 0L,
        val priority: Int = 0,
        val failureCount: Int = 0,
        val backoffUntil: Long = 0L
    )

    // 抓取配置
    data class FetchConfig(
        val maxConcurrentSources: Int,
        val maxBooksPerSource: Int,
        val timeoutMs: Long,
        val retryCount: Int,
        val minLoadIntervalMs: Long,
        val sourcesPerPage: Int
    )

    // 网络类型枚举
    enum class NetworkType { WIFI, MOBILE, UNKNOWN }
    
    // 设备性能枚举
    enum class DevicePerformance { HIGH, MEDIUM, LOW }
    
    // 网络质量枚举
    enum class NetworkQuality { EXCELLENT, GOOD, FAIR, POOR }
    
    // 网络质量检测缓存
    private var lastNetworkQualityCheck = 0L
    private var cachedNetworkQuality = NetworkQuality.GOOD
    private const val NETWORK_QUALITY_CACHE_MS = 30_000L // 30秒缓存

    // 退避/恢复状态
    private val failureCounts = mutableMapOf<String, Int>()
    private val backoffUntil = mutableMapOf<String, Long>()

    /**
     * 获取智能抓取配置 - 优化版本
     * 降低并发数，增强稳定性和响应速度
     */
    fun getSmartFetchConfig(): FetchConfig {
        val networkType = getNetworkType()
        val devicePerformance = getDevicePerformance()
        val networkQuality = getNetworkQuality()
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val isPeakTime = currentHour in 19..23 // 晚高峰时段
        
        return when {
            networkType == NetworkType.WIFI && devicePerformance == DevicePerformance.HIGH && 
            networkQuality == NetworkQuality.EXCELLENT && !isPeakTime -> {
                FetchConfig(
                    maxConcurrentSources = 3, // 从4降至3，提升稳定性
                    maxBooksPerSource = 20,   // 从25降至20，减少单源压力
                    timeoutMs = 12000L,       // 从15s降至12s，更快响应
                    retryCount = 2,
                    minLoadIntervalMs = 800L, // 从1s降至0.8s，提升响应
                    sourcesPerPage = 6        // 从8降至6，平衡质量与数量
                )
            }
            networkType == NetworkType.WIFI && devicePerformance == DevicePerformance.MEDIUM && 
            networkQuality >= NetworkQuality.GOOD -> {
                FetchConfig(
                    maxConcurrentSources = 2, // 从3降至2
                    maxBooksPerSource = 15,   // 从20降至15
                    timeoutMs = 15000L,       // 从20s降至15s
                    retryCount = 2,
                    minLoadIntervalMs = 1000L,
                    sourcesPerPage = 5        // 从6降至5
                )
            }
            networkType == NetworkType.MOBILE && devicePerformance == DevicePerformance.HIGH && 
            networkQuality >= NetworkQuality.GOOD -> {
                FetchConfig(
                    maxConcurrentSources = 2, // 保持2
                    maxBooksPerSource = 12,   // 从15降至12，节省流量
                    timeoutMs = 20000L,       // 从25s降至20s
                    retryCount = 1,
                    minLoadIntervalMs = 1200L, // 从1.5s降至1.2s
                    sourcesPerPage = 4        // 从5降至4
                )
            }
            else -> {
                // 低性能设备或网络较差时的保守配置
                FetchConfig(
                    maxConcurrentSources = 1, // 从2降至1，确保稳定
                    maxBooksPerSource = 10,   // 从12降至10
                    timeoutMs = 25000L,       // 从30s降至25s
                    retryCount = 1,
                    minLoadIntervalMs = 1500L, // 从2s降至1.5s
                    sourcesPerPage = 3        // 保持3
                )
            }
        }
    }

    /**
     * 基于内容类型细化每页书源抓取配额
     */
    fun getSourcesPerPageForType(
        config: FetchConfig,
        type: io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType
    ): Int {
        val base = config.sourcesPerPage
        return when (type) {
            io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType.AUDIO -> max(2, base - 2)
            io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType.IMAGE -> max(2, base - 1)
            io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType.MUSIC -> max(2, base - 1)
            io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType.DRAMA -> min(base + 1, base + 2)
            io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType.FILE -> max(2, base - 1)
            else -> base
        }
    }

    /**
     * 获取优化的书源列表 - 优先使用已标记类型的书源
     */
    suspend fun getOptimizedSources(
        page: Int, 
        config: FetchConfig, 
        contentType: io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType? = null
    ): List<BookSource> {
        return withContext(Dispatchers.IO) {
            val allSources = appDb.bookSourceDao.allEnabled
                .filter { it.enabledExplore && !it.exploreUrl.isNullOrBlank() }
            
            // 根据内容类型过滤书源，优先使用已标记的书源
            val filteredSources = filterSourcesByContentType(allSources, contentType)
            
            // 评估书源性能
            val sourcePerformances = filteredSources.map { source ->
                evaluateSourcePerformance(source)
            }
            
            // 智能排序
            val sortedSources = smartSort(sourcePerformances, page)
            
            // 根据页码和配置选择书源
            selectSourcesForPage(sortedSources, page, config)
        }
    }

    /**
     * 根据内容类型过滤书源，优先使用已标记的书源
     */
    private fun filterSourcesByContentType(
        allSources: List<BookSource>, 
        contentType: io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType?
    ): List<BookSource> {
        // 如果是"全部"类型或未指定类型，返回所有书源
        if (contentType == null || contentType == io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType.ALL) {
            return allSources
        }
        
        val targetTypeString = contentType.name
        
        // 分离已标记和未标记的书源
        val markedSources = allSources.filter { source ->
            source.contentTypeOverride == targetTypeString
        }
        
        val unmarkedSources = allSources.filter { source ->
            source.contentTypeOverride.isNullOrBlank()
        }
        
        AppLog.put("SmartFetchStrategy: 书源过滤 - 目标类型=$targetTypeString, 已标记=${markedSources.size}, 未标记=${unmarkedSources.size}")
        
        // 优先返回已标记的书源，然后是未标记的书源（用于智能检测）
        return markedSources + unmarkedSources
    }

    /**
     * 评估书源性能
     */
    private fun evaluateSourcePerformance(source: BookSource): SourcePerformance {
        // 基于历史数据评估性能
        val responseTime = source.respondTime
        val weight = source.weight
        val lastUpdateTime = source.lastUpdateTime
        
        // 计算成功率（基于权重和响应时间）
        val successRate = when {
            responseTime < 5000 && weight > 0 -> 1.0f
            responseTime < 10000 && weight >= 0 -> 0.8f
            responseTime < 20000 -> 0.6f
            else -> 0.4f
        }
        
        // 计算优先级
        val priority = calculatePriority(responseTime, weight, lastUpdateTime)
        
        return SourcePerformance(
            bookSource = source,
            responseTime = responseTime,
            successRate = successRate,
            lastUpdateTime = lastUpdateTime,
            priority = priority
        )
    }

    /**
     * 计算书源优先级
     */
    private fun calculatePriority(responseTime: Long, weight: Int, lastUpdateTime: Long): Int {
        var priority = 100
        
        // 响应时间影响
        priority -= when {
            responseTime < 3000 -> 0
            responseTime < 5000 -> 10
            responseTime < 10000 -> 20
            responseTime < 20000 -> 40
            else -> 60
        }
        
        // 权重影响
        priority += weight * 2
        
        // 更新时间影响（越新越好）
        val daysSinceUpdate = (System.currentTimeMillis() - lastUpdateTime) / (24 * 60 * 60 * 1000)
        priority -= (daysSinceUpdate * 2).toInt()
        
        return max(0, priority)
    }

    /**
     * 智能排序书源
     */
    private fun smartSort(performances: List<SourcePerformance>, page: Int): List<SourcePerformance> {
        return when (page) {
            1 -> {
                // 第一页优先使用高质量书源
                performances.sortedWith(
                    compareByDescending<SourcePerformance> { it.priority }
                        .thenByDescending { it.successRate }
                        .thenBy { it.responseTime }
                )
            }
            else -> {
                // 后续页面使用轮换策略
                performances.sortedWith(
                    compareBy<SourcePerformance> { it.responseTime }
                        .thenByDescending { it.priority }
                )
            }
        }
    }

    /**
     * 获取网络类型
     */
    private fun getNetworkType(): NetworkType {
        return try {
            val context = splitties.init.appCtx
            val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) 
                as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            when {
                networkCapabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> NetworkType.WIFI
                networkCapabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> NetworkType.MOBILE
                else -> NetworkType.UNKNOWN
            }
        } catch (e: Exception) {
            AppLog.put("获取网络类型失败", e)
            NetworkType.UNKNOWN
        }
    }
    
    /**
     * 获取设备性能等级
     */
    private fun getDevicePerformance(): DevicePerformance {
        return try {
            val context = splitties.init.appCtx
            val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) 
                as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            val totalMemoryMB = memoryInfo.totalMem / (1024 * 1024)
            val availableProcessors = Runtime.getRuntime().availableProcessors()
            
            when {
                totalMemoryMB >= 6144 && availableProcessors >= 8 -> DevicePerformance.HIGH
                totalMemoryMB >= 4096 && availableProcessors >= 4 -> DevicePerformance.MEDIUM
                else -> DevicePerformance.LOW
            }
        } catch (e: Exception) {
            AppLog.put("获取设备性能失败", e)
            DevicePerformance.MEDIUM
        }
    }
    
    /**
     * 获取网络质量等级
     * 通过ping测试和连接速度评估网络质量
     */
    private fun getNetworkQuality(): NetworkQuality {
        val currentTime = System.currentTimeMillis()
        
        // 使用缓存避免频繁检测
        if (currentTime - lastNetworkQualityCheck < NETWORK_QUALITY_CACHE_MS) {
            return cachedNetworkQuality
        }
        
        return try {
            val context = splitties.init.appCtx
            val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) 
                as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            if (networkCapabilities == null) {
                cachedNetworkQuality = NetworkQuality.POOR
                lastNetworkQualityCheck = currentTime
                return NetworkQuality.POOR
            }
            
            // 基于网络类型和信号强度评估质量
            val quality = when {
                networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> {
                    // WiFi网络质量评估
                    val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) 
                        as android.net.wifi.WifiManager
                    val wifiInfo = wifiManager.connectionInfo
                    val rssi = wifiInfo.rssi
                    
                    when {
                        rssi >= -50 -> NetworkQuality.EXCELLENT  // 信号强度很好
                        rssi >= -60 -> NetworkQuality.GOOD       // 信号强度良好
                        rssi >= -70 -> NetworkQuality.FAIR       // 信号强度一般
                        else -> NetworkQuality.POOR              // 信号强度较差
                    }
                }
                networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    // 移动网络质量评估
                    val telephonyManager = context.getSystemService(android.content.Context.TELEPHONY_SERVICE) 
                        as android.telephony.TelephonyManager
                    
                    when (telephonyManager.networkType) {
                        android.telephony.TelephonyManager.NETWORK_TYPE_LTE,
                        android.telephony.TelephonyManager.NETWORK_TYPE_NR -> NetworkQuality.GOOD
                        android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA,
                        android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA,
                        android.telephony.TelephonyManager.NETWORK_TYPE_HSPA -> NetworkQuality.FAIR
                        else -> NetworkQuality.POOR
                    }
                }
                else -> NetworkQuality.FAIR
            }
            
            cachedNetworkQuality = quality
            lastNetworkQualityCheck = currentTime
            AppLog.put("网络质量检测: $quality")
            quality
            
        } catch (e: Exception) {
            AppLog.put("获取网络质量失败", e)
            cachedNetworkQuality = NetworkQuality.FAIR
            lastNetworkQualityCheck = currentTime
            NetworkQuality.FAIR
        }
    }
    
    /**
     * 更新书源性能统计
     */
    fun updateSourcePerformance(
        sourceUrl: String,
        responseTime: Long,
        success: Boolean,
        resultCount: Int
    ) {
        try {
            if (success) {
                failureCounts.remove(sourceUrl)
                backoffUntil.remove(sourceUrl)
            } else {
                val currentFailures = failureCounts.getOrDefault(sourceUrl, 0) + 1
                failureCounts[sourceUrl] = currentFailures
                
                // 计算退避时间
                val backoffMs = when {
                    currentFailures <= 2 -> 30_000L  // 30秒
                    currentFailures <= 5 -> 300_000L // 5分钟
                    else -> 1800_000L                // 30分钟
                }
                backoffUntil[sourceUrl] = System.currentTimeMillis() + backoffMs
                
                AppLog.put("书源性能更新: $sourceUrl 失败 $currentFailures 次，退避 ${backoffMs/1000}秒")
            }
        } catch (e: Exception) {
            AppLog.put("更新书源性能统计失败", e)
        }
    }
    
    /**
     * 检查书源是否在退避期
     */
    fun isSourceInBackoff(sourceUrl: String): Boolean {
        val backoffTime = backoffUntil[sourceUrl] ?: return false
        return System.currentTimeMillis() < backoffTime
    }
    
    /**
     * 选择页面书源
     */
    private fun selectSourcesForPage(
        sortedSources: List<SourcePerformance>,
        page: Int,
        config: FetchConfig
    ): List<BookSource> {
        val startIndex = (page - 1) * config.sourcesPerPage
        val endIndex = min(startIndex + config.sourcesPerPage, sortedSources.size)
        
        if (startIndex >= sortedSources.size) {
            return emptyList()
        }
        
        return sortedSources.subList(startIndex, endIndex)
            .filter { !isSourceInBackoff(it.bookSource.bookSourceUrl) }
            .map { it.bookSource }
    }
}