package com.haithem44.offlinetube

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.*

object Prog {
    private fun prefs(c: Context) =
        c.getSharedPreferences("progress", Context.MODE_PRIVATE)

    fun save(c: Context, id: Long, pos: Long, dur: Long) {
        prefs(c).edit().putLong("p$id", pos).putLong("d$id", dur).apply()
    }

    fun pos(c: Context, id: Long): Long = prefs(c).getLong("p$id", 0L)
    fun dur(c: Context, id: Long): Long = prefs(c).getLong("d$id", 0L)
}

class FsIcon(c: Context) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val i = w * 0.25f
        val k = w * 0.18f
        val l = i
        val t = h * 0.25f
        val r = w - i
        val b = h - h * 0.25f
        canvas.drawLine(l, t, l + k, t, paint)
        canvas.drawLine(l, t, l, t + k, paint)
        canvas.drawLine(r, t, r - k, t, paint)
        canvas.drawLine(r, t, r, t + k, paint)
        canvas.drawLine(l, b, l + k, b, paint)
        canvas.drawLine(l, b, l, b - k, paint)
        canvas.drawLine(r, b, r - k, b, paint)
        canvas.drawLine(r, b, r, b - k, paint)
    }
}

class PlayerActivity : Activity(), SurfaceHolder.Callback {
    private var mp: MediaPlayer? = null
    private var uri: Uri? = null
    private var videoId = -1L
    private var vw = 0
    private var vh = 0
    private var prepared = false
    private var completed = false
    private var userSeeking = false
    private var counter = 0
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var container: FrameLayout
    private lateinit var surface: SurfaceView
    private lateinit var controls: LinearLayout
    private lateinit var playBtn: ImageButton
    private lateinit var seek: SeekBar
    private lateinit var cur: TextView
    private lateinit var total: TextView

    private val hideRun = Runnable { controls.visibility = View.GONE }

    private val tick = object : Runnable {
        override fun run() {
            val p = mp
            if (p != null && prepared && !completed) {
                try {
                    val pos = p.currentPosition
                    if (!userSeeking) {
                        seek.progress = pos
                        cur.text = fmt(pos)
                    }
                    counter++
                    if (counter % 10 == 0 && p.isPlaying) saveNow()
                } catch (e: Exception) {
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        uri = intent.data
        videoId = intent.getLongExtra("id", -1L)
        if (uri == null) {
            finish()
            return
        }

        val svc = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)

        container = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
        }
        surface = SurfaceView(this)
        container.addView(surface, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        surface.holder.addCallback(this)

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            isClickable = true
        }
        playBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setBackgroundColor(Color.TRANSPARENT)
        }
        cur = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            text = "0:00"
            setPadding(dp(6), 0, dp(6), 0)
        }
        seek = SeekBar(this)
        total = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            text = "0:00"
            setPadding(dp(6), 0, dp(6), 0)
        }
        val fsBtn = FsIcon(this).apply { isClickable = true }

        controls.addView(playBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        controls.addView(cur, LinearLayout.LayoutParams(-2, -2))
        controls.addView(seek, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(total, LinearLayout.LayoutParams(-2, -2))
        controls.addView(fsBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        container.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(container)

        container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitVideo() }
        container.setOnClickListener {
            if (controls.visibility == View.VISIBLE) controls.visibility = View.GONE
            else showControls()
        }

        playBtn.setOnClickListener {
            val p = mp
            if (p != null && prepared) {
                if (p.isPlaying) p.pause() else p.start()
                updatePlayIcon()
            }
            showControls()
        }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) cur.text = fmt(p)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(s: SeekBar?) {
                userSeeking = false
                mp?.seekTo(s?.progress ?: 0)
                showControls()
            }
        })

        fsBtn.setOnClickListener {
            val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (land) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                handler.postDelayed({
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }, 1500)
            } else {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            showControls()
        }

        applyUi()
        handler.post(tick)
    }

    @Suppress("DEPRECATION")
    private fun applyUi() {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        window.decorView.systemUiVisibility = if (land)
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        else View.SYSTEM_UI_FLAG_VISIBLE
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyUi()
        container.post { fitVideo() }
    }

    private fun showControls() {
        controls.visibility = View.VISIBLE
        handler.removeCallbacks(hideRun)
        handler.postDelayed(hideRun, 3500)
    }

    private fun fitVideo() {
        if (vw == 0 || vh == 0) return
        val cw = container.width - container.paddingLeft - container.paddingRight
        val ch = container.height - container.paddingTop - container.paddingBottom
        if (cw <= 0 || ch <= 0) return
        val scale = minOf(cw.toFloat() / vw, ch.toFloat() / vh)
        val w = (vw * scale).toInt()
        val h = (vh * scale).toInt()
        val lp = surface.layoutParams
        if (lp.width != w || lp.height != h) {
            lp.width = w
            lp.height = h
            surface.layoutParams = lp
        }
    }

    private fun updatePlayIcon() {
        val playing = try {
            prepared && mp?.isPlaying == true
        } catch (e: Exception) {
            false
        }
        playBtn.setImageResource(
            if (playing) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun initPlayer(holder: SurfaceHolder) {
        val u = uri ?: return
        val p = MediaPlayer()
        mp = p
        try {
            p.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            p.setDataSource(this, u)
            p.setDisplay(holder)
            p.setOnVideoSizeChangedListener { _, w, h ->
                vw = w
                vh = h
                fitVideo()
            }
            p.setOnPreparedListener { m ->
                prepared = true
                val d = m.duration
                seek.max = d
                total.text = fmt(d)
                val saved = Prog.pos(this, videoId)
                if (saved > 3000 && saved < d - 3000) m.seekTo(saved.toInt())
                m.start()
                updatePlayIcon()
                showControls()
            }
            p.setOnCompletionListener { m ->
                completed = true
                Prog.save(this, videoId, m.duration.toLong(), m.duration.toLong())
                finish()
            }
            p.setOnErrorListener { _, _, _ ->
                Toast.makeText(this, "تعذر تشغيل الفيديو", Toast.LENGTH_LONG).show()
                finish()
                true
            }
            p.prepareAsync()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر تشغيل الفيديو", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val p = mp
        if (p == null) initPlayer(holder) else p.setDisplay(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        mp?.setDisplay(null)
    }

    private fun saveNow() {
        val p = mp ?: return
        if (!prepared || completed) return
        try {
            Prog.save(this, videoId, p.currentPosition.toLong(), p.duration.toLong())
        } catch (e: Exception) {
        }
    }

    override fun onPause() {
        super.onPause()
        saveNow()
    }

    override fun onDestroy() {
        saveNow()
        handler.removeCallbacksAndMessages(null)
        try {
            mp?.release()
        } catch (e: Exception) {
        }
        mp = null
        stopService(Intent(this, KeepAliveService::class.java))
        super.onDestroy()
    }

    private fun fmt(ms: Int): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec)
        else String.format("%d:%02d", m, sec)
    }
}
