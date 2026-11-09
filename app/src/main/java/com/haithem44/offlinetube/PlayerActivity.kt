package com.haithem44.offlinetube

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView

class PlayerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val video = VideoView(this)
        frame.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        setContentView(frame)

        val mc = MediaController(this)
        mc.setAnchorView(video)
        video.setMediaController(mc)
        video.setVideoURI(intent.data)
        video.setOnPreparedListener { it.start() }
        video.setOnCompletionListener { finish() }
    }
}
