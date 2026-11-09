package com.example.offlinetube

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class CustomPlayerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var playerView: PlayerView
    private var exoPlayer: ExoPlayer? = null

    init {
        // استخدام تخطيط مخصص سننشئه في الخطوة القادمة
        LayoutInflater.from(context).inflate(R.layout.custom_video_controller, this, true)
        
        playerView = findViewById(R.id.player_view)
        val btnBack10 = findViewById<ImageButton>(R.id.btn_back_10)
        val btnForward10 = findViewById<ImageButton>(R.id.btn_forward_10)
        val btnFullscreen = findViewById<ImageButton>(R.id.btn_fullscreen)
        val tvTime = findViewById<TextView>(R.id.tv_time)

        // أزرار التقديم والتأخير
        btnBack10.setOnClickListener { exoPlayer?.seekBack() }
        btnForward10.setOnClickListener { exoPlayer?.seekForward() }
        
        // زر التكبير (لتغيير حجم الفيديو)
        btnFullscreen.setOnClickListener {
            if (playerView.resizeMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM // تكبير لملء الشاشة
            } else {
                playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT // إرجاع للحجم الطبيعي
            }
        }
    }

    fun setPlayer(player: ExoPlayer) {
        this.exoPlayer = player
        playerView.player = player
        playerView.useController = false // إخفاء أزرار يوتيوب الافتراضية
    }
}
