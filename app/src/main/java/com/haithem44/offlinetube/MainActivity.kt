package com.haithem44.offlinetube

import android.Manifest
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.util.Rational
import android.util.Size
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.MediaFormat as NpFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class VideoItem(val id: Long, val title: String, val duration: Long, val uri: Uri)

private data class RowState(
    val status: String,
    val pct: Int,
    val action: String,
    val color: String,
    val clickable: Boolean
)

data class DownloadRecord(
    val vid: String,
    val url: String,
    val title: String,
    val height: Int,
    val videoUrl: String,
    val audioUrl: String?,
    val totalSize: Long,
    val videoSize: Long,
    val audioSize: Long,
    val durationMs: Long
)

class IconDrawable(private val kind: Int, initial: Int) : Drawable() {
    companion object {
        const val HOME = 0; const val SEARCH = 1; const val PLAY = 2; const val REFRESH = 3
        const val FOLDER = 4; const val TRASH = 5; const val BACK = 6; const val LOCK = 7
        const val UNLOCK = 8; const val PAUSE = 9; const val PLAY_FILLED = 10; const val EXPAND = 11
        const val ROTATE = 12; const val PIP = 13; const val COMPRESS = 14; const val COMMENT = 15
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND; color = initial
    }

    fun setIconColor(c: Int) { paint.color = c; invalidateSelf() }

    override fun draw(canvas: Canvas) {
        val b = bounds; val size = minOf(b.width(), b.height()).toFloat()
        if (size <= 0f) return
        val s = size / 24f
        canvas.save()
        canvas.translate(b.left + (b.width() - size) / 2f, b.top + (b.height() - size) / 2f)
        canvas.scale(s, s); paint.strokeWidth = 2f; paint.style = Paint.Style.STROKE
        val p = Path()
        when (kind) {
            HOME -> {
                p.moveTo(3f, 11f); p.lineTo(12f, 3f); p.lineTo(21f, 11f)
                p.moveTo(5f, 10f); p.lineTo(5f, 20f); p.lineTo(10f, 20f)
                p.lineTo(10f, 14f); p.lineTo(14f, 14f); p.lineTo(14f, 20f)
                p.lineTo(19f, 20f); p.lineTo(19f, 10f)
            }
            SEARCH -> { canvas.drawCircle(10.5f, 10.5f, 6.5f, paint); p.moveTo(15.3f, 15.3f); p.lineTo(21f, 21f) }
            PLAY -> { p.moveTo(8f, 5f); p.lineTo(19f, 12f); p.lineTo(8f, 19f); p.close() }
            REFRESH -> { canvas.drawArc(RectF(4f, 4f, 20f, 20f), 25f, 300f, false, paint); p.moveTo(18.5f, 2.5f); p.lineTo(18.5f, 7.5f); p.lineTo(13.5f, 7.5f) }
            FOLDER -> { p.moveTo(3f, 6f); p.lineTo(9f, 6f); p.lineTo(11f, 8f); p.lineTo(21f, 8f); p.lineTo(21f, 19f); p.lineTo(3f, 19f); p.close() }
            TRASH -> {
                p.moveTo(3.5f, 6f); p.lineTo(20.5f, 6f)
                p.moveTo(9f, 6f); p.lineTo(9f, 4.5f); p.quadTo(9f, 3.5f, 10f, 3.5f); p.lineTo(14f, 3.5f); p.quadTo(15f, 3.5f, 15f, 4.5f); p.lineTo(15f, 6f)
                p.moveTo(5.5f, 6f); p.lineTo(6.4f, 19.2f); p.quadTo(6.5f, 20.5f, 7.8f, 20.5f); p.lineTo(16.2f, 20.5f); p.quadTo(17.5f, 20.5f, 17.6f, 19.2f); p.lineTo(18.5f, 6f)
                p.moveTo(10f, 10f); p.lineTo(10f, 16.5f)
                p.moveTo(14f, 10f); p.lineTo(14f, 16.5f)
            }
            BACK -> { p.moveTo(20f, 12f); p.lineTo(4f, 12f); p.moveTo(10f, 6f); p.lineTo(4f, 12f); p.lineTo(10f, 18f) }
            LOCK -> { canvas.drawRoundRect(RectF(5f, 11f, 19f, 21f), 2f, 2f, paint); p.moveTo(8f, 11f); p.lineTo(8f, 8f); p.arcTo(RectF(8f, 4f, 16f, 12f), 180f, 180f); p.lineTo(16f, 11f) }
            UNLOCK -> { canvas.drawRoundRect(RectF(5f, 11f, 19f, 21f), 2f, 2f, paint); p.moveTo(8f, 11f); p.lineTo(8f, 8f); p.arcTo(RectF(8f, 4f, 16f, 12f), 180f, 180f) }
            PAUSE -> { paint.style = Paint.Style.FILL; canvas.drawRoundRect(RectF(6f, 4f, 10.5f, 20f), 1.5f, 1.5f, paint); canvas.drawRoundRect(RectF(13.5f, 4f, 18f, 20f), 1.5f, 1.5f, paint); paint.style = Paint.Style.STROKE }
            PLAY_FILLED -> { paint.style = Paint.Style.FILL; p.moveTo(7f, 4.5f); p.lineTo(19f, 12f); p.lineTo(7f, 19.5f); p.close(); canvas.drawPath(p, paint); p.reset(); paint.style = Paint.Style.STROKE }
            EXPAND -> { p.moveTo(4f, 9f); p.lineTo(4f, 4f); p.lineTo(9f, 4f); p.moveTo(15f, 4f); p.lineTo(20f, 4f); p.lineTo(20f, 9f); p.moveTo(20f, 15f); p.lineTo(20f, 20f); p.lineTo(15f, 20f); p.moveTo(9f, 20f); p.lineTo(4f, 20f); p.lineTo(4f, 15f) }
            ROTATE -> { canvas.drawRoundRect(RectF(3f, 6f, 21f, 18f), 2.5f, 2.5f, paint); p.moveTo(18f, 10f); p.lineTo(18f, 14f) }
            PIP -> { canvas.drawRoundRect(RectF(3f, 5f, 21f, 19f), 2f, 2f, paint); paint.style = Paint.Style.FILL; canvas.drawRoundRect(RectF(12f, 11f, 19f, 17f), 1f, 1f, paint); paint.style = Paint.Style.STROKE }
            COMPRESS -> { p.moveTo(9f, 4f); p.lineTo(9f, 9f); p.lineTo(4f, 9f); p.moveTo(15f, 4f); p.lineTo(15f, 9f); p.lineTo(20f, 9f); p.moveTo(20f, 15f); p.lineTo(15f, 15f); p.lineTo(15f, 20f); p.moveTo(4f, 15f); p.lineTo(9f, 15f); p.lineTo(9f, 20f) }
            COMMENT -> { canvas.drawRoundRect(RectF(3f, 4f, 21f, 17f), 3f, 3f, paint); p.moveTo(8f, 17f); p.lineTo(8f, 21f); p.lineTo(12.5f, 17f) }
        }
        canvas.drawPath(p, paint); canvas.restore()
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class ShimmerView(ctx: Context) : View(ctx) {
    private val paint = Paint(); private val shMatrix = Matrix()
    private var shader: LinearGradient? = null; private var phase = -1f
    private val anim = ValueAnimator.ofFloat(-1f, 1f).apply {
        duration = 1300; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        shader = LinearGradient(0f, 0f, w.toFloat(), 0f, intArrayOf(Color.parseColor("#1F1F1F"), Color.parseColor("#363636"), Color.parseColor("#1F1F1F")), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        paint.shader = shader
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        shMatrix.setTranslate(phase * width, 0f); shader?.setLocalMatrix(shMatrix)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}

class MainActivity : AppCompatActivity() {

    private val appName: String by lazy {
        try { getString(R.string.app_name) } catch (_: Exception) { "TUBE TV PRO" }
    }

    private lateinit var prefs: Prefs
    private val bg = Color.parseColor("#0F0F0F")
    private val red = Color.parseColor("#FF0000")
    private val gray = Color.parseColor("#AAAAAA")
    private val navActive = Color.parseColor("#FF4D4D")
    private val navInactive = Color.parseColor("#E0E0E0")

    private val all = ArrayList<VideoItem>()
    private val shown = ArrayList<VideoItem>()
    private val expandedCommentIds = HashSet<Long>()

    private lateinit var listView: ListView
    private lateinit var adapter: VideoAdapter
    private lateinit var continueHeader: LinearLayout
    private lateinit var logo: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var emptyText: TextView
    private lateinit var ytTab: YoutubeTab
    private lateinit var ytPanel: FrameLayout
    private val YT_TAB = 5
    private var hasBanner = false
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
    private var pendingFullscreen = false
    private var observerRegistered = false

    private val tabNames = arrayOf("الصفحة الرئيسية", "تابع المشاهدة", "الأطول", "تمت المشاهدة", "Shorts", "YouTube")
    private val tabTexts = ArrayList<TextView>()
    private val tabLines = ArrayList<View>()

    private class NavRef(val tabIdx: Int, val icon: IconDrawable, val label: TextView)
    private val navRefs = ArrayList<NavRef>()
    private val animatedIds = HashSet<Long>()

    private val pool = Executors.newFixedThreadPool(3)
    private val ui = Handler(Looper.getMainLooper())

    private val mediaObserver = object : ContentObserver(ui) {
        override fun onChange(selfChange: Boolean) {
            if (playerRoot.visibility != View.VISIBLE) {
                loadVideos()
            }
        }
    }

    private val cache = object : LruCache<Long, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 6).toInt()) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount / 1024
        override fun entryRemoved(evicted: Boolean, key: Long, old: Bitmap, new: Bitmap?) { }
    }

    private val thumbW: Int by lazy { maxOf(resources.displayMetrics.widthPixels, 1080).coerceAtMost(2160) }
    private val thumbH: Int by lazy { thumbW * 9 / 16 }
    private val thumbDir: File by lazy { File(cacheDir, "thumbs_hq_v2").apply { mkdirs() } }

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
    private var savedResize = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private lateinit var gestures: GestureDetector
    private val pauseDrawable = IconDrawable(IconDrawable.PAUSE, Color.WHITE)
    private val playDrawable = IconDrawable(IconDrawable.PLAY_FILLED, Color.WHITE)

    private var player: ExoPlayer? = null
    private var prepared = false
    private var current: VideoItem? = null
    private var pending: VideoItem? = null
    private var videoW = 0
    private var videoH = 0
    private var isLandscape = false
    private var userSeeking = false
    private var lastSaveTime = 0L
    private var locked = false
    private var fullscreen = false
    private val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private var speedIdx = 2
    private var gestureMode = 0
    private var gestureValue = 0f
    private val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val hideHud = Runnable { hudText.visibility = View.GONE }
    private val hideUnlock = Runnable { if (locked) unlockBtn.visibility = View.GONE }

    private fun saveProgress(p: ExoPlayer, c: VideoItem) {
        try {
            val dur = p.duration
            if (dur == C.TIME_UNSET || dur <= 0) return
            val ended = p.playbackState == Player.STATE_ENDED
            val pos = if (ended) 0L else p.currentPosition
            prefs.savePosition(c.id, pos, dur, ended)
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
        folderPath = prefs.folderPath
        tab = savedInstanceState?.getInt("tab") ?: prefs.lastTab
        query = savedInstanceState?.getString("query") ?: ""
        pendingOpenId = savedInstanceState?.let { if (it.getBoolean("player_open")) it.getLong("current_id", -1L) else -1L } ?: -1L
        pendingRestoreComments = savedInstanceState?.getBoolean("comments_open") == true
        pendingFullscreen = savedInstanceState?.getBoolean("fullscreen") == true

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (playerRoot.visibility == View.VISIBLE) {
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

        listView = ListView(this)
        listView.divider = null
        listView.dividerHeight = 0
        listView.setPadding(0, 0, 0, 0)
        listView.clipToPadding = false
        listView.setBackgroundColor(bg)

        continueHeader = LinearLayout(this)
        continueHeader.orientation = LinearLayout.VERTICAL
        listView.addHeaderView(continueHeader, null, false)

        adapter = VideoAdapter()
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, pos, _ ->
            val idx = pos - listView.headerViewsCount
            if (idx in 0 until shown.size) openPlayer(shown[idx])
        }

        emptyText = TextView(this)
        emptyText.text = "جارٍ تحميل الفيديوهات..."
        emptyText.setTextColor(gray)
        emptyText.textSize = 16f
        emptyText.gravity = Gravity.CENTER
        emptyText.setPadding(dp(24), dp(24), dp(24), dp(24))

        val listFrame = FrameLayout(this)
        listFrame.addView(listView, FrameLayout.LayoutParams(-1, -1))
        listFrame.addView(emptyText, FrameLayout.LayoutParams(-1, -1))
        listView.emptyView = emptyText

        ytTab = YoutubeTab(this, { folderPath }, { reloadVideos() }, { openPlayer(it) })
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

        listView.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { touchStartY = e.y; pulling = false }
                MotionEvent.ACTION_MOVE -> {
                    val atTop = listView.childCount == 0 || (listView.firstVisiblePosition == 0 && listView.getChildAt(0).top >= 0)
                    if (atTop && !refreshing && e.y - touchStartY > dp(120)) pulling = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if (pulling) { pulling = false; reloadVideos() } }
            }
            false
        }

        main.addView(listFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        main.addView(buildBottomNav(), LinearLayout.LayoutParams(-1, dp(68)))
        rootFrame.addView(main, FrameLayout.LayoutParams(-1, -1))

        buildPlayer()
        playerRoot.layoutDirection = View.LAYOUT_DIRECTION_LTR
        rootFrame.addView(playerRoot, FrameLayout.LayoutParams(-1, -1))
        playerRoot.visibility = View.GONE

        addSplash()
        setContentView(rootFrame)
        applyFont(rootFrame)
        ui.post(ticker)
        checkPermissionAndLoad()
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
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                setBgKeepPad(view, normal); view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
        }
        false
    }
}

private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

private fun fmt(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%02d:%02d", m, sec)
}

private fun humanSize(b: Long): String {
    return when {
        b >= 1_073_741_824 -> String.format(Locale.US, "%.1f GB", b / 1_073_741_824.0)
        b >= 1_048_576 -> String.format(Locale.US, "%.0f MB", b / 1_048_576.0)
        b >= 1024 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
        else -> "$b B"
    }
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
    refreshBtn.setOnClickListener { buzz(it); reloadVideos() }
    logo.addView(refreshBtn, LinearLayout.LayoutParams(dp(40), dp(40)))

    val folderBtn = ImageButton(this)
    folderBtn.setImageDrawable(IconDrawable(IconDrawable.FOLDER, navInactive))
    folderBtn.background = null
    folderBtn.setOnClickListener { buzz(it); pickFolderLauncher.launch(null) }
    logo.addView(folderBtn, LinearLayout.LayoutParams(dp(40), dp(40)))

    top.addView(logo)

    searchBox = EditText(this)
    searchBox.hint = "ابحث عن فيديو..."
    searchBox.setTextColor(Color.WHITE); searchBox.setHintTextColor(Color.parseColor("#777777"))
    searchBox.textSize = 15f; searchBox.isSingleLine = true
    searchBox.setBackground(GradientDrawable().apply {
        setColor(Color.parseColor("#1F1F1F")); cornerRadius = dp(22).toFloat()
        setStroke(dp(1), Color.parseColor("#333333"))
    })
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
        "YouTube" to IconDrawable.PLAY
    )
    for ((i, item) in items.withIndex()) {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.setPadding(0, dp(8), 0, dp(6))

        val icon = IconDrawable(item.second, if (i == 0) navActive else navInactive)
        val iv = ImageView(this)
        iv.setImageDrawable(icon)
        col.addView(iv, LinearLayout.LayoutParams(dp(24), dp(24)))

        val tv = TextView(this)
        tv.text = item.first; tv.textSize = 11f
        tv.setTextColor(if (i == 0) navActive else navInactive)
        col.addView(tv)

        col.setOnClickListener { buzz(it); handleNav(i) }
        navRefs.add(NavRef(i, icon, tv))
        nav.addView(col, LinearLayout.LayoutParams(0, -1, 1f))
    }
    return nav
}

private fun handleNav(i: Int) {
    when (i) {
        0 -> selectTab(0)
        1 -> { searchBox.requestFocus(); val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager; imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT) }
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
        1 -> shown.addAll(base.filter { prefs.getPosition(it.id) in 1 until it.duration })
        2 -> shown.addAll(base.sortedByDescending { it.duration })
        3 -> shown.addAll(base.filter { prefs.isWatched(it.id) })
        4 -> shown.addAll(base.filter { it.duration in 1..60_000 })
    }
    updateContinueHeader()
    adapter.notifyDataSetChanged()
    emptyText.text = if (shown.isEmpty()) {
        if (tab == 1) "لا توجد فيديوهات قيد المشاهدة" else "لا توجد فيديوهات"
    } else ""
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

private fun reloadVideos() {
    if (refreshing) return
    refreshing = true
    refreshBar.visibility = View.VISIBLE
    loadVideos()
}

private fun afterDeleted(v: VideoItem) {
    all.removeAll { it.id == v.id }
    prefs.removePosition(v.id)
    applyFilter()
}

private fun checkPermissionAndLoad() {
    if (Build.VERSION.SDK_INT >= 33) {
        val perm = if (Build.VERSION.SDK_INT >= 34) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(perm), 100)
            return
        }
    } else if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 33) {
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

private fun loadVideos() {
    pool.execute {
        val result = ArrayList<VideoItem>()
        try {
            val collection = if (Build.VERSION.SDK_INT >= 29) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val proj = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DURATION)
            val sel: String? = null
            val selArgs: Array<String>? = null
            val sort = MediaStore.Video.Media.DATE_ADDED + " DESC"

            contentResolver.query(collection, proj, sel, selArgs, sort)?.use { c ->
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
            all.clear(); all.addAll(result)
            applyFilter()
            if (refreshing) {
                refreshing = false
                refreshBar.visibility = View.GONE
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
    playerRoot.addView(aspectLayout, FrameLayout.LayoutParams(-1, -1))

    gestureOverlay = View(this)
    playerRoot.addView(gestureOverlay, FrameLayout.LayoutParams(-1, -1))

    hudText = TextView(this)
    hudText.setTextColor(Color.WHITE); hudText.textSize = 16f; hudText.typeface = fontBold
    hudText.setBackground(GradientDrawable().apply { setColor(Color.parseColor("#CC000000")); cornerRadius = dp(12).toFloat() })
    hudText.setPadding(dp(16), dp(10), dp(16), dp(10))
    hudText.visibility = View.GONE
    playerRoot.addView(hudText, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER).apply { })

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
    rewind.setOnClickListener { buzz(it); player?.seekBack(); showControls() }
    midRow.addView(rewind, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(30) })

    playPause = ImageView(this)
    playPause.setImageDrawable(pauseDrawable)
    playPause.background = GradientDrawable().apply { setColor(Color.parseColor("#55000000")); shape = GradientDrawable.OVAL }
    playPause.setPadding(dp(14), dp(14), dp(14), dp(14))
    playPause.setOnClickListener { buzz(it); togglePlay() }
    midRow.addView(playPause, LinearLayout.LayoutParams(dp(72), dp(72)))

    val forward = ImageButton(this)
    forward.setImageDrawable(IconDrawable(IconDrawable.ROTATE, Color.WHITE))
    forward.background = null
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

    fsBtn = ImageButton(this)
    fsBtn.setImageDrawable(IconDrawable(IconDrawable.EXPAND, Color.WHITE))
    fsBtn.background = null
    fsBtn.setOnClickListener { buzz(it); setFullscreen(!fullscreen) }
    timeRow.addView(fsBtn, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginStart = dp(4) })

    bottomBar.addView(timeRow)

    seekBar = SeekBar(this)
    seekBar.max = 1000
    seekBar.progressTintList = android.content.res.ColorStateList.valueOf(red)
    seekBar.thumbTintList = android.content.res.ColorStateList.valueOf(red)
    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) { timeText.text = fmt(progress.toLong()) + " / " + fmt((player?.duration ?: 0).coerceAtLeast(0)) }
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
            if (e.x < w / 3f) { player?.seekBack(); hud("⏪ 10 ثوانٍ") }
            else if (e.x > 2 * w / 3f) { player?.seekForward(); hud("⏩ 10 ثوانٍ") }
            else togglePlay()
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (e1 == null) return false
            val w = gestureOverlay.width
            val h = gestureOverlay.height
            if (gestureMode == 0) {
                val ddx = e2.x - e1.x; val ddy = e2.y - e1.y
                gestureMode = if (Math.abs(ddx) > Math.abs(ddy)) 2 else 1
            }
            when (gestureMode) {
                1 -> {
                    val delta = (e1.y - e2.y) / h * 2f
                    val cur = player?.volume ?: 1f
                    val nv = (cur + delta * (e1.x < w / 2f ? 0.1f : 1f)).coerceIn(0f, 1f)
                    player?.volume = nv
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (nv * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt(), 0)
                    hud(if (e1.x < w / 2f) "🔆 السطوع" else "🔊 الصوت ${(nv * 100).toInt()}%")
                }
                2 -> {
                    val delta = (e2.x - e1.x) / w * 60000f
                    val dur = player?.duration ?: return false
                    if (dur > 0) {
                        val np = (e1.let { player?.currentPosition?.plus(delta.toLong()) ?: 0L }).coerceIn(0, dur)
                        player?.seekTo(np)
                        hud("${fmt(np)} / ${fmt(dur)}")
                    }
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

private fun setFullscreen(on: Boolean) {
    fullscreen = on
    if (on) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        topBar.visibility = View.GONE
        fsBtn.setImageDrawable(IconDrawable(IconDrawable.COMPRESS, Color.WHITE))
    } else {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        topBar.visibility = View.VISIBLE
        fsBtn.setImageDrawable(IconDrawable(IconDrawable.EXPAND, Color.WHITE))
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

    val p = player ?: ExoPlayer.Builder(this).build().also { player = it }
    prepared = false
    p.clearMediaItems()
    p.setMediaItem(MediaItem.fromUri(v.uri))

    val savedPos = prefs.getPosition(v.id)
    p.prepare()
    p.playWhenReady = true
    if (savedPos > 0) {
        p.seekTo(savedPos)
    }

    p.addListener(object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                prepared = true
                val dur = p.duration
                if (dur != C.TIME_UNSET && dur > 0) {
                    seekBar.max = dur.toInt()
                }
                if (!p.isPlaying) p.play()
            }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playPause.setImageDrawable(if (isPlaying) pauseDrawable else playDrawable)
            showControls()
        }
        override fun onVideoSizeChanged(vs: VideoSize) {
            videoW = vs.width; videoH = vs.height
            if (videoH > 0) {
                aspectLayout.setAspectRatio(videoW.toFloat() / videoH.toFloat())
            }
        }
    })

    showControls()
    if (pendingRestoreComments) {
        pendingRestoreComments = false
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
            ui.post { loadRealComments(videoId) }
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

private fun loadRealComments(videoId: String) {
    try {
        if (!ytTab.isInitialized()) return
        val svc = ServiceList.YouTube
        val info = org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(svc, "https://www.youtube.com/watch?v=" + videoId)
        val commentsUrl = info.commentsUrl
        if (commentsUrl == null || commentsUrl.isEmpty()) {
            showCommentsError("لا توجد تعليقات")
            return
        }
        val ci = CommentsInfo.getInfo(svc, commentsUrl)
        val items = ci.relatedItems
        commentsCount = ci.commentCount
        commentBadge.text = if (commentsCount > 0) "💬 $commentsCount" else "💬"
        commentsTitle.text = "التعليقات ($commentsCount)"
        commentsList.removeAllViews()
        if (items.isEmpty()) { showCommentsError("لا توجد تعليقات"); return }
        for (item in items) {
            addCommentView(item)
        }
    } catch (e: Exception) {
        showCommentsError("تعذّر تحميل التعليقات: ${e.message}")
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
    if (p != null && p.isPlaying) p.pause()
    if (Build.VERSION.SDK_INT >= 26) {
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9)).build())
        } catch (_: Exception) {}
    }
}

override fun onResume() {
    super.onResume()
    if (!firstResume) loadVideos()
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
    pool.shutdown()
    mediaObserver.let {
        try { contentResolver.unregisterContentObserver(it) } catch (_: Exception) {}
    }
    player?.release()
    player = null
    cache.evictAll()
}

override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
    super.onPictureInPictureModeChanged(isInPip, newConfig)
    if (isInPip) {
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
            if (type == "primary") Environment.getExternalStorageDirectory().path + "/" + path
            else "/storage/$type/$path"
        } else null
    }
    return null
}

private fun updateMediaStore(uri: Uri) {
    try {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        }
    } catch (_: Exception) {}
}
inner class VideoAdapter : BaseAdapter() {
    override fun getCount(): Int = shown.size
    override fun getItem(position: Int): Any = shown[position]
    override fun getItemId(position: Int): Long = shown[position].id
    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = shown[position]
        val ctx = this@MainActivity

        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.HORIZONTAL
        card.layoutDirection = View.LAYOUT_DIRECTION_RTL
        card.setPadding(dp(8), dp(6), dp(8), dp(6))
        card.background = GradientDrawable().apply {
            setColor(Color.parseColor("#1A1A1A")); cornerRadius = dp(12).toFloat()
        }
        card.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(8), dp(4), dp(8), dp(4))
        }

        val thumb = ImageView(ctx)
        thumb.scaleType = ImageView.ScaleType.CENTER_CROP
        thumb.background = GradientDrawable().apply { setColor(Color.parseColor("#2A2A2A")); cornerRadius = dp(8).toFloat() }
        thumb.clipToOutline = true
        thumb.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(8).toFloat())
            }
        }
        val thumbLp = LinearLayout.LayoutParams(dp(140), dp(80))
        thumbLp.marginEnd = dp(10)
        card.addView(thumb, thumbLp)
        loadThumb(v, thumb)

        val info = LinearLayout(ctx)
        info.orientation = LinearLayout.VERTICAL
        info.gravity = Gravity.CENTER_VERTICAL
        card.addView(info, LinearLayout.LayoutParams(0, -1, 1f))

        val title = TextView(ctx)
        title.text = v.title
        title.setTextColor(Color.WHITE); title.textSize = 14f; title.typeface = fontBold
        title.maxLines = 2; title.ellipsize = android.text.TextUtils.TruncateAt.END
        info.addView(title)

        val duration = TextView(ctx)
        duration.text = fmt(v.duration)
        duration.setTextColor(gray); duration.textSize = 12f
        duration.setPadding(0, dp(4), 0, 0)
        info.addView(duration)

        val progress = prefs.getPosition(v.id)
        if (progress > 0 && v.duration > 0) {
            val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
            bar.max = v.duration.toInt()
            bar.progress = progress.toInt()
            bar.progressTintList = android.content.res.ColorStateList.valueOf(red)
            bar.setPadding(0, dp(6), 0, 0)
            info.addView(bar, LinearLayout.LayoutParams(-1, dp(6)))
        }

        val menuBtn = ImageButton(ctx)
        menuBtn.setImageDrawable(IconDrawable(IconDrawable.TRASH, gray))
        menuBtn.background = null
        menuBtn.setOnClickListener { buzz(it); showDeleteDialog(v) }
        card.addView(menuBtn, LinearLayout.LayoutParams(dp(36), dp(36)))

        return card
    }
}

private fun loadThumb(v: VideoItem, iv: ImageView) {
    val cached = cache.get(v.id)
    if (cached != null) { iv.setImageBitmap(cached); return }
    iv.setImageDrawable(ShimmerView(this).let { shimmer ->
        GradientDrawable().apply { setColor(Color.parseColor("#2A2A2A")); cornerRadius = dp(8).toFloat() }
    })
    pool.execute {
        val file = File(thumbDir, "${v.id}.jpg")
        if (file.exists()) {
            val bmp = BitmapFactory.decodeFile(file.path)
            if (bmp != null) {
                cache.put(v.id, bmp)
                ui.post { if (v.id == iv.tag) iv.setImageBitmap(bmp) }
                return@execute
            }
        }
        try {
            val r = MediaMetadataRetriever()
            r.setDataSource(this@MainActivity, v.uri)
            val frame = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            r.release()
            if (frame != null) {
                val scaled = Bitmap.createScaledBitmap(frame, thumbW, thumbH, true)
                cache.put(v.id, scaled)
                ui.post { if (v.id == iv.tag) iv.setImageBitmap(scaled) }
                try {
                    FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
    iv.tag = v.id
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
        if (Build.VERSION.SDK_INT >= 29) {
            val deleteRequest = MediaStore.createDeleteRequest(contentResolver, listOf(v.uri))
            deleteLauncher.launch(IntentSenderRequest.Builder(deleteRequest.intentSender).build())
            deleteNeedsRetry = false
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
    class Prefs(context: Context) {
        private val sp: SharedPreferences = context.getSharedPreferences("tube_pro", Context.MODE_PRIVATE)

        var folderPath: String
            get() = sp.getString("folder", "") ?: ""
            set(v) { sp.edit().putString("folder", v).apply() }

        var lastTab: Int
            get() = sp.getInt("tab", 0)
            set(v) { sp.edit().putInt("tab", v).apply() }

        fun savePosition(id: Long, pos: Long, dur: Long, watched: Boolean) {
            sp.edit().putLong("pos_$id", pos).apply()
            if (watched) sp.edit().putBoolean("watched_$id", true).apply()
        }

        fun getPosition(id: Long): Long = sp.getLong("pos_$id", 0L)
        fun isWatched(id: Long): Boolean = sp.getBoolean("watched_$id", false)
        fun removePosition(id: Long) {
            sp.edit().remove("pos_$id").remove("watched_$id").apply()
        }
    }
}

class YoutubeTab(private val activity: MainActivity, private val getFolderPath: () -> String, private val onReload: () -> Unit, private val onPlay: (VideoItem) -> Unit) {
    val view: FrameLayout = FrameLayout(activity)
    private var web: WebView? = null
    private var initialized = false

    fun isInitialized(): Boolean = initialized

    fun onShown() {
        if (!initialized) {
            initialized = true
            setupWebView()
        }
        web?.onResume()
    }

    fun onHidden() {
        web?.onPause()
    }

    fun handleBack(): Boolean {
        val w = web ?: return false
        if (w.canGoBack()) { w.goBack(); return true }
        return false
    }

    private fun setupWebView() {
        val w = WebView(activity)
        web = w
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.settings.loadWithOverviewMode = true
        w.settings.useWideViewPort = true

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)

        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView?, newProgress: Int) {}
        }
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView?, url: String): Boolean {
                if (url.contains("youtube.com/watch") || url.contains("youtu.be/")) {
                    v?.loadUrl(url)
                    return true
                }
                return false
            }
        }
        w.loadUrl("https://m.youtube.com")
        view.addView(w, FrameLayout.LayoutParams(-1, -1))
    }
}
