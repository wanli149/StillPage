package io.stillpage.app.ui.book.music.play

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.stillpage.app.constant.AppLog
import io.stillpage.app.ui.book.audio.AudioPlayActivity

/**
 * 音乐播放页面
 * 直接跳转到音频播放页面
 */
class MusicPlayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bookUrl = intent.getStringExtra("bookUrl") ?: ""
        val songIndex = intent.getIntExtra("songIndex", 0)
        val autoPlay = intent.getBooleanExtra("autoPlay", true)
        val inBookshelf = intent.getBooleanExtra("inBookshelf", false)

        AppLog.put("MusicPlayActivity: 音乐播放 bookUrl=$bookUrl, songIndex=$songIndex")

        // 直接启动AudioPlayActivity
        val audioIntent = Intent(this, AudioPlayActivity::class.java).apply {
            putExtra("bookUrl", bookUrl)
            putExtra("inBookshelf", inBookshelf)
            putExtra("autoPlay", autoPlay)
            if (songIndex > 0) {
                putExtra("chapterIndex", songIndex)
            }
        }

        startActivity(audioIntent)
        finish() // 关闭当前Activity
    }
}
