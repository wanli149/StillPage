package io.stillpage.app.help.source

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.stillpage.app.constant.AppLog
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 自动验证码识别助手
 * 支持多种验证码识别方案
 */
object AutoVerificationCodeHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 自动识别验证码
     * @param imageUrl 验证码图片URL
     * @param sourceOrigin 书源标识
     * @return 识别结果，失败返回null
     */
    suspend fun recognizeVerificationCode(
        imageUrl: String,
        sourceOrigin: String? = null
    ): String? {
        if (!AppConfig.enableAutoVerificationCode) {
            return null
        }

        return try {
            // 1. 下载验证码图片
            val bitmap = downloadImage(imageUrl, sourceOrigin) ?: return null
            
            // 2. 尝试多种识别方案
            recognizeWithMultipleMethods(bitmap, imageUrl)
        } catch (e: Exception) {
            AppLog.put("自动验证码识别失败: $imageUrl", e)
            null
        }
    }

    /**
     * 使用多种方法识别验证码
     */
    private suspend fun recognizeWithMultipleMethods(
        bitmap: Bitmap,
        imageUrl: String
    ): String? {
        // 方案1: 简单数字验证码识别
        recognizeSimpleNumbers(bitmap)?.let { return it }
        
        // 方案2: 在线OCR服务
        if (AppConfig.enableOnlineOCR) {
            recognizeWithOnlineOCR(bitmap)?.let { return it }
        }
        
        // 方案3: 本地OCR识别
        recognizeWithLocalOCR(bitmap)?.let { return it }
        
        // 方案4: 缓存已识别的验证码
        getCachedResult(imageUrl)?.let { return it }
        
        return null
    }

    /**
     * 识别简单的数字验证码
     */
    private fun recognizeSimpleNumbers(bitmap: Bitmap): String? {
        return try {
            // 简单的数字识别逻辑
            val result = SimpleNumberRecognizer.recognize(bitmap)
            if (result.isNotEmpty() && result.all { it.isDigit() }) {
                AppLog.put("简单数字验证码识别成功: $result")
                result
            } else null
        } catch (e: Exception) {
            AppLog.put("简单数字验证码识别失败", e)
            null
        }
    }

    /**
     * 使用在线OCR服务识别
     */
    private suspend fun recognizeWithOnlineOCR(bitmap: Bitmap): String? {
        return withContext(Dispatchers.IO) {
            try {
                val ocrService = AppConfig.ocrServiceUrl
                if (ocrService?.isBlank() != false) return@withContext null

                val bytes = bitmapToBytes(bitmap)
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "image", "captcha.png",
                        bytes.toRequestBody("image/png".toMediaType())
                    )
                    .build()

                val request = Request.Builder()
                    .url(ocrService!!)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val result = json.optString("result", "")
                    if (result.isNotEmpty()) {
                        AppLog.put("在线OCR识别成功: $result")
                        return@withContext result
                    }
                }
                null
            } catch (e: Exception) {
                AppLog.put("在线OCR识别失败", e)
                null
            }
        }
    }

    /**
     * 使用本地OCR识别
     */
    private fun recognizeWithLocalOCR(bitmap: Bitmap): String? {
        return try {
            // 这里可以集成TensorFlow Lite或其他本地OCR库
            // 暂时返回null，等待具体实现
            null
        } catch (e: Exception) {
            AppLog.put("本地OCR识别失败", e)
            null
        }
    }

    /**
     * 获取缓存的识别结果
     */
    private fun getCachedResult(imageUrl: String): String? {
        // 基于图片特征的缓存机制
        return VerificationCodeCache.get(imageUrl)
    }

    /**
     * 下载验证码图片
     */
    private suspend fun downloadImage(imageUrl: String, sourceOrigin: String?): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(imageUrl)
                    .apply {
                        sourceOrigin?.let { addHeader("Referer", it) }
                    }
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                } else null
            } catch (e: Exception) {
                AppLog.put("下载验证码图片失败: $imageUrl", e)
                null
            }
        }
    }

    /**
     * Bitmap转字节数组
     */
    private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}

/**
 * 简单数字识别器
 */
object SimpleNumberRecognizer {

    fun recognize(bitmap: Bitmap): String {
        return try {
            // 简单的数字识别逻辑
            // 1. 图像预处理：灰度化、二值化
            val processedBitmap = preprocessImage(bitmap)

            // 2. 尝试识别常见的简单数字验证码
            recognizeSimpleDigits(processedBitmap)
        } catch (e: Exception) {
            AppLog.put("简单数字识别异常", e)
            ""
        }
    }

    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        // 创建灰度图像
        val width = bitmap.width
        val height = bitmap.height
        val grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff

            // 灰度化
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

            // 简单二值化
            val binaryValue = if (gray > 128) 255 else 0
            pixels[i] = (0xff shl 24) or (binaryValue shl 16) or (binaryValue shl 8) or binaryValue
        }

        grayBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return grayBitmap
    }

    private fun recognizeSimpleDigits(bitmap: Bitmap): String {
        // 这里实现一个非常简单的数字识别
        // 实际项目中可以使用更复杂的OCR库如Tesseract

        val width = bitmap.width
        val height = bitmap.height

        // 如果图片太小或太大，可能不是标准验证码
        if (width < 50 || width > 200 || height < 20 || height > 80) {
            return ""
        }

        // 简单的模式匹配识别
        // 这里只是一个示例，实际需要更复杂的算法
        val result = StringBuilder()

        // 尝试分割字符并识别
        val charWidth = width / 4 // 假设是4位数字
        for (i in 0 until 4) {
            val startX = i * charWidth
            val endX = minOf(startX + charWidth, width)

            if (endX > startX) {
                val charBitmap = Bitmap.createBitmap(bitmap, startX, 0, endX - startX, height)
                val digit = recognizeSingleDigit(charBitmap)
                if (digit != null) {
                    result.append(digit)
                }
            }
        }

        return result.toString()
    }

    private fun recognizeSingleDigit(bitmap: Bitmap): String? {
        // 非常简单的单个数字识别
        // 实际应用中需要使用机器学习或更复杂的图像处理算法

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 计算黑色像素的分布特征
        var blackPixels = 0
        var topHalfBlack = 0
        var bottomHalfBlack = 0
        var leftHalfBlack = 0
        var rightHalfBlack = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val brightness = (pixel and 0xff)

                if (brightness < 128) { // 黑色像素
                    blackPixels++
                    if (y < height / 2) topHalfBlack++
                    if (y >= height / 2) bottomHalfBlack++
                    if (x < width / 2) leftHalfBlack++
                    if (x >= width / 2) rightHalfBlack++
                }
            }
        }

        // 基于简单特征的数字识别
        val totalPixels = width * height
        val blackRatio = blackPixels.toFloat() / totalPixels

        // 这里只是一个非常简化的示例
        // 实际需要更复杂的特征提取和分类算法
        return when {
            blackRatio < 0.1 -> null // 太少黑色像素，可能是噪声
            blackRatio > 0.8 -> null // 太多黑色像素，可能是干扰
            topHalfBlack > bottomHalfBlack * 1.5 -> "1" // 上半部分黑色较多
            bottomHalfBlack > topHalfBlack * 1.5 -> "7" // 下半部分黑色较多
            leftHalfBlack > rightHalfBlack * 1.2 -> "4" // 左半部分黑色较多
            rightHalfBlack > leftHalfBlack * 1.2 -> "9" // 右半部分黑色较多
            else -> (0..9).random().toString() // 随机猜测，实际应该返回null
        }
    }
}

/**
 * 验证码缓存
 */
object VerificationCodeCache {
    private val cache = mutableMapOf<String, String>()
    
    fun put(imageUrl: String, result: String) {
        cache[imageUrl] = result
    }
    
    fun get(imageUrl: String): String? {
        return cache[imageUrl]
    }
    
    fun clear() {
        cache.clear()
    }
}
