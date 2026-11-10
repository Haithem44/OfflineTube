package com.haithem44.offlinetube

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Size
import android.util.LruCache
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import java.util.concurrent.Executors

data class VideoItem(val id: Long, val title: String, val duration: Long, val uri: Uri)

class MainActivity : Activity() {

    private val bg = Color.parseColor("#0F0F0F")
    private val cardBg = Color.parseColor("#1C1C1E")
    private val red = Color.parseColor("#FF0000")

    private val all = ArrayList<VideoItem>()
    private val shown = ArrayList<VideoItem>()

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var listView: ListView
    private lateinit var adapter: VideoAdapter
    private lateinit var titleView: TextView
    private lateinit var searchBox: EditText
    private var hasBanner = false
    private var topBarH = 0

    private val pool = Executors.newFixedThreadPool(3)
    private val ui = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<Long, Bitmap>(60) {}

    // Player
    private lateinit var rootFrame: FrameLayout
    private lateinit var playerRoot: FrameLayout
    private lateinit var surfaceView: SurfaceView
    private lateinit var controls: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var playerTitle: TextView
    private lateinit var playPause: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var timeText: TextView
    private var mp: MediaPlayer? = null
    private var prepared = false
    private var current: VideoItem? = null
    private var pending: VideoItem? = null
    private var videoW = 0
    private var videoH = 0
    private var isLandscape = false
    private var fillMode = false
    private var userSeeking = false

    private val ticker = object : Runnable {
        override fun run() {
            val p = mp
            if (p != null && prepared) {
                try {
                    val pos = p.currentPosition
                    if (!userSeeking) {
                        seekBar.max = p.duration
                        seekBar.progress = pos
                    }
                    timeText.text = fmt(pos.toLong()) + " / " + fmt(p.duration.toLong())
                    current?.let { savePos(it, pos.toLong(), p.duration.toLong()) }
                } catch (e: Exception) {
                }
            }
            ui.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("offlinetube", Context.MODE_PRIVATE)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        rootFrame = FrameLayout(this)
        rootFrame.setBackgroundColor(bg)

        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        val tb = buildTopBar()
        main.addView(tb, LinearLayout.LayoutParams(-1, topBarH))

        listView = ListView(this)
        listView.divider = null
        listView.setBackgroundColor(bg)
        listView.setPadding(0, dp(8), 0, dp(16))
        listView.clipToPadding = false
        adapter = VideoAdapter()
        listView.adapter = adapter
        listView.setOnItemClickListener { parent, _, pos, _ ->
            val item = parent.getItemAtPosition(pos)
            if (item is VideoItem) openPlayer(item)
        }
        main.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))
        rootFrame.addView(main, FrameLayout.LayoutParams(-1, -1))

        buildPlayer()
        rootFrame.addView(playerRoot, FrameLayout.LayoutParams(-1, -1))
        playerRoot.visibility = View.GONE

        setContentView(rootFrame)
        ui.post(ticker)
        checkPermissionAndLoad()
    }

    // ---------------- Top bar with banner image ----------------

    // يبحث عن الصورة في drawable ثم mipmap
    private fun findImageRes(name: String): Int {
        var id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) id = resources.getIdentifier(name, "mipmap", packageName)
        return id
    }

    private fun buildTopBar(): FrameLayout {
        val resId = findImageRes("appbanner")
        hasBanner = resId != 0
        topBarH = if (hasBanner) dp(130) else dp(56)

        val bar = FrameLayout(this)
        bar.setBackgroundColor(bg)

        if (hasBanner) {
            val iv = ImageView(this)
            iv.setImageResource(resId)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            bar.addView(iv, FrameLayout.LayoutParams(-1, -1))

            // تدرج داكن خفيف أعلى الصورة ليظهر زر البحث بوضوح
            val scrim = View(this)
            scrim.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#99000000"), Color.TRANSPARENT)
            )
            bar.addView(scrim, FrameLayout.LayoutParams(-1, dp(70), Gravity.TOP))
        }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), 0, dp(8), 0)

        titleView = TextView(this)
        titleView.text = "OfflineTube"
        titleView.setTextColor(Color.WHITE)
        titleView.textSize = 22f
        titleView.typeface = Typeface.DEFAULT_BOLD
        titleView.visibility = if (hasBanner) View.INVISIBLE else View.VISIBLE
        row.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))

        searchBox = EditText(this)
        searchBox.hint = "ابحث عن فيديو"
        searchBox.setHintTextColor(Color.LTGRAY)
        searchBox.setTextColor(Color.WHITE)
        searchBox.isSingleLine = true
        searchBox.visibility = View.GONE
        val sbShape = GradientDrawable()
        sbShape.setColor(Color.parseColor("#B3000000"))
        sbShape.cornerRadius = dp(20).toFloat()
        searchBox.background = sbShape
        searchBox.setPadding(dp(14), dp(6), dp(14), dp(6))
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s?.toString() ?: "")
            }

            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        row.addView(searchBox, LinearLayout.LayoutParams(0, -2, 1f))

        val searchBtn = ImageButton(this)
        searchBtn.setImageResource(android.R.drawable.ic_menu_search)
        searchBtn.setColorFilter(Color.WHITE)
        val btnShape = GradientDrawable()
        btnShape.shape = GradientDrawable.OVAL
        btnShape.setColor(Color.parseColor("#80000000"))
        searchBtn.background = btnShape
        searchBtn.setOnClickListener { toggleSearch() }
        val lp = LinearLayout.LayoutParams(dp(42), dp(42))
        lp.leftMargin = dp(8)
        row.addView(searchBtn, lp)

        bar.addView(row, FrameLayout.LayoutParams(-1, dp(56), Gravity.TOP))
        return bar
    }

    private fun toggleSearch() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (searchBox.visibility == View.VISIBLE) {
            searchBox.setText("")
            searchBox.visibility = View.GONE
            titleView.visibility = if (hasBanner) View.INVISIBLE else View.VISIBLE
            imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
        } else {
            titleView.visibility = View.GONE
            searchBox.visibility = View.VISIBLE
            searchBox.requestFocus()
            imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun applyFilter(q: String) {
        shown.clear()
        val s = q.trim().lowercase()
        for (v in all) if (s.isEmpty() || v.title.lowercase().contains(s)) shown.add(v)
        adapter.notifyDataSetChanged()
    }

    // ---------------- Permission + loading ----------------

    private fun checkPermissionAndLoad() {
        val perm = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) {
            loadVideos()
        } else {
            requestPermissions(arrayOf(perm), 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) loadVideos()
    }

    private fun loadVideos() {
        pool.execute {
            val list = ArrayList<VideoItem>()
            val proj = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION
            )
            try {
                contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, null, null,
                    MediaStore.Video.Media.DATE_ADDED + " DESC"
                )?.use { c ->
                    val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    while (c.moveToNext()) {
                        val id = c.getLong(iId)
                        val name = (c.getString(iName) ?: "video").substringBeforeLast('.')
                        val uri = Uri.withAppendedPath(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString()
                        )
                        list.add(VideoItem(id, name, c.getLong(iDur), uri))
                    }
                }
            } catch (e: Exception) {
            }
            ui.post {
                all.clear()
                all.addAll(list)
                applyFilter(searchBox.text.toString())
            }
        }
    }

    // ---------------- List adapter (large cards) ----------------

    inner class VideoAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = shown[p].id
        override fun getViewTypeCount() = 1
        override fun getItemViewType(p: Int) = 0
        override fun getView(p: Int, convertView: View?, parent: ViewGroup?): View {
            return bigCard(shown[p])
        }
    }

    private fun screenW() = resources.displayMetrics.widthPixels

    // Large rounded card: wide thumbnail on top, title + info below
    private fun bigCard(v: VideoItem): View {
        val cardW = screenW() - dp(24)
        val thumbH = cardW * 9 / 16

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        val shape = GradientDrawable()
        shape.setColor(cardBg)
        shape.cornerRadius = dp(18).toFloat()
        card.background = shape
        card.outlineProvider = ViewOutlineProvider.BACKGROUND
        card.clipToOutline = true
        card.elevation = dp(4).toFloat()

        card.addView(thumbFrame(v, cardW, thumbH), LinearLayout.LayoutParams(cardW, thumbH))

        val title = TextView(this)
        title.text = v.title
        title.setTextColor(Color.WHITE)
        title.textSize = 17f
        title.typeface = Typeface.DEFAULT_BOLD
        title.maxLines = 2
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        title.setPadding(dp(14), dp(12), dp(14), 0)
        card.addView(title)

        val info = TextView(this)
        val pos = prefs.getLong("p_" + v.id, 0L)
        val dur = if (v.duration > 0) v.duration else prefs.getLong("d_" + v.id, 0L)
        val pct = if (pos > 0 && dur > 0) ((pos * 100) / dur).toInt().coerceIn(0, 100) else 0
        info.text = if (pct > 0) "المدة " + fmt(v.duration) + "  •  شوهد " + pct + "%"
        else "المدة " + fmt(v.duration)
        info.setTextColor(Color.parseColor("#AAAAAA"))
        info.textSize = 13f
        info.setPadding(dp(14), dp(4), dp(14), dp(14))
        card.addView(info)

        val outer = FrameLayout(this)
        outer.setPadding(dp(12), dp(8), dp(12), dp(8))
        outer.addView(card, FrameLayout.LayoutParams(cardW, -2))
        return outer
    }

    // Thumbnail + duration badge + colored watched-progress line
    private fun thumbFrame(v: VideoItem, w: Int, h: Int): FrameLayout {
        val f = FrameLayout(this)
        f.setBackgroundColor(Color.parseColor("#222222"))
        f.layoutParams = ViewGroup.LayoutParams(w, h)

        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        f.addView(iv, FrameLayout.LayoutParams(-1, -1))
        loadThumb(v, iv)

        val badge = TextView(this)
        badge.text = fmt(v.duration)
        badge.setTextColor(Color.WHITE)
        badge.textSize = 12f
        val bShape = GradientDrawable()
        bShape.setColor(Color.parseColor("#CC000000"))
        bShape.cornerRadius = dp(6).toFloat()
        badge.background = bShape
        badge.setPadding(dp(7), dp(2), dp(7), dp(2))
        f.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM).apply {
            rightMargin = dp(10); bottomMargin = dp(12)
        })

        val pos = prefs.getLong("p_" + v.id, 0L)
        val dur = if (v.duration > 0) v.duration else prefs.getLong("d_" + v.id, 0L)
        if (pos > 0 && dur > 0) {
            val frac = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            val done = View(this)
            done.setBackgroundColor(red)
            val rest = View(this)
            rest.setBackgroundColor(Color.parseColor("#55FFFFFF"))
            line.addView(done, LinearLayout.LayoutParams(0, -1, frac))
            line.addView(rest, LinearLayout.LayoutParams(0, -1, 1f - frac))
            f.addView(line, FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM))
        }
        return f
    }

    private fun loadThumb(v: VideoItem, iv: ImageView) {
        iv.tag = v.id
        val cached = cache.get(v.id)
        if (cached != null) {
            iv.setImageBitmap(cached)
            return
        }
        pool.execute {
            val bmp: Bitmap? = try {
                if (Build.VERSION.SDK_INT >= 29) {
                    contentResolver.loadThumbnail(v.uri, Size(720, 405), null)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(
                        contentResolver, v.id, MediaStore.Video.Thumbnails.MINI_KIND, null
                    )
                }
            } catch (e: Exception) {
                null
            }
            if (bmp != null) {
                cache.put(v.id, bmp)
                ui.post { if (iv.tag == v.id) iv.setImageBitmap(bmp) }
            }
        }
    }

    // ---------------- Player ----------------

    private fun buildPlayer() {
        playerRoot = FrameLayout(this)
        playerRoot.setBackgroundColor(Color.BLACK)
        playerRoot.isClickable = true

        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) {
                mp?.let { try { it.setDisplay(h) } catch (e: Exception) { } }
                pending?.let {
                    pending = null
                    startPlayback(it)
                }
            }

            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
            override fun surfaceDestroyed(h: SurfaceHolder) {
                // keep audio playing when the screen locks
                mp?.let { try { it.setDisplay(null) } catch (e: Exception) { } }
            }
        })
        playerRoot.addView(surfaceView, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

        playerRoot.setOnClickListener {
            val show = controls.visibility != View.VISIBLE
            controls.visibility = if (show) View.VISIBLE else View.GONE
            topBar.visibility = if (show) View.VISIBLE else View.GONE
        }
        playerRoot.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitVideo() }

        // top bar
        topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.setBackgroundColor(Color.parseColor("#88000000"))
        val back = TextView(this)
        back.text = "←"
        back.setTextColor(Color.WHITE)
        back.textSize = 26f
        back.setPadding(dp(16), dp(8), dp(16), dp(8))
        back.setOnClickListener { closePlayer() }
        topBar.addView(back)
        playerTitle = TextView(this)
        playerTitle.setTextColor(Color.WHITE)
        playerTitle.textSize = 15f
        playerTitle.maxLines = 1
        playerTitle.ellipsize = android.text.TextUtils.TruncateAt.END
        topBar.addView(playerTitle, LinearLayout.LayoutParams(0, -2, 1f))
        playerRoot.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // bottom controls
        controls = LinearLayout(this)
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER_VERTICAL
        controls.setBackgroundColor(Color.parseColor("#88000000"))
        controls.setPadding(dp(8), dp(4), dp(8), dp(4))

        playPause = TextView(this)
        playPause.text = "⏸"
        playPause.setTextColor(Color.WHITE)
        playPause.textSize = 24f
        playPause.setPadding(dp(12), dp(8), dp(12), dp(8))
        playPause.setOnClickListener {
            val p = mp ?: return@setOnClickListener
            if (!prepared) return@setOnClickListener
            if (p.isPlaying) {
                p.pause(); playPause.text = "▶"
            } else {
                p.start(); playPause.text = "⏸"
            }
        }
        controls.addView(playPause)

        seekBar = SeekBar(this)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(s: SeekBar?) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(s: SeekBar?) {
                userSeeking = false
                if (prepared) mp?.seekTo(seekBar.progress)
            }
        })
        controls.addView(seekBar, LinearLayout.LayoutParams(0, -2, 1f))

        timeText = TextView(this)
        timeText.setTextColor(Color.WHITE)
        timeText.textSize = 12f
        timeText.text = "0:00 / 0:00"
        timeText.setPadding(dp(6), 0, dp(6), 0)
        controls.addView(timeText)

        // زر ملء الشاشة بالكامل (قص الأطراف) / احتواء
        val fill = TextView(this)
        fill.text = "⤢"
        fill.setTextColor(Color.WHITE)
        fill.textSize = 24f
        fill.setPadding(dp(10), dp(8), dp(10), dp(8))
        fill.setOnClickListener {
            fillMode = !fillMode
            fitVideo()
        }
        controls.addView(fill)

        // زر تدوير الشاشة
        val fs = TextView(this)
        fs.text = "⛶"
        fs.setTextColor(Color.WHITE)
        fs.textSize = 24f
        fs.setPadding(dp(10), dp(8), dp(10), dp(8))
        fs.setOnClickListener { toggleFullscreen() }
        controls.addView(fs)

        playerRoot.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
    }

    private fun openPlayer(v: VideoItem) {
        releasePlayer()
        current = v
        videoW = 0
        videoH = 0
        fillMode = false
        playerTitle.text = v.title
        playPause.text = "⏸"
        seekBar.progress = 0
        controls.visibility = View.VISIBLE
        topBar.visibility = View.VISIBLE
        playerRoot.visibility = View.VISIBLE
        hideSystemBars(true)
        if (surfaceView.holder.surface.isValid) {
            startPlayback(v)
        } else {
            pending = v
        }
    }

    private fun startPlayback(v: VideoItem) {
        releasePlayer()
        val p = MediaPlayer()
        mp = p
        prepared = false
        try {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build()
            )
            try {
                p.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            } catch (e: Exception) {
            }
            p.setDataSource(this, v.uri)
            p.setDisplay(surfaceView.holder)
            p.setOnVideoSizeChangedListener { _, w, h ->
                videoW = w
                videoH = h
                // الفيديو العرضي يفتح تلقائياً بشاشة أفقية ممتلئة
                if (w > h && !isLandscape) {
                    isLandscape = true
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                fitVideo()
            }
            p.setOnPreparedListener { m ->
                prepared = true
                seekBar.max = m.duration
                val saved = prefs.getLong("p_" + v.id, 0L)
                if (saved > 0 && saved < m.duration - 3000) m.seekTo(saved.toInt())
                m.start()
                playPause.text = "⏸"
            }
            p.setOnCompletionListener {
                savePos(v, 0L, it.duration.toLong())
                playPause.text = "▶"
            }
            p.setOnErrorListener { _, _, _ ->
                playPause.text = "▶"
                true
            }
            p.prepareAsync()
        } catch (e: Exception) {
            playPause.text = "▶"
        }
    }

    private fun fitVideo() {
        val cw = playerRoot.width
        val ch = playerRoot.height
        if (videoW <= 0 || videoH <= 0 || cw <= 0 || ch <= 0) return
        val sx = cw.toFloat() / videoW
        val sy = ch.toFloat() / videoH
        val scale = if (fillMode) maxOf(sx, sy) else minOf(sx, sy)
        val nw = (videoW * scale).toInt()
        val nh = (videoH * scale).toInt()
        val lp = surfaceView.layoutParams as FrameLayout.LayoutParams
        if (lp.width != nw || lp.height != nh) {
            lp.width = nw
            lp.height = nh
            lp.gravity = Gravity.CENTER
            surfaceView.layoutParams = lp
        }
    }

    private fun toggleFullscreen() {
        isLandscape = !isLandscape
        requestedOrientation = if (isLandscape)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars(hide: Boolean) {
        window.decorView.systemUiVisibility = if (hide) {
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        } else {
            View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun closePlayer() {
        val p = mp
        val c = current
        if (p != null && c != null && prepared) {
            try {
                savePos(c, p.currentPosition.toLong(), p.duration.toLong())
            } catch (e: Exception) {
            }
        }
        releasePlayer()
        current = null
        pending = null
        videoW = 0
        videoH = 0
        fillMode = false
        playerRoot.visibility = View.GONE
        isLandscape = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        hideSystemBars(false)
        adapter.notifyDataSetChanged()
    }

    private fun releasePlayer() {
        val p = mp ?: return
        mp = null
        prepared = false
        try {
            p.setOnPreparedListener(null)
            p.setOnCompletionListener(null)
            p.release()
        } catch (e: Exception) {
        }
    }

    private fun savePos(v: VideoItem, pos: Long, dur: Long) {
        prefs.edit().putLong("p_" + v.id, pos).putLong("d_" + v.id, dur).apply()
    }

    override fun onBackPressed() {
        if (playerRoot.visibility == View.VISIBLE) closePlayer() else super.onBackPressed()
    }

    override fun onDestroy() {
        ui.removeCallbacks(ticker)
        releasePlayer()
        pool.shutdown()
        super.onDestroy()
    }

    // ---------------- helpers ----------------

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec)
        else String.format("%d:%02d", m, sec)
    }
}
