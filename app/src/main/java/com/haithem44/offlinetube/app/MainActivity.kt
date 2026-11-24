package com.haithem44.offlinetube

import android.Manifest
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Rational
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.memory.MemoryCache
import org.schabi.newpipe.extractor.MediaFormat as NpFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class VideoItem(val id: Long, val title: String, val duration: Long, val uri: Uri)

class MainActivity : AppCompatActivity() {

    private val appName: String by lazy {
        try { getString(R.string.app_name) } catch (_: Exception) { "TUBE TV PRO" }
    }

    private lateinit var prefs: Prefs
    private val store by lazy { PositionStore(this) }
    private val bg = Color.parseColor("#0F0F0F")
    private val red = Color.parseColor("#FF0000")
    private val gray = Color.parseColor("#AAAAAA")
    private val navActive = Color.parseColor("#FF4D4D")
    private val navInactive = Color.parseColor("#E0E0E0")

    private val all = ArrayList<VideoItem>()
    private val shown = ArrayList<VideoItem>()
    private val expandedCommentIds = HashSet<Long>()

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: VideoAdapter
    private lateinit var continueHeader: LinearLayout
    private lateinit var logo: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var emptyText: TextView
    private lateinit var ytTab: YoutubeTab
    private lateinit var ytPanel: FrameLayout
    private val YT_TAB = 5
    private var tab = 0
    private var query = ""

    private lateinit var refreshBar: TextView
    private var refreshing = false
    private var touchStartY = 0f
    private var pulling = false
    private var firstResume = true

    private var folderPath: String = ""
    private var pendingDelete: VideoItem? = null
    private var deleteNeedsRetry = false
    private var pendingOpenId: Long = -1L
    private var pendingRestoreComments = false
    private var observerRegistered = false

    private lateinit var dlPanel: LinearLayout
    private lateinit var dlList: LinearLayout
    private lateinit var dlEmpty: TextView
    private lateinit var dlBadge: TextView
    private val dlRows = HashMap<Int, DlRow>()
    private val notifiedDone = HashSet<Int>()
    private val refreshViews = ArrayList<View>()
    private var spinAnim: ValueAnimator? = null

    private class DlRow(val root: LinearLayout, val title: TextView, val bar: ProgressBar, val status: TextView, val action: TextView) {
        var lastBytes = 0L; var lastTime = 0L; var speed = 0L
    }

    private val dlTicker = object : Runnable {
        override fun run() {
            try { refreshDownloads() } catch (_: Exception) {}
            ui.postDelayed(this, 500)
        }
    }

    private val tabNames = arrayOf("الصفحة الرئيسية", "تابع المشاهدة", "الأطول", "تمت المشاهدة", "Shorts", "YouTube")
    private val tabTexts = ArrayList<TextView>()
    private val tabLines = ArrayList<View>()

    private class NavRef(val tabIdx: Int, val icon: IconDrawable, val label: TextView)
    private val navRefs = ArrayList<NavRef>()

    private val pool = Executors.newFixedThreadPool(3)
    private val ui = Handler(Looper.getMainLooper())

    private var loadGen = 0
    private var loadedOnce = false
    private var refreshManual = true
    private var shuffleNext = false
    private val shuffleRank = HashMap<Long, Int>()

    private val observerReload = Runnable {
        if (!refreshing && playerRoot.visibility != View.VISIBLE) loadVideos()
    }

    // MediaStore يرسل عشرات التنبيهات أثناء الفحص، فنجمعها في تحميل واحد
    private val mediaObserver = object : ContentObserver(ui) {
        override fun onChange(selfChange: Boolean) {
            ui.removeCallbacks(observerReload)
            ui.postDelayed(observerReload, 1000)
        }
    }

    // Coil: يستخرج إطار الفيديو كصورة مصغّرة ويخزّنها في الذاكرة
    private val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .build()
    }

    private fun loadFont(name: String): Typeface? {
        if (Build.VERSION.SDK_INT < 26) return null
        return try {
            val id = resources.getIdentifier(name, "font", packageName)
            if (id == 0) null else resources.getFont(id)
        } catch (e: Exception) { null }
    }
    private val fontRegular: Typeface by lazy { loadFont("cairo_regular") ?: Typeface.DEFAULT }
    private val fontBold: Typeface by lazy { loadFont("cairo_bold") ?: Typeface.DEFAULT_BOLD }

    private fun applyFont(v: View) {
        if (v is TextView) {
            val bold = v.typeface === fontBold || v.typeface?.isBold == true
            v.typeface = if (bold) fontBold else fontRegular
        }
        if (v is ViewGroup) { for (i in 0 until v.childCount) applyFont(v.getChildAt(i)) }
    }

    private fun buzz(v: View) { try { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } catch (_: Exception) {} }

    private lateinit var rootFrame: FrameLayout
    private lateinit var playerRoot: FrameLayout
    private lateinit var aspectLayout: AspectRatioFrameLayout
    private lateinit var surfaceView: SurfaceView
    private lateinit var controls: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var playerTitle: TextView
    private lateinit var playPause: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var timeText: TextView
    private lateinit var speedBtn: TextView
    private lateinit var fsBtn: ImageView
    private lateinit var unlockBtn: ImageView
    private lateinit var hudText: TextView
    private lateinit var commentsPanel: LinearLayout
    private lateinit var commentsList: LinearLayout
    private lateinit var commentsTitle: TextView
    private lateinit var commentBadge: TextView
    private lateinit var gestureOverlay: View
    private var commentsOpen = false
    private var commentsCount = 0
    private lateinit var gestures: GestureDetector
    private val pauseDrawable = IconDrawable(IconDrawable.PAUSE, Color.WHITE)
    private val playDrawable = IconDrawable(IconDrawable.PLAY_FILLED, Color.WHITE)

    private var player: ExoPlayer? = null
    private var prepared = false
    private var current: VideoItem? = null
    private var videoW = 0
    private var videoH = 0
    private var userSeeking = false
    private var lastSaveTime = 0L
    private var locked = false
    private var fullscreen = false
    private val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private var speedIdx = 2
    private var gestureMode = 0
    private val hideHud = Runnable { hudText.visibility = View.GONE }
    private val hideUnlock = Runnable { if (locked) unlockBtn.visibility = View.GONE }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            val p = player ?: return
            if (state == Player.STATE_READY) {
                prepared = true
                val dur = p.duration
                if (dur != C.TIME_UNSET && dur > 0) {
                    seekBar.max = dur.toInt()
                }
            } else if (state == Player.STATE_ENDED) {
                current?.let { saveProgress(p, it) }
            }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playPause.setImageDrawable(if (isPlaying) pauseDrawable else playDrawable)
            showControls()
        }
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            videoW = videoSize.width; videoH = videoSize.height
            if (videoH > 0) {
                aspectLayout.setAspectRatio(videoW.toFloat() / videoH.toFloat())
            }
        }
    }

    private fun saveProgress(p: ExoPlayer, c: VideoItem) {
        try {
            val dur = p.duration
            if (dur == C.TIME_UNSET || dur <= 0) return
            val ended = p.playbackState == Player.STATE_ENDED
            val pos = if (ended) 0L else p.currentPosition
            store.save(c.id, pos, ended)
        } catch (_: Exception) {}
    }

    private val ticker = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && prepared) {
                try {
                    val pos = p.currentPosition
                    val dur = p.duration
                    if (dur != C.TIME_UNSET && dur > 0) {
                        if (!userSeeking) {
                            seekBar.max = dur.toInt()
                            seekBar.progress = pos.toInt()
                        }
                        timeText.text = fmt(pos) + " / " + fmt(dur)
                        val now = System.currentTimeMillis()
                        if (now - lastSaveTime > 5000) {
                            current?.let { saveProgress(p, it) }
                            lastSaveTime = now
                        }
                    }
                } catch (_: Exception) {}
            }
            ui.postDelayed(this, 500)
        }
    }

    private val pickFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Toast.makeText(this@MainActivity, "تعذّر حفظ صلاحية المجلد", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        val path = treeToPath(uri)
        if (path == null) {
            Toast.makeText(this@MainActivity, "تعذّر قراءة المجلد", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        folderPath = path; prefs.folderPath = path; loadVideos()
    }

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val v = pendingDelete ?: return@registerForActivityResult
        val needsRetry = deleteNeedsRetry
        if (result.resultCode == RESULT_OK) {
            if (needsRetry) {
                try {
                    if (contentResolver.delete(v.uri, null, null) > 0) afterDeleted(v)
                    else Toast.makeText(this@MainActivity, "تعذّر الحذف", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) { Toast.makeText(this@MainActivity, "تعذّر الحذف", Toast.LENGTH_SHORT).show() }
            } else { afterDeleted(v) }
        }
        pendingDelete = null; deleteNeedsRetry = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        DownloadCenter.init(this)
        try { NewPipe.init(SimpleDownloader()) } catch (_: Exception) {}
        folderPath = prefs.folderPath
        tab = savedInstanceState?.getInt("tab") ?: prefs.lastTab
        query = savedInstanceState?.getString("query") ?: ""
        pendingOpenId = savedInstanceState?.let { if (it.getBoolean("player_open")) it.getLong("current_id", -1L) else -1L } ?: -1L
        pendingRestoreComments = savedInstanceState?.getBoolean("comments_open") == true

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::dlPanel.isInitialized && dlPanel.visibility == View.VISIBLE) {
                    hideDownloads()
                } else if (playerRoot.visibility == View.VISIBLE) {
                    if (locked) showUnlockBtn()
                    else if (commentsPanel.visibility == View.VISIBLE) hideComments()
                    else if (fullscreen) setFullscreen(false)
                    else closePlayer()
                } else if (tab == YT_TAB) {
                    if (!ytTab.handleBack()) selectTab(0)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        rootFrame = FrameLayout(this)
        rootFrame.setBackgroundColor(bg)

        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        main.layoutDirection = View.LAYOUT_DIRECTION_RTL

        main.addView(buildTopBlock())
        main.addView(buildTabs(), LinearLayout.LayoutParams(-1, -2))

        // ── القائمة: RecyclerView + ViewHolder + DiffUtil ──
        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setBackgroundColor(bg)
            clipToPadding = false
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }
        val style = VideoAdapter.Style(
            fontBold, fontRegular, red, gray,
            resources.displayMetrics.density, resources.displayMetrics.widthPixels
        )
        adapter = VideoAdapter(style, imageLoader, { openPlayer(it) }, { showDeleteDialog(it) })
        recycler.adapter = adapter

        continueHeader = LinearLayout(this)
        continueHeader.orientation = LinearLayout.VERTICAL

        val listColumn = LinearLayout(this)
        listColumn.orientation = LinearLayout.VERTICAL
        listColumn.addView(continueHeader, LinearLayout.LayoutParams(-1, -2))
        listColumn.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))

        emptyText = TextView(this)
        emptyText.text = "جارٍ تحميل الفيديوهات..."
        emptyText.setTextColor(gray)
        emptyText.textSize = 16f
        emptyText.gravity = Gravity.CENTER
        emptyText.setPadding(dp(24), dp(24), dp(24), dp(24))

        val listFrame = FrameLayout(this)
        listFrame.addView(listColumn, FrameLayout.LayoutParams(-1, -1))
        listFrame.addView(emptyText, FrameLayout.LayoutParams(-1, -1))

        ytTab = YoutubeTab(this, { folderPath }, { reloadVideos() }, { openPlayer(it) }, { startDownloadFromUrl(it) })
        ytPanel = ytTab.view
        listFrame.addView(ytPanel, FrameLayout.LayoutParams(-1, -1))
        ytPanel.visibility = if (tab == YT_TAB) View.VISIBLE else View.GONE
        if (tab == YT_TAB) ytTab.onShown()

        refreshBar = TextView(this)
        refreshBar.text = "↻ جارٍ التحديث..."
        refreshBar.setTextColor(Color.WHITE)
        refreshBar.textSize = 14f
        refreshBar.gravity = Gravity.CENTER
        refreshBar.setPadding(dp(16), dp(8), dp(16), dp(8))
        refreshBar.background = GradientDrawable().apply { setColor(red); cornerRadius = dp(20).toFloat() }
        refreshBar.elevation = dp(8).toFloat()
        refreshBar.visibility = View.GONE
        listFrame.addView(refreshBar, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(10) })

        recycler.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { touchStartY = e.y; pulling = false }
                MotionEvent.ACTION_MOVE -> {
                    if (!recycler.canScrollVertically(-1) && !refreshing && e.y - touchStartY > dp(120)) pulling = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if (pulling) { pulling = false; reloadVideos() } }
            }
            false
        }

        main.addView(listFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        main.addView(buildBottomNav(), LinearLayout.LayoutParams(-1, dp(68)))
        rootFrame.addView(main, FrameLayout.LayoutParams(-1, -1))
        buildDownloadsPanel()

        buildPlayer()
        playerRoot.layoutDirection = View.LAYOUT_DIRECTION_LTR
        rootFrame.addView(playerRoot, FrameLayout.LayoutParams(-1, -1))
        playerRoot.visibility = View.GONE

        addSplash()
        setContentView(rootFrame)
        applyFont(rootFrame)
        ui.post(ticker)
        ui.post(dlTicker)

        // Room لا يسمح بالاستعلام على الخيط الرئيسي: نحمّل المواضع أولًا ثم نبدأ تحميل الفيديوهات
        pool.execute {
            try {
                listOf("thumbs_hq_v2", "thumbs_hq_v3", "thumbs_hq_v4").forEach { File(cacheDir, it).deleteRecursively() }
            } catch (_: Exception) {}
            try { store.load() } catch (_: Exception) {}
            ui.post {
                if (isFinishing || isDestroyed) return@post
                applyFilter()
                checkPermissionAndLoad()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
        outState.putString("query", query)
        outState.putBoolean("player_open", playerRoot.visibility == View.VISIBLE)
        outState.putBoolean("fullscreen", fullscreen)
        outState.putBoolean("comments_open", commentsOpen && commentsPanel.visibility == View.VISIBLE)
        current?.let { c ->
            outState.putLong("current_id", c.id)
            val p = player
            if (p != null && prepared) saveProgress(p, c)
        }
    }

    private fun addSplash() {
        val splash = LinearLayout(this)
        splash.orientation = LinearLayout.VERTICAL
        splash.gravity = Gravity.CENTER
        splash.setBackgroundColor(bg)
        splash.isClickable = true

        val sIcon = TextView(this)
        sIcon.text = "▶"; sIcon.setTextColor(Color.WHITE); sIcon.textSize = 30f; sIcon.gravity = Gravity.CENTER
        sIcon.background = GradientDrawable().apply { setColor(red); cornerRadius = dp(16).toFloat() }
        sIcon.setPadding(dp(22), dp(10), dp(22), dp(10))
        splash.addView(sIcon, LinearLayout.LayoutParams(-2, -2))

        val sName = TextView(this)
        sName.text = appName; sName.setTextColor(Color.WHITE); sName.textSize = 28f; sName.typeface = Typeface.DEFAULT_BOLD; sName.gravity = Gravity.CENTER
        splash.addView(sName, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) })

        rootFrame.addView(splash, FrameLayout.LayoutParams(-1, -1))

        sIcon.scaleX = 0.7f; sIcon.scaleY = 0.7f
        sIcon.animate().scaleX(1f).scaleY(1f).setDuration(500).setInterpolator(DecelerateInterpolator()).start()
        ui.postDelayed({ splash.animate().alpha(0f).setDuration(350).withEndAction { rootFrame.removeView(splash) }.start() }, 900)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun fmt(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%02d:%02d", m, sec)
    }

    private fun buildTopBlock(): View {
        val top = LinearLayout(this)
        top.orientation = LinearLayout.VERTICAL
        top.setBackgroundColor(bg)
        top.setPadding(dp(14), dp(14), dp(14), dp(6))

        logo = LinearLayout(this)
        logo.orientation = LinearLayout.HORIZONTAL
        logo.gravity = Gravity.CENTER_VERTICAL

        val iconBox = TextView(this)
        iconBox.text = "▶"; iconBox.setTextColor(Color.WHITE); iconBox.textSize = 15f
        iconBox.gravity = Gravity.CENTER
        iconBox.background = GradientDrawable().apply { setColor(red); cornerRadius = dp(10).toFloat() }
        val ibLp = LinearLayout.LayoutParams(dp(34), dp(34))
        logo.addView(iconBox, ibLp)

        val title = TextView(this)
        title.text = appName; title.setTextColor(Color.WHITE); title.textSize = 19f; title.typeface = fontBold
        title.setPadding(dp(10), 0, 0, 0)
        logo.addView(title)

        val spacer = View(this)
        logo.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))

        val refreshBtn = ImageButton(this)
        refreshBtn.setImageDrawable(IconDrawable(IconDrawable.REFRESH, navInactive))
        refreshBtn.background = null
        refreshBtn.contentDescription = "تحديث"
        refreshBtn.setOnClickListener { buzz(it); reloadVideos() }
        logo.addView(refreshBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
        refreshViews.add(refreshBtn)

        val folderBtn = ImageButton(this)
        folderBtn.setImageDrawable(IconDrawable(IconDrawable.FOLDER, navInactive))
        folderBtn.background = null
        folderBtn.contentDescription = "اختيار مجلد"
        folderBtn.setOnClickListener { buzz(it); pickFolderLauncher.launch(null) }
        logo.addView(folderBtn, LinearLayout.LayoutParams(dp(40), dp(40)))

        val dlBox = FrameLayout(this)
        val dlBtn = ImageButton(this)
        dlBtn.setImageDrawable(IconDrawable(IconDrawable.DOWNLOAD, navInactive))
        dlBtn.background = null
        dlBtn.contentDescription = "التنزيلات"
        dlBtn.setOnClickListener { buzz(it); if (dlPanel.visibility == View.VISIBLE) hideDownloads() else showDownloads() }
        dlBox.addView(dlBtn, FrameLayout.LayoutParams(dp(40), dp(40)))
        dlBadge = TextView(this)
        dlBadge.setTextColor(Color.WHITE); dlBadge.textSize = 10f; dlBadge.gravity = Gravity.CENTER
        dlBadge.background = GradientDrawable().apply { setColor(red); shape = GradientDrawable.OVAL }
        dlBadge.visibility = View.GONE
        dlBox.addView(dlBadge, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.TOP or Gravity.END))
        logo.addView(dlBox, LinearLayout.LayoutParams(dp(40), dp(40)))

        top.addView(logo)

        searchBox = EditText(this)
        searchBox.hint = "ابحث عن فيديو..."
        searchBox.setTextColor(Color.WHITE); searchBox.setHintTextColor(Color.parseColor("#777777"))
        searchBox.textSize = 15f; searchBox.isSingleLine = true
        searchBox.background = GradientDrawable().apply {
            setColor(Color.parseColor("#1F1F1F")); cornerRadius = dp(22).toFloat()
            setStroke(dp(1), Color.parseColor("#333333"))
        }
        searchBox.setPadding(dp(18), dp(10), dp(18), dp(10))
        searchBox.setText(query)
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                applyFilter()
            }
        })
        top.addView(searchBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        return top
    }

    private fun buildTabs(): View {
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.setBackgroundColor(bg)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(10), dp(6), dp(10), dp(6))
        row.layoutDirection = View.LAYOUT_DIRECTION_RTL

        for ((i, name) in tabNames.withIndex()) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER

            val tv = TextView(this)
            tv.text = name; tv.textSize = 14f; tv.setPadding(dp(14), dp(8), dp(14), dp(6))
            tv.setTextColor(if (i == tab) Color.WHITE else gray)
            tv.typeface = if (i == tab) fontBold else fontRegular

            val line = View(this)
            line.setBackgroundColor(red)
            val lp = LinearLayout.LayoutParams(-1, dp(3))
            lp.marginStart = dp(10); lp.marginEnd = dp(10)
            line.layoutParams = lp
            line.visibility = if (i == tab) View.VISIBLE else View.INVISIBLE

            col.addView(tv); col.addView(line)
            tv.setOnClickListener { buzz(it); selectTab(i) }
            tabTexts.add(tv); tabLines.add(line)
            row.addView(col, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(2) })
        }
        scroll.addView(row)
        return scroll
    }

    private fun selectTab(idx: Int) {
        if (tab == idx && idx != YT_TAB) return
        val old = tab
        tab = idx; prefs.lastTab = idx
        for (i in tabTexts.indices) {
            tabTexts[i].setTextColor(if (i == idx) Color.WHITE else gray)
            tabTexts[i].typeface = if (i == idx) fontBold else fontRegular
            tabLines[i].visibility = if (i == idx) View.VISIBLE else View.INVISIBLE
        }
        ytPanel.visibility = if (idx == YT_TAB) View.VISIBLE else View.GONE
        if (idx == YT_TAB) {
            ytTab.onShown()
        } else if (old == YT_TAB) {
            ytTab.onHidden()
        }
        applyFilter()
    }

    private fun buildBottomNav(): View {
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(Color.parseColor("#151515"))
        nav.gravity = Gravity.CENTER
        nav.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val items = arrayOf(
            "الرئيسية" to IconDrawable.HOME,
            "بحث" to IconDrawable.SEARCH,
            "المجلد" to IconDrawable.FOLDER,
            "YouTube" to IconDrawable.PLAY,
            "تحديث" to IconDrawable.REFRESH
        )
        for ((i, item) in items.withIndex()) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER
            col.setPadding(0, dp(8), 0, dp(6))

            val isRefresh = i == 4
            val icon = IconDrawable(item.second, if (isRefresh) Color.WHITE else if (i == 0) navActive else navInactive)
            val iv = ImageView(this)
            iv.setImageDrawable(icon)
            if (isRefresh) {
                iv.background = GradientDrawable().apply { setColor(red); shape = GradientDrawable.OVAL }
                iv.setPadding(dp(6), dp(6), dp(6), dp(6))
                iv.elevation = dp(3).toFloat()
                col.addView(iv, LinearLayout.LayoutParams(dp(32), dp(32)))
                refreshViews.add(iv)
            } else {
                col.addView(iv, LinearLayout.LayoutParams(dp(24), dp(24)))
            }

            val tv = TextView(this)
            tv.text = item.first; tv.textSize = 11f
            tv.setTextColor(if (i == 0) navActive else navInactive)
            col.addView(tv)

            col.setOnClickListener { buzz(it); handleNav(i) }
            if (!isRefresh) navRefs.add(NavRef(i, icon, tv))
            nav.addView(col, LinearLayout.LayoutParams(0, -1, 1f))
        }
        return nav
    }

    private fun handleNav(i: Int) {
        if (i == 4) { reloadVideos(); return }
        when (i) {
            0 -> selectTab(0)
            1 -> {
                searchBox.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT)
            }
            2 -> pickFolderLauncher.launch(null)
            3 -> selectTab(YT_TAB)
        }
        for (ref in navRefs) {
            val active = ref.tabIdx == i
            ref.icon.setIconColor(if (active) navActive else navInactive)
            ref.label.setTextColor(if (active) navActive else navInactive)
        }
    }

    private fun updateNavForTab() {
        val activeIdx = if (tab == YT_TAB) 3 else if (tab == 0) 0 else -1
        for (ref in navRefs) {
            val active = ref.tabIdx == activeIdx
            ref.icon.setIconColor(if (active) navActive else navInactive)
            ref.label.setTextColor(if (active) navActive else navInactive)
        }
    }

    private fun applyFilter() {
        if (tab == YT_TAB) return
        shown.clear()
        val q = query.trim().lowercase(Locale.getDefault())
        val base = if (q.isEmpty()) all else all.filter { it.title.lowercase(Locale.getDefault()).contains(q) }
        when (tab) {
            0 -> shown.addAll(base)
            1 -> shown.addAll(base.filter { val pos = store.getPosition(it.id); pos > 0L && pos < it.duration })
            2 -> shown.addAll(base.sortedByDescending { it.duration })
            3 -> shown.addAll(base.filter { store.isWatched(it.id) })
            4 -> shown.addAll(base.filter { it.duration in 1L..60_000L })
        }
        updateContinueHeader()
        adapter.submitList(shown.map { VideoRow(it, store.getPosition(it.id)) })
        emptyText.text = if (shown.isEmpty()) {
            if (tab == 1) "لا توجد فيديوهات قيد المشاهدة" else "لا توجد فيديوهات"
        } else ""
        emptyText.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        updateNavForTab()
    }

    private fun updateContinueHeader() {
        continueHeader.removeAllViews()
        if (tab != 1 || shown.isEmpty()) { continueHeader.visibility = View.GONE; return }
        continueHeader.visibility = View.VISIBLE
        val title = TextView(this)
        title.text = "تابع من حيث توقفت"; title.setTextColor(Color.WHITE); title.textSize = 16f; title.typeface = fontBold
        title.setPadding(dp(16), dp(12), dp(16), dp(8))
        continueHeader.addView(title)
    }

    private fun setSpinning(on: Boolean) {
        spinAnim?.cancel(); spinAnim = null
        refreshViews.forEach { it.rotation = 0f }
        if (!on) return
        spinAnim = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 800; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { a -> val r = a.animatedValue as Float; refreshViews.forEach { v -> v.rotation = r } }
            start()
        }
    }

    /** يُدخل في مكتبة الجهاز الفيديوهات الجديدة فقط (غير المفهرسة) داخل المجلد المختار */
    @Suppress("DEPRECATION")
    private fun scanMediaFolders() {
        try {
            val exts = setOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v")
            val dirs = ArrayList<File>()
            val depth: Int
            if (folderPath.isNotEmpty()) {
                dirs.add(File(folderPath))
                depth = if (includeSubfolders) 3 else 1
            } else {
                dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES))
                dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                depth = 3
            }
            val indexed = HashSet<String>()
            val (sel, args) = folderSelection()
            contentResolver.query(videoCollection(), arrayOf(MediaStore.Video.Media.DATA), sel, args, null)?.use { c ->
                val i = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                while (c.moveToNext()) c.getString(i)?.let { indexed.add(it) }
            }
            val files = ArrayList<String>()
            for (d in dirs.distinct()) {
                d.walkTopDown().maxDepth(depth)
                    .filter { it.isFile && it.extension.lowercase() in exts && it.path !in indexed }
                    .forEach { if (files.size < 300) files.add(it.path) }
            }
            if (files.isEmpty()) return
            val latch = CountDownLatch(files.size)
            MediaScannerConnection.scanFile(this, files.toTypedArray(), null) { _, _ -> latch.countDown() }
            latch.await(4, TimeUnit.SECONDS)
        } catch (_: Exception) {}
    }

    private fun reloadVideos(manual: Boolean = true) {
        if (tab == YT_TAB && manual) {
            ytTab.reload()
            Toast.makeText(this, "تم تحديث صفحة YouTube", Toast.LENGTH_SHORT).show()
            return
        }
        if (refreshing) return
        refreshing = true
        refreshManual = manual
        if (manual) shuffleNext = true
        if (manual) {
            refreshBar.visibility = View.VISIBLE
            setSpinning(true)
        }
        val started = System.currentTimeMillis()
        pool.execute {
            scanMediaFolders()
            if (manual) {
                val wait = 700L - (System.currentTimeMillis() - started)
                if (wait > 0) { try { Thread.sleep(wait) } catch (_: Exception) {} }
            }
            ui.post { loadVideos() }
        }
    }

    private fun afterDeleted(v: VideoItem) {
        all.removeAll { it.id == v.id }
        store.remove(v.id)
        applyFilter()
    }

    private fun checkPermissionAndLoad() {
        if (Build.VERSION.SDK_INT >= 33) {
            val perm = Manifest.permission.READ_MEDIA_VIDEO
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(perm), 100)
                return
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
                return
            }
        }
        loadVideos()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) loadVideos()
    }

    /** true = يشمل المجلدات الفرعية داخل المجلد المختار، false = المجلد نفسه فقط */
    private val includeSubfolders = false

    /** شرط الاستعلام: فيديوهات المجلد المختار فقط. إن لم يُختر مجلد بعد تُعرض كل الفيديوهات. */
    @Suppress("DEPRECATION")
    private fun folderSelection(): Pair<String?, Array<String>?> {
        val dir = folderPath.trimEnd('/')
        if (dir.isEmpty()) return Pair(null, null)
        val esc = dir.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val col = MediaStore.Video.Media.DATA
        return if (includeSubfolders) {
            Pair("$col LIKE ? ESCAPE '\\'", arrayOf("$esc/%"))
        } else {
            Pair(
                "$col LIKE ? ESCAPE '\\' AND $col NOT LIKE ? ESCAPE '\\'",
                arrayOf("$esc/%", "$esc/%/%")
            )
        }
    }

    private fun videoCollection(): Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    private fun loadVideos() {
        val gen = ++loadGen
        pool.execute {
            val result = ArrayList<VideoItem>()
            try {
                val collection = videoCollection()
                val proj = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DURATION)
                val sort = MediaStore.Video.Media.DATE_ADDED + " DESC"

                val (selection, args) = folderSelection()
                contentResolver.query(collection, proj, selection, args, sort)?.use { c ->
                    val idIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val tIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                    val dIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    while (c.moveToNext()) {
                        val id = c.getLong(idIdx)
                        val title = c.getString(tIdx) ?: "فيديو"
                        val dur = c.getLong(dIdx)
                        val uri = ContentUris.withAppendedId(collection, id)
                        result.add(VideoItem(id, title, dur, uri))
                    }
                }
            } catch (_: Exception) {}
            ui.post {
                // نتيجة قديمة سبقتها نتيجة أحدث: نتجاهلها
                if (gen != loadGen) return@post
                // زر التحديث: خلط جديد للترتيب
                val didShuffle = shuffleNext
                if (shuffleNext) {
                    shuffleNext = false
                    shuffleRank.clear()
                    result.shuffled().forEachIndexed { i, item -> shuffleRank[item.id] = i }
                }
                // يبقى الخلط ثابتًا في التحديثات التلقائية؛ والفيديو الجديد يظهر في الأعلى
                val ordered: List<VideoItem> =
                    if (shuffleRank.isEmpty()) result else result.sortedBy { shuffleRank[it.id] ?: -1 }
                // لا نعيد بناء القائمة إن لم يتغير شيء (يمنع الوميض)
                if (!loadedOnce || ordered != all) {
                    all.clear(); all.addAll(ordered)
                    loadedOnce = true
                    applyFilter()
                    if (didShuffle) recycler.scrollToPosition(0)
                }
                if (refreshing) {
                    refreshing = false
                    refreshBar.visibility = View.GONE
                    setSpinning(false)
                    if (refreshManual) Toast.makeText(this@MainActivity, "تم خلط الفيديوهات 🔀  (" + all.size + " فيديو)", Toast.LENGTH_SHORT).show()
                }
                restorePendingPlayer()
            }
        }
    }

    private fun restorePendingPlayer() {
        if (pendingOpenId != -1L) {
            val v = all.find { it.id == pendingOpenId }
            pendingOpenId = -1L
            if (v != null) openPlayer(v)
        }
        updateNavForTab()
    }

    private fun buildPlayer() {
        playerRoot = FrameLayout(this)
        playerRoot.setBackgroundColor(Color.BLACK)

        aspectLayout = AspectRatioFrameLayout(this)
        aspectLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {}
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })
        aspectLayout.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
        playerRoot.addView(aspectLayout, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

        gestureOverlay = View(this)
        playerRoot.addView(gestureOverlay, FrameLayout.LayoutParams(-1, -1))

        hudText = TextView(this)
        hudText.setTextColor(Color.WHITE); hudText.textSize = 16f; hudText.typeface = fontBold
        hudText.background = GradientDrawable().apply { setColor(Color.parseColor("#CC000000")); cornerRadius = dp(12).toFloat() }
        hudText.setPadding(dp(16), dp(10), dp(16), dp(10))
        hudText.visibility = View.GONE
        playerRoot.addView(hudText, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        controls = LinearLayout(this)
        controls.orientation = LinearLayout.VERTICAL
        controls.gravity = Gravity.CENTER
        controls.setBackgroundColor(Color.TRANSPARENT)

        topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.setPadding(dp(12), dp(10), dp(12), dp(10))
        topBar.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#CC000000"), Color.TRANSPARENT))

        val backBtn = ImageButton(this)
        backBtn.setImageDrawable(IconDrawable(IconDrawable.BACK, Color.WHITE))
        backBtn.background = null
        backBtn.contentDescription = "رجوع"
        backBtn.setOnClickListener { buzz(it); closePlayer() }
        topBar.addView(backBtn, LinearLayout.LayoutParams(dp(40), dp(40)))

        playerTitle = TextView(this)
        playerTitle.setTextColor(Color.WHITE); playerTitle.textSize = 14f; playerTitle.typeface = fontBold
        playerTitle.isSingleLine = true; playerTitle.ellipsize = android.text.TextUtils.TruncateAt.END
        topBar.addView(playerTitle, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })

        unlockBtn = ImageButton(this)
        unlockBtn.setImageDrawable(IconDrawable(IconDrawable.UNLOCK, Color.WHITE))
        unlockBtn.background = null
        unlockBtn.visibility = View.GONE
        unlockBtn.contentDescription = "فتح القفل"
        unlockBtn.setOnClickListener { buzz(it); unlockPlayer() }
        topBar.addView(unlockBtn, LinearLayout.LayoutParams(dp(40), dp(40)))

        controls.addView(topBar, LinearLayout.LayoutParams(-1, -2))

        val midSpace = View(this)
        controls.addView(midSpace, LinearLayout.LayoutParams(-1, 0, 1f))

        val midRow = LinearLayout(this)
        midRow.orientation = LinearLayout.HORIZONTAL
        midRow.gravity = Gravity.CENTER

        val rewind = ImageButton(this)
        rewind.setImageDrawable(IconDrawable(IconDrawable.ROTATE, Color.WHITE))
        rewind.background = null
        rewind.scaleX = -1f
        rewind.contentDescription = "رجوع 10 ثوانٍ"
        rewind.setOnClickListener { buzz(it); player?.seekBack(); showControls() }
        midRow.addView(rewind, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(30) })

        playPause = ImageView(this)
        playPause.setImageDrawable(pauseDrawable)
        playPause.background = GradientDrawable().apply { setColor(Color.parseColor("#55000000")); shape = GradientDrawable.OVAL }
        playPause.setPadding(dp(14), dp(14), dp(14), dp(14))
        playPause.contentDescription = "تشغيل / إيقاف مؤقت"
        playPause.setOnClickListener { buzz(it); togglePlay() }
        midRow.addView(playPause, LinearLayout.LayoutParams(dp(72), dp(72)))

        val forward = ImageButton(this)
        forward.setImageDrawable(IconDrawable(IconDrawable.ROTATE, Color.WHITE))
        forward.background = null
        forward.contentDescription = "تقديم 10 ثوانٍ"
        forward.setOnClickListener { buzz(it); player?.seekForward(); showControls() }
        midRow.addView(forward, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(30) })

        controls.addView(midRow, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER })

        val midSpace2 = View(this)
        controls.addView(midSpace2, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottomBar = LinearLayout(this)
        bottomBar.orientation = LinearLayout.VERTICAL
        bottomBar.setPadding(dp(12), dp(8), dp(12), dp(12))
        bottomBar.background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(Color.parseColor("#CC000000"), Color.TRANSPARENT))

        val timeRow = LinearLayout(this)
        timeRow.orientation = LinearLayout.HORIZONTAL
        timeRow.gravity = Gravity.CENTER_VERTICAL

        timeText = TextView(this)
        timeText.setTextColor(Color.WHITE); timeText.textSize = 12f
        timeRow.addView(timeText, LinearLayout.LayoutParams(-2, -2))

        val sp = View(this)
        timeRow.addView(sp, LinearLayout.LayoutParams(0, 1, 1f))

        speedBtn = TextView(this)
        speedBtn.text = "1x"; speedBtn.setTextColor(Color.WHITE); speedBtn.textSize = 13f
        speedBtn.setPadding(dp(12), dp(4), dp(12), dp(4))
        speedBtn.background = GradientDrawable().apply { setColor(Color.parseColor("#44FFFFFF")); cornerRadius = dp(12).toFloat() }
        speedBtn.setOnClickListener { buzz(it); cycleSpeed() }
        timeRow.addView(speedBtn)

        commentBadge = TextView(this)
        commentBadge.text = "💬"; commentBadge.textSize = 16f; commentBadge.gravity = Gravity.CENTER
        commentBadge.setPadding(dp(10), dp(4), dp(10), dp(4))
        commentBadge.background = GradientDrawable().apply { setColor(Color.parseColor("#44FFFFFF")); cornerRadius = dp(12).toFloat() }
        commentBadge.setOnClickListener { buzz(it); toggleComments() }
        timeRow.addView(commentBadge, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })

        // الزر الجديد: الرجوع إلى القائمة الرئيسية (بجانب زر التكبير)
        val homeBtn = ImageButton(this)
        homeBtn.setImageDrawable(IconDrawable(IconDrawable.HOME, Color.WHITE))
        homeBtn.background = null
        homeBtn.contentDescription = "الرئيسية"
        homeBtn.setOnClickListener { buzz(it); closePlayer(); selectTab(0) }
        timeRow.addView(homeBtn, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginStart = dp(4) })

        fsBtn = ImageButton(this)
        fsBtn.setImageDrawable(IconDrawable(IconDrawable.EXPAND, Color.WHITE))
        fsBtn.background = null
        fsBtn.contentDescription = "ملء الشاشة"
        fsBtn.setOnClickListener { buzz(it); setFullscreen(!fullscreen) }
        timeRow.addView(fsBtn, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginStart = dp(4) })

        bottomBar.addView(timeRow)

        seekBar = SeekBar(this)
        seekBar.max = 1000
        seekBar.progressTintList = android.content.res.ColorStateList.valueOf(red)
        seekBar.thumbTintList = android.content.res.ColorStateList.valueOf(red)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val d = player?.duration ?: 0L
                    timeText.text = fmt(progress.toLong()) + " / " + fmt(if (d > 0L) d else 0L)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                userSeeking = false
                player?.seekTo((sb?.progress ?: 0).toLong())
            }
        })
        bottomBar.addView(seekBar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        controls.addView(bottomBar, LinearLayout.LayoutParams(-1, -2))

        playerRoot.addView(controls, FrameLayout.LayoutParams(-1, -1))

        commentsPanel = LinearLayout(this)
        commentsPanel.orientation = LinearLayout.VERTICAL
        commentsPanel.setBackgroundColor(Color.parseColor("#F0121212"))
        commentsPanel.visibility = View.GONE

        val cHead = LinearLayout(this)
        cHead.orientation = LinearLayout.HORIZONTAL
        cHead.gravity = Gravity.CENTER_VERTICAL
        cHead.setPadding(dp(14), dp(12), dp(14), dp(12))

        commentsTitle = TextView(this)
        commentsTitle.text = "التعليقات"; commentsTitle.setTextColor(Color.WHITE); commentsTitle.textSize = 16f; commentsTitle.typeface = fontBold
        cHead.addView(commentsTitle, LinearLayout.LayoutParams(0, -2, 1f))

        val closeC = ImageButton(this)
        closeC.setImageDrawable(IconDrawable(IconDrawable.BACK, Color.WHITE))
        closeC.background = null
        closeC.contentDescription = "إغلاق التعليقات"
        closeC.setOnClickListener { hideComments() }
        cHead.addView(closeC, LinearLayout.LayoutParams(dp(40), dp(40)))

        commentsPanel.addView(cHead)

        val cScroll = ScrollView(this)
        commentsList = LinearLayout(this)
        commentsList.orientation = LinearLayout.VERTICAL
        cScroll.addView(commentsList)
        commentsPanel.addView(cScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        playerRoot.addView(commentsPanel, FrameLayout.LayoutParams(dp(320), -1, Gravity.END))

        setupGestures()
    }

    private fun setupGestures() {
        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (controls.visibility == View.VISIBLE) hideControls() else showControls()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val w = gestureOverlay.width
                if (e.x < w / 3f) {
                    player?.seekBack(); hud("⏪ 10 ثوانٍ")
                } else if (e.x > 2 * w / 3f) {
                    player?.seekForward(); hud("⏩ 10 ثوانٍ")
                } else {
                    togglePlay()
                }
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (e1 == null) return false
                val w = gestureOverlay.width.toFloat()
                val h = gestureOverlay.height.toFloat()
                if (w <= 0f || h <= 0f) return false

                if (gestureMode == 0) {
                    val ddx = Math.abs(e2.x - e1.x)
                    val ddy = Math.abs(e2.y - e1.y)
                    gestureMode = if (ddx > ddy) 2 else 1
                }

                if (gestureMode == 1) {
                    if (e1.x < w / 2f) {
                        val lp = window.attributes
                        val cur = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                        val nb = (cur + dy / h).coerceIn(0.05f, 1f)
                        lp.screenBrightness = nb
                        window.attributes = lp
                        hud("🔆 السطوع " + (nb * 100).toInt() + "%")
                    } else {
                        val cur = player?.volume ?: 1f
                        val nv = (cur + dy / h).coerceIn(0f, 1f)
                        player?.volume = nv
                        hud("🔊 الصوت " + (nv * 100).toInt() + "%")
                    }
                } else if (gestureMode == 2) {
                    val p = player ?: return false
                    val dur = p.duration
                    if (dur != C.TIME_UNSET && dur > 0L) {
                        val delta = (dx / w * 60000f).toLong()
                        val np = (p.currentPosition - delta).coerceIn(0L, dur)
                        p.seekTo(np)
                        hud(fmt(np) + " / " + fmt(dur))
                    }
                }
                return true
            }

            override fun onDown(e: MotionEvent): Boolean = true
        })

        gestureOverlay.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { gestureMode = 0; v.performClick() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { gestureMode = 0; ui.postDelayed(hideHud, 800) }
            }
            gestures.onTouchEvent(e)
        }
    }

    private fun hud(msg: String) {
        hudText.text = msg; hudText.visibility = View.VISIBLE
        ui.removeCallbacks(hideHud); ui.postDelayed(hideHud, 1200)
    }

    private fun showControls() {
        controls.animate().alpha(1f).setDuration(150).withStartAction { controls.visibility = View.VISIBLE }.start()
        ui.removeCallbacks(hideControlsRunnable)
        ui.postDelayed(hideControlsRunnable, 3500)
    }

    private val hideControlsRunnable = Runnable {
        if (!commentsOpen && player?.isPlaying == true) {
            controls.animate().alpha(0f).setDuration(200).withEndAction { if (controls.alpha == 0f) controls.visibility = View.INVISIBLE }.start()
        }
    }

    private fun hideControls() {
        controls.animate().alpha(0f).setDuration(200).withEndAction { controls.visibility = View.INVISIBLE }.start()
    }

    private fun showUnlockBtn() {
        unlockBtn.visibility = View.VISIBLE
        ui.removeCallbacks(hideUnlock)
        ui.postDelayed(hideUnlock, 2500)
    }

    private fun unlockPlayer() {
        locked = false
        unlockBtn.visibility = View.GONE
        gestureOverlay.visibility = View.VISIBLE
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) { p.pause(); playPause.setImageDrawable(playDrawable) }
        else { p.play(); playPause.setImageDrawable(pauseDrawable) }
        showControls()
    }

    private fun cycleSpeed() {
        speedIdx = (speedIdx + 1) % speeds.size
        val sp = speeds[speedIdx]
        player?.setPlaybackSpeed(sp)
        speedBtn.text = if (sp == sp.toInt().toFloat()) "${sp.toInt()}x" else "${sp}x"
        showControls()
    }

    private fun setCutoutMode(always: Boolean) {
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                if (always && Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
    }

    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (on) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(window, false)
            setCutoutMode(true)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            // الصورة تملأ الشاشة كاملة بلا حواف سوداء
            aspectLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            topBar.visibility = View.GONE
            fsBtn.setImageDrawable(IconDrawable(IconDrawable.COMPRESS, Color.WHITE))
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
            setCutoutMode(false)
            aspectLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            topBar.visibility = View.VISIBLE
            fsBtn.setImageDrawable(IconDrawable(IconDrawable.EXPAND, Color.WHITE))
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && fullscreen) {
            WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun openPlayer(v: VideoItem) {
        current = v
        playerTitle.text = v.title
        commentsOpen = false
        commentsPanel.visibility = View.GONE
        commentsList.removeAllViews()
        commentsCount = 0
        expandedCommentIds.clear()
        commentBadge.text = "💬"

        playerRoot.visibility = View.VISIBLE
        controls.visibility = View.VISIBLE
        controls.alpha = 1f
        playPause.setImageDrawable(pauseDrawable)
        speedIdx = 2; speedBtn.text = "1x"

        val p = player ?: ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .also { player = it }

        prepared = false
        p.setVideoSurfaceView(surfaceView)
        p.removeListener(playerListener)
        p.addListener(playerListener)
        p.setPlaybackSpeed(1f)
        p.clearMediaItems()
        p.setMediaItem(MediaItem.fromUri(v.uri))

        val savedPos = store.getPosition(v.id)
        p.prepare()
        p.playWhenReady = true
        if (savedPos > 0L) {
            p.seekTo(savedPos)
        }

        showControls()
        if (pendingRestoreComments) {
            pendingRestoreComments = false
            commentsOpen = true
            commentsPanel.visibility = View.VISIBLE
            fetchComments(v)
        }
    }

    private fun closePlayer() {
        val p = player
        val c = current
        if (p != null && c != null && prepared) saveProgress(p, c)
        playerRoot.visibility = View.GONE
        if (fullscreen) setFullscreen(false)
        p?.stop()
        p?.clearMediaItems()
        prepared = false
        current = null
        commentsPanel.visibility = View.GONE
        commentsOpen = false
        applyFilter()
    }

    private fun toggleComments() {
        if (commentsOpen) hideComments() else showComments()
    }

    private fun showComments() {
        commentsOpen = true
        commentsPanel.visibility = View.VISIBLE
        val v = current ?: return
        fetchComments(v)
    }

    private fun hideComments() {
        commentsOpen = false
        commentsPanel.visibility = View.GONE
    }

    private fun fetchComments(v: VideoItem) {
        commentsList.removeAllViews()
        val loading = TextView(this)
        loading.text = "جارٍ تحميل التعليقات..."; loading.setTextColor(gray); loading.textSize = 14f
        loading.setPadding(dp(16), dp(16), dp(16), dp(16))
        commentsList.addView(loading)

        val title = v.title
        pool.execute {
            try {
                val url = "https://www.youtube.com/results?search_query=" + Uri.encode(title)
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val html = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val videoIdMatch = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"").find(html)
                if (videoIdMatch == null) {
                    ui.post { showCommentsError("لم يتم العثور على الفيديو") }
                    return@execute
                }
                val videoId = videoIdMatch.groupValues[1]
                loadRealComments(videoId)
            } catch (e: Exception) {
                ui.post { showCommentsError("تعذّر تحميل التعليقات: ${e.message}") }
            }
        }
    }

    private fun showCommentsError(msg: String) {
        commentsList.removeAllViews()
        val tv = TextView(this)
        tv.text = msg; tv.setTextColor(gray); tv.textSize = 14f
        tv.setPadding(dp(16), dp(16), dp(16), dp(16))
        commentsList.addView(tv)
    }

    // تُنفَّذ على خيط خلفي (فيها طلب شبكة)
    private fun loadRealComments(videoId: String) {
        try {
            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            val ci = CommentsInfo.getInfo(ServiceList.YouTube, videoUrl)
            val items = ci.relatedItems
            val count = ci.commentsCount
            ui.post {
                commentsCount = count
                commentBadge.text = if (count > 0) "💬 $count" else "💬"
                commentsTitle.text = "التعليقات ($count)"
                commentsList.removeAllViews()
                if (items.isEmpty()) {
                    showCommentsError("لا توجد تعليقات")
                } else {
                    for (item in items) {
                        addCommentView(item)
                    }
                }
            }
        } catch (e: Exception) {
            ui.post { showCommentsError("تعذّر تحميل التعليقات: ${e.message}") }
        }
    }

    private fun addCommentView(item: CommentsInfoItem) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(dp(14), dp(8), dp(14), dp(8))

        val author = TextView(this)
        author.text = item.uploaderName ?: "مجهول"
        author.setTextColor(red); author.textSize = 13f; author.typeface = fontBold
        row.addView(author)

        val comment = TextView(this)
        comment.text = item.commentText?.content ?: ""
        comment.setTextColor(Color.WHITE); comment.textSize = 13f
        comment.setPadding(0, dp(4), 0, dp(4))
        row.addView(comment)

        val likes = TextView(this)
        likes.text = "👍 ${item.likeCount}"
        likes.setTextColor(gray); likes.textSize = 11f
        row.addView(likes)

        commentsList.addView(row)

        val divider = View(this)
        divider.setBackgroundColor(Color.parseColor("#2A2A2A"))
        commentsList.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
    }

    override fun onPause() {
        super.onPause()
        val p = player; val c = current
        if (p != null && c != null && prepared) saveProgress(p, c)
        val playing = p != null && p.isPlaying
        var inPip = false
        if (playing && playerRoot.visibility == View.VISIBLE && Build.VERSION.SDK_INT >= 26) {
            try {
                enterPictureInPictureMode(PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9)).build())
                inPip = true
            } catch (_: Exception) {}
        }
        if (playing && !inPip) p?.pause()
    }

    override fun onResume() {
        super.onResume()
        if (!firstResume) {
            if (playerRoot.visibility == View.VISIBLE) loadVideos() else reloadVideos(false)
        }
        firstResume = false
        if (!observerRegistered) {
            try {
                contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
                observerRegistered = true
            } catch (_: Exception) {}
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) {
            player?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacks(dlTicker)
        ui.removeCallbacks(ticker)
        pool.shutdown()
        ui.removeCallbacks(observerReload)
        try { contentResolver.unregisterContentObserver(mediaObserver) } catch (_: Exception) {}
        player?.release()
        player = null
        ytTab.onDestroy()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            controls.visibility = View.GONE
            commentsPanel.visibility = View.GONE
        } else {
            controls.visibility = View.VISIBLE
        }
    }

    private fun treeToPath(uri: Uri): String? {
        if (Build.VERSION.SDK_INT >= 21 && DocumentsContract.isTreeUri(uri)) {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val parts = docId.split(":")
            return if (parts.size >= 2) {
                val type = parts[0]; val path = parts[1]
                @Suppress("DEPRECATION")
                if (type == "primary") Environment.getExternalStorageDirectory().path + "/" + path
                else "/storage/$type/$path"
            } else null
        }
        return null
    }

    // ───────────── التنزيلات ─────────────

    private class QualityOption(val label: String, val videoUrl: String, val audioUrl: String?, val ext: String)

    private fun startDownloadFromUrl(url: String?) {
        if (url == null) return
        Toast.makeText(this, "جارٍ جلب الجودات المتاحة...", Toast.LENGTH_SHORT).show()
        pool.execute {
            try {
                val info = StreamInfo.getInfo(ServiceList.YouTube, url)
                val options = ArrayList<QualityOption>()

                // جودات عالية: فيديو بدون صوت (H.264) + أفضل صوت M4A ثم دمجهما
                val bestAudio = info.audioStreams
                    .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == NpFormat.M4A }
                    .maxByOrNull { it.averageBitrate }
                if (bestAudio != null) {
                    info.videoOnlyStreams
                        .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                                  it.format == NpFormat.MPEG_4 &&
                                  it.codec?.startsWith("avc1") == true }
                        .groupBy { it.height }
                        .forEach { (_, list) ->
                            val best = list.maxByOrNull { it.bitrate } ?: list.first()
                            options.add(QualityOption("${best.resolution} • mp4 (دمج صوت)", best.content, bestAudio.content, "mp4"))
                        }
                }
                // جودات جاهزة بصوتها (غالبًا 360p)
                val haveHeights = options.map { it.label.substringBefore("p") }.toSet()
                info.videoStreams
                    .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                    .forEach {
                        if (it.resolution.substringBefore("p") !in haveHeights) {
                            options.add(QualityOption("${it.resolution} • ${it.format?.suffix ?: "mp4"}", it.content, null, it.format?.suffix ?: "mp4"))
                        }
                    }
                options.sortByDescending { it.label.substringBefore("p").toIntOrNull() ?: 0 }

                ui.post {
                    if (isFinishing || isDestroyed) return@post
                    if (options.isEmpty()) {
                        Toast.makeText(this, "لا توجد جودة متاحة للتحميل لهذا الفيديو", Toast.LENGTH_LONG).show()
                        return@post
                    }
                    val labels = options.map { it.label }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("اختر الجودة")
                        .setItems(labels) { _, which ->
                            val o = options[which]
                            DownloadCenter.enqueue(info.name, o.videoUrl, o.ext, o.audioUrl)
                            Toast.makeText(this, "بدأ التحميل (${DownloadCenter.SEGMENTS} أجزاء متوازية)", Toast.LENGTH_SHORT).show()
                            showDownloads()
                        }
                        .setNegativeButton("إلغاء", null)
                        .show()
                }
            } catch (e: Exception) {
                ui.post { Toast.makeText(this, "تعذّر جلب الفيديو: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun buildDownloadsPanel() {
        dlPanel = LinearLayout(this)
        dlPanel.orientation = LinearLayout.VERTICAL
        dlPanel.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val r = dp(18).toFloat()
        dlPanel.background = GradientDrawable().apply {
            setColor(Color.parseColor("#F2151515"))
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
        dlPanel.elevation = dp(16).toFloat()
        dlPanel.isClickable = true
        dlPanel.visibility = View.GONE

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(16), dp(12), dp(8), dp(8))
        val title = TextView(this)
        title.text = "التنزيلات"; title.setTextColor(Color.WHITE); title.textSize = 16f; title.typeface = fontBold
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val close = TextView(this)
        close.text = "✕"; close.setTextColor(Color.WHITE); close.textSize = 18f
        close.setPadding(dp(12), dp(6), dp(12), dp(6))
        close.contentDescription = "إغلاق"
        close.setOnClickListener { buzz(it); hideDownloads() }
        head.addView(close)
        dlPanel.addView(head)

        dlEmpty = TextView(this)
        dlEmpty.text = "لا توجد تنزيلات.\nافتح فيديو في تبويب YouTube ثم اضغط زر التحميل الأحمر."
        dlEmpty.setTextColor(gray); dlEmpty.textSize = 14f; dlEmpty.gravity = Gravity.CENTER
        dlEmpty.setPadding(dp(24), dp(24), dp(24), dp(24))
        dlPanel.addView(dlEmpty, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this)
        dlList = LinearLayout(this)
        dlList.orientation = LinearLayout.VERTICAL
        dlList.setPadding(dp(8), 0, dp(8), dp(12))
        scroll.addView(dlList)
        dlPanel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        applyFont(dlPanel)
        rootFrame.addView(dlPanel, FrameLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.6f).toInt(), Gravity.BOTTOM))
    }

    private fun showDownloads() { dlPanel.visibility = View.VISIBLE; refreshDownloads() }
    private fun hideDownloads() { dlPanel.visibility = View.GONE }

    private fun createDlRow(t: DownloadTask): DlRow {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(10), dp(12), dp(10))
        root.background = GradientDrawable().apply { setColor(Color.parseColor("#1F1F1F")); cornerRadius = dp(12).toFloat() }
        root.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) }

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = t.title; title.setTextColor(Color.WHITE); title.textSize = 13f; title.typeface = fontBold
        title.isSingleLine = true; title.ellipsize = android.text.TextUtils.TruncateAt.END
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val action = TextView(this)
        action.setTextColor(Color.WHITE); action.textSize = 12f
        action.setPadding(dp(12), dp(4), dp(12), dp(4))
        action.background = GradientDrawable().apply { setColor(Color.parseColor("#44FFFFFF")); cornerRadius = dp(12).toFloat() }
        action.setOnClickListener {
            buzz(it)
            if (t.isActive) DownloadCenter.cancel(t) else { DownloadCenter.remove(t); refreshDownloads() }
        }
        top.addView(action, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        root.addView(top)

        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 1000
        bar.progressTintList = android.content.res.ColorStateList.valueOf(red)
        bar.indeterminateTintList = android.content.res.ColorStateList.valueOf(red)
        root.addView(bar, LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(8) })

        val status = TextView(this)
        status.setTextColor(gray); status.textSize = 11f
        status.setPadding(0, dp(6), 0, 0)
        root.addView(status)

        applyFont(root)
        return DlRow(root, title, bar, status, action)
    }

    private fun fmtSize(b: Long): String =
        if (b >= 1_048_576L) String.format(Locale.US, "%.1f MB", b / 1048576.0)
        else String.format(Locale.US, "%d KB", b / 1024)

    private fun updateDlRow(r: DlRow, t: DownloadTask) {
        val done = t.downloaded.get()
        val total = t.total
        val now = System.currentTimeMillis()
        if (t.state == DownloadTask.DOWNLOADING) {
            if (r.lastTime == 0L) { r.lastTime = now; r.lastBytes = done }
            else if (now - r.lastTime >= 1000) {
                r.speed = (done - r.lastBytes) * 1000 / (now - r.lastTime)
                r.lastBytes = done; r.lastTime = now
            }
        }
        r.action.text = if (t.isActive) "إلغاء" else "إزالة"
        when (t.state) {
            DownloadTask.QUEUED -> {
                r.bar.isIndeterminate = true
                r.status.text = "جارٍ التحضير..."
            }
            DownloadTask.DOWNLOADING -> {
                val parts = if (t.segments > 1) "${t.segments} أجزاء" else "اتصال واحد"
                if (total > 0) {
                    r.bar.isIndeterminate = false
                    r.bar.progress = (done * 1000 / total).toInt().coerceIn(0, 1000)
                    r.status.text = "${done * 100 / total}% • ${fmtSize(done)} / ${fmtSize(total)} • ${fmtSize(r.speed)}/ث • $parts"
                } else {
                    r.bar.isIndeterminate = true
                    r.status.text = "${fmtSize(done)} • ${fmtSize(r.speed)}/ث • $parts"
                }
            }
            DownloadTask.SAVING -> {
                r.bar.isIndeterminate = true
                r.status.text = if (t.audioUrl != null) "جارٍ دمج الصوت والصورة وحفظ الملف..." else "جارٍ الحفظ في المعرض..."
            }
            DownloadTask.DONE -> {
                r.bar.isIndeterminate = false; r.bar.progress = 1000
                r.status.text = "اكتمل ✓ (Movies/TubeTV)"
            }
            DownloadTask.FAILED -> {
                r.bar.isIndeterminate = false
                r.status.text = "فشل: ${t.error ?: ""}"
            }
            DownloadTask.CANCELED -> {
                r.bar.isIndeterminate = false
                r.status.text = "أُلغي"
            }
        }
    }

    private fun refreshDownloads() {
        val tasks = DownloadCenter.tasks
        val active = tasks.count { it.isActive }
        for (t in tasks) { if (t.state == DownloadTask.DONE && notifiedDone.add(t.id)) loadVideos() }
        dlBadge.visibility = if (active > 0) View.VISIBLE else View.GONE
        dlBadge.text = active.toString()
        if (dlPanel.visibility != View.VISIBLE) return

        for (t in tasks.reversed()) {
            var row = dlRows[t.id]
            if (row == null) {
                row = createDlRow(t)
                dlRows[t.id] = row
                dlList.addView(row.root, 0)
            }
            updateDlRow(row, t)
        }
        val ids = tasks.map { it.id }.toSet()
        for (id in dlRows.keys.filter { it !in ids }) {
            dlRows.remove(id)?.let { dlList.removeView(it.root) }
        }
        dlEmpty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showDeleteDialog(v: VideoItem) {
        AlertDialog.Builder(this)
            .setTitle("حذف الفيديو")
            .setMessage("هل أنت متأكد من حذف \"${v.title}\"؟")
            .setPositiveButton("حذف") { _, _ -> deleteVideo(v) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun deleteVideo(v: VideoItem) {
        pendingDelete = v
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val deleteRequest = MediaStore.createDeleteRequest(contentResolver, listOf(v.uri))
                deleteNeedsRetry = false
                deleteLauncher.launch(IntentSenderRequest.Builder(deleteRequest.intentSender).build())
            } else {
                if (contentResolver.delete(v.uri, null, null) > 0) afterDeleted(v)
                else Toast.makeText(this, "تعذّر الحذف", Toast.LENGTH_SHORT).show()
            }
        } catch (e: RecoverableSecurityException) {
            deleteNeedsRetry = true
            try {
                deleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
            } catch (_: Exception) {
                Toast.makeText(this, "تعذّر الحذف", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "تعذّر الحذف", Toast.LENGTH_SHORT).show()
        }
    }

    /** إعدادات بسيطة فقط (المجلد والتبويب). مواضع المشاهدة انتقلت إلى Room عبر PositionStore. */
    class Prefs(context: Context) {
        private val sp: SharedPreferences = context.getSharedPreferences("tube_pro", Context.MODE_PRIVATE)

        var folderPath: String
            get() = sp.getString("folder", "") ?: ""
            set(v) { sp.edit().putString("folder", v).apply() }

        var lastTab: Int
            get() = sp.getInt("tab", 0)
            set(v) { sp.edit().putInt("tab", v).apply() }
    }
}
