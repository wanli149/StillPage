package io.stillpage.app.ui.book.video

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.stillpage.app.R
import io.stillpage.app.databinding.ActivityVideoUrlTestBinding
import kotlinx.coroutines.launch

/**
 * 视频URL测试和诊断Activity
 * 用于测试和诊断视频播放问题
 */
class VideoUrlTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoUrlTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoUrlTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupViews()
    }

    private fun setupViews() {
        binding.btnTest.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isNotBlank()) {
                testVideoUrl(url)
            } else {
                binding.tvResult.text = "请输入要测试的URL"
            }
        }

        binding.btnClear.setOnClickListener {
            binding.etUrl.text.clear()
            binding.tvResult.text = ""
        }

        // 预填充一个测试URL
        binding.etUrl.setText("https://api.langge.cf/content?item_id=7510908324095740990&source=番茄&device=67766e083bb3f769&tab=短剧&version=4.6.29")
    }

    private fun testVideoUrl(url: String) {
        binding.tvResult.text = "正在诊断URL...\n$url\n\n"
        binding.btnTest.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = VideoPlayDiagnostics.diagnoseVideoUrl(url)
                val report = result.toDetailedString()
                
                runOnUiThread {
                    binding.tvResult.text = report
                    binding.scrollView.post {
                        binding.scrollView.fullScroll(ScrollView.FOCUS_UP)
                    }
                }
                
            } catch (e: Exception) {
                runOnUiThread {
                    binding.tvResult.text = "诊断失败: ${e.message}\n\n${e.stackTraceToString()}"
                }
            } finally {
                runOnUiThread {
                    binding.btnTest.isEnabled = true
                }
            }
        }
    }
}