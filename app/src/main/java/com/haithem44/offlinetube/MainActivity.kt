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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

data class VideoItem(val id: Long, val title: String, val duration: Long, val uri: Uri)

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
    
    // مجموعة لتتبع الفيديوهات التي تم فتح تعليقاتها في القائمة
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (playerRoot.visibility == View.VISIBLE) {
                    if (locked) showUnlockBtn() else if (commentsPanel.visibility == View.VISIBLE) hideComments() else closePlayer()
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

    private fun raised(top: Int, bottom: Int, radius: Int, pressed: Boolean = false): Drawable {
        val r = dp(radius).toFloat()
        val shadow = GradientDrawable().apply { setColor(Color.parseColor("#66000000")); cornerRadius = r }
        val edge = GradientDrawable().apply { setColor(Color.parseColor("#33FFFFFF")); cornerRadius = r }
        val face = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, if (pressed) intArrayOf(bottom, top) else intArrayOf(top, bottom)).apply { cornerRadius = r }
        val layers = LayerDrawable(arrayOf<Drawable>(shadow, edge, face))
        layers.setLayerInset(0, 0, dp(4), 0, 0); layers.setLayerInset(1, 0, 0, 0, dp(3))
        layers.setLayerInset(2, dp(1), dp(1), dp(1), if (pressed) dp(3) else dp(4))
        return layers
    }

    private fun setBgKeepPad(v: View, d: Drawable) {
        val l = v.paddingLeft; val t = v.paddingTop
        val r = v.paddingRight; val b = v.paddingBottom
        v.background = d; v.setPadding(l, t, r, b)
    }

    private fun make3D(v: View, top: Int = Color.parseColor("#3A3A3A"), bottom: Int = Color.parseColor("#1C1C1C"), radius: Int = 14) {
        val normal = raised(top, bottom, radius)
        val down = raised(top, bottom, radius, true)
        setBgKeepPad(v, normal); v.elevation = dp(6).toFloat()
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { setBgKeepPad(view, down); view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start(); buzz(view) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { setBgKeepPad(view, normal); view.animate().scaleX(1f).scaleY(1f).setDuration(120).start() }
            }
            false
        }
    }

    private fun makePlayerBtn(kind: Int, bgColor: Int = Color.parseColor("#33FFFFFF"), onClick: () -> Unit): ImageView {
        val b = ImageView(this)
        b.scaleType = ImageView.ScaleType.FIT_CENTER
        b.setImageDrawable(IconDrawable(kind, Color.WHITE))
        b.setPadding(dp(12), dp(12), dp(12), dp(12))

        val bgd = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(bgColor)
        }
        b.background = bgd
        b.setOnClickListener { onClick() }

        b.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            false
        }
        return b
    }

    private fun makeCommentButton(): View {
        val holder = FrameLayout(this)
        val icon = makePlayerBtn(IconDrawable.COMMENT) { toggleComments() }
        holder.addView(icon, FrameLayout.LayoutParams(dp(44), dp(44)))
        commentBadge = TextView(this)
        commentBadge.setTextColor(Color.WHITE)
        commentBadge.textSize = 10f
        commentBadge.typeface = Typeface.DEFAULT_BOLD
        commentBadge.gravity = Gravity.CENTER
        commentBadge.setPadding(dp(4), 0, dp(4), 0)
        commentBadge.background = GradientDrawable().apply { setColor(red); cornerRadius = dp(8).toFloat() }
        commentBadge.visibility = View.GONE
        holder.addView(
            commentBadge,
            FrameLayout.LayoutParams(-2, dp(16), Gravity.RIGHT or Gravity.TOP).apply {
                topMargin = dp(2); rightMargin = dp(2)
            }
        )
        return holder
    }

    private fun countComments(id: Long): Int = try {
        val f = File(File(filesDir, "comments"), "$id.txt")
        if (!f.exists()) 0
        else f.readText()
            .substringAfter("\n\n", "")
            .split("\n\n──────────\n\n")
            .count { it.isNotBlank() }
    } catch (_: Exception) { 0 }

    private fun findImageRes(name: String): Int {
        var id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) id = resources.getIdentifier(name, "mipmap", packageName)
        return id
    }

    private fun buildTopBlock(): FrameLayout {
        val resId = findImageRes("appbanner")
        hasBanner = resId != 0
        val h = if (hasBanner) dp(110) else dp(56)

        val block = FrameLayout(this)
        block.setBackgroundColor(bg); block.layoutParams = LinearLayout.LayoutParams(-1, h)

        if (hasBanner) {
            val iv = ImageView(this); iv.setImageResource(resId); iv.scaleType = ImageView.ScaleType.CENTER_CROP
            block.addView(iv, FrameLayout.LayoutParams(-1, -1))
            val scrim = View(this)
            scrim.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#99000000"), Color.TRANSPARENT))
            block.addView(scrim, FrameLayout.LayoutParams(-1, dp(70), Gravity.TOP))
        }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.CENTER_VERTICAL; row.setPadding(dp(12), 0, dp(8), 0)

        logo = LinearLayout(this)
        logo.orientation = LinearLayout.HORIZONTAL; logo.gravity = Gravity.CENTER_VERTICAL; logo.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val icon = TextView(this)
        icon.text = "▶"; icon.setTextColor(Color.WHITE); icon.textSize = 12f; icon.gravity = Gravity.CENTER
        val iconShape = GradientDrawable(); iconShape.setColor(red); iconShape.cornerRadius = dp(7).toFloat()
        icon.background = iconShape; icon.setPadding(dp(8), dp(3), dp(8), dp(3)); logo.addView(icon)
        val name = TextView(this)
        name.text = appName; name.setTextColor(Color.WHITE); name.textSize = 21f; name.typeface = Typeface.DEFAULT_BOLD; name.setPadding(dp(6), 0, 0, 0)
        logo.addView(name); logo.visibility = if (hasBanner) View.INVISIBLE else View.VISIBLE
        row.addView(logo, LinearLayout.LayoutParams(0, -2, 1f))

        searchBox = EditText(this)
        searchBox.hint = "ابحث عن فيديو"; searchBox.setHintTextColor(Color.LTGRAY); searchBox.setTextColor(Color.WHITE); searchBox.isSingleLine = true; searchBox.visibility = View.GONE
        val sbShape = GradientDrawable(); sbShape.setColor(Color.parseColor("#B3303030")); sbShape.cornerRadius = dp(20).toFloat()
        searchBox.background = sbShape; searchBox.setPadding(dp(14), dp(6), dp(14), dp(6))
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; refresh() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        row.addView(searchBox, LinearLayout.LayoutParams(0, -2, 1f))

        val searchBtn = ImageButton(this)
        searchBtn.scaleType = ImageView.ScaleType.FIT_CENTER; searchBtn.setImageDrawable(IconDrawable(IconDrawable.SEARCH, Color.WHITE))
        searchBtn.setPadding(dp(10), dp(8), dp(10), dp(12)); searchBtn.contentDescription = "بحث"; searchBtn.setOnClickListener { toggleSearch() }
        make3D(searchBtn, Color.parseColor("#4A4A4A"), Color.parseColor("#1E1E1E"), 21)
        val lp = LinearLayout.LayoutParams(dp(42), dp(42)); lp.marginStart = dp(8); row.addView(searchBtn, lp)
        block.addView(row, FrameLayout.LayoutParams(-1, dp(56), Gravity.TOP))
        return block
    }

    private fun toggleSearch() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (searchBox.visibility == View.VISIBLE) {
            searchBox.setText(""); searchBox.visibility = View.GONE
            logo.visibility = if (hasBanner) View.INVISIBLE else View.VISIBLE
            imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
        } else { openSearch() }
    }

    private fun openSearch() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        logo.visibility = View.GONE; searchBox.visibility = View.VISIBLE
        searchBox.requestFocus(); imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun buildTabs(): View {
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false; scroll.setBackgroundColor(bg)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL; row.setPadding(dp(6), 0, dp(6), 0)

        for (i in tabNames.indices) {
            val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL
            val t = TextView(this)
            t.text = tabNames[i]; t.textSize = 15f; t.gravity = Gravity.CENTER; t.setPadding(dp(18), dp(8), dp(18), dp(12))
            val line = View(this); col.addView(t); col.addView(line, LinearLayout.LayoutParams(-1, 0))
            col.setOnClickListener { selectTab(i) }
            tabTexts.add(t); tabLines.add(line)
            row.addView(col, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), dp(6), dp(4), dp(6)) })
        }
        scroll.addView(row); updateTabStyles(); return scroll
    }

    private fun updateTabStyles() {
        for (i in tabTexts.indices) {
            val sel = i == tab
            tabTexts[i].setTextColor(if (sel) Color.WHITE else gray)
            tabTexts[i].typeface = if (sel) fontBold else fontRegular
            setBgKeepPad(tabTexts[i], if (sel) raised(Color.parseColor("#FF3D3D"), Color.parseColor("#B71C1C"), 20) else raised(Color.parseColor("#2E2E2E"), Color.parseColor("#1A1A1A"), 20))
            tabLines[i].setBackgroundColor(Color.TRANSPARENT)
        }
        updateNavStyles()
    }

    private fun selectTab(i: Int) {
        val prev = tab
        tab = i; prefs.lastTab = i
        ytPanel.visibility = if (i == YT_TAB) View.VISIBLE else View.GONE
        if (i == YT_TAB) ytTab.onShown() else if (prev == YT_TAB) ytTab.onHidden()
        updateTabStyles(); refresh(); listView.setSelection(0)
    }

    private fun navLp() = LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) }

    private fun buildBottomNav(): View {
        val bar = LinearLayout(this); bar.orientation = LinearLayout.VERTICAL; bar.setBackgroundColor(bg)
        val divider = View(this); divider.setBackgroundColor(Color.parseColor("#2A2A2A")); bar.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        row.addView(navItem(IconDrawable.HOME, "الرئيسية", 0, { selectTab(0) }), navLp())
        row.addView(navItem(IconDrawable.SEARCH, "بحث", -1, { openSearch() }), navLp())
        row.addView(navItem(IconDrawable.PLAY, "تابع", 1, { selectTab(1) }), navLp())
        row.addView(navItem(IconDrawable.REFRESH, "تحديث", -1, { reloadVideos() }), navLp())
        row.addView(navItem(
            IconDrawable.FOLDER, "المجلد", -1,
            { pickFolder() },
            { Toast.makeText(this@MainActivity, "اضغط ضغطة عادية لتغيير المجلد", Toast.LENGTH_SHORT).show() }
        ), navLp())
        bar.addView(row, LinearLayout.LayoutParams(-1, 0, 1f)); updateNavStyles(); return bar
    }

    private fun navItem(kind: Int, label: String, tabIdx: Int, onClick: () -> Unit, onLongClick: (() -> Unit)? = null): View {
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.gravity = Gravity.CENTER
        val icon = IconDrawable(kind, navInactive); val iv = ImageView(this); iv.setImageDrawable(icon)
        val l = TextView(this); l.text = label; l.textSize = 11f; l.setTextColor(navInactive); l.gravity = Gravity.CENTER
        col.addView(iv, LinearLayout.LayoutParams(dp(24), dp(24)))
        col.addView(l, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        col.setPadding(dp(4), dp(4), dp(4), dp(6)); col.setOnClickListener { onClick() }
        if (onLongClick != null) col.setOnLongClickListener { onLongClick(); true }
        make3D(col, Color.parseColor("#333333"), Color.parseColor("#181818"), 16)
        navRefs.add(NavRef(tabIdx, icon, l)); return col
    }

    private fun updateNavStyles() {
        for (n in navRefs) {
            val c = if (n.tabIdx == tab) navActive else navInactive
            n.icon.setIconColor(c); n.label.setTextColor(c)
        }
    }

    private fun refresh() {
        if (animatedIds.size > 3000) animatedIds.clear()
        val s = query.trim().lowercase()
        var list: List<VideoItem> = all.filter { s.isEmpty() || it.title.lowercase().contains(s) }
        list = when (tab) {
            1 -> list.filter { prefs.position(it.id) > 0L && !prefs.watched(it.id) }
            2 -> list.sortedByDescending { it.duration }
            3 -> list.filter { prefs.watched(it.id) }.sortedByDescending { prefs.lastTouched(it.id) }
            4 -> list.filter { it.duration in 1L..60000L }
            5 -> emptyList()
            else -> list
        }
        shown.clear(); shown.addAll(list); adapter.notifyDataSetChanged(); rebuildContinue(); updateEmptyState()
    }

    private fun updateEmptyState() {
        emptyText.text = when {
            folderPath.isEmpty() -> "اضغط زر \"المجلد\" بالأسفل لاختيار مجلد الفيديوهات"
            all.isEmpty() -> "لا توجد فيديوهات في المجلد المحدد\nاسحب للأسفل للتحديث أو غيّر المجلد"
            query.isNotEmpty() -> "لا توجد نتائج لـ \"$query\""
            tab == 3 -> "لم تشاهد أي فيديو بعد"
            else -> "لا توجد فيديوهات"
        }
    }

    private fun rebuildContinue() {
        continueHeader.removeAllViews()
        if (tab != 0 || query.isNotBlank()) return
        val items = all.filter { prefs.position(it.id) > 3000L && !prefs.watched(it.id) }.sortedByDescending { prefs.lastTouched(it.id) }.take(10)
        if (items.isEmpty()) return
        val head = TextView(this)
        head.text = "أكمل المشاهدة"; head.setTextColor(Color.WHITE); head.textSize = 17f; head.typeface = Typeface.DEFAULT_BOLD
        head.setPadding(dp(16), dp(12), dp(16), dp(8)); continueHeader.addView(head)

        val sv = HorizontalScrollView(this); sv.isHorizontalScrollBarEnabled = false; sv.setPadding(dp(12), 0, dp(12), dp(6)); sv.clipToPadding = false
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        for (v in items) { row.addView(miniCard(v), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4); marginEnd = dp(4) }) }
        sv.addView(row); continueHeader.addView(sv); applyFont(continueHeader)
    }

    private fun miniCard(v: VideoItem): View {
        val w = dp(170); val h = w * 9 / 16
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL
        val f = FrameLayout(this); f.setBackgroundColor(Color.parseColor("#222222")); f.clipToOutline = true
        f.outlineProvider = object : ViewOutlineProvider() { override fun getOutline(view: View, o: Outline) { o.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat()) } }
        val iv = ImageView(this); iv.scaleType = ImageView.ScaleType.CENTER_CROP; f.addView(iv, FrameLayout.LayoutParams(-1, -1)); loadThumb(v, iv, null)
        val pos = prefs.position(v.id); val dur = if (v.duration > 0) v.duration else prefs.duration(v.id)
        if (pos > 0 && dur > 0) {
            val frac = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            val line = LinearLayout(this); line.orientation = LinearLayout.HORIZONTAL; line.layoutDirection = View.LAYOUT_DIRECTION_LTR
            val done = View(this); done.setBackgroundColor(red); val rest = View(this); rest.setBackgroundColor(Color.parseColor("#55FFFFFF"))
            line.addView(done, LinearLayout.LayoutParams(0, -1, frac)); line.addView(rest, LinearLayout.LayoutParams(0, -1, 1f - frac))
            f.addView(line, FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM))
        }
        col.addView(f, LinearLayout.LayoutParams(w, h))
        val t = TextView(this); t.text = v.title; t.setTextColor(Color.WHITE); t.textSize = 12f; t.maxLines = 2; t.ellipsize = android.text.TextUtils.TruncateAt.END
        t.setPadding(dp(2), dp(6), dp(2), 0); col.addView(t, LinearLayout.LayoutParams(w, -2))
        col.setOnClickListener { openPlayer(v) }; return col
    }

    private fun interleave(list: List<VideoItem>): List<VideoItem> {
        val n = list.size; if (n < 3) return list.shuffled()
        val third = n / 3
        val newer = list.subList(0, third).shuffled(); val middle = list.subList(third, third * 2).shuffled(); val older = list.subList(third * 2, n).shuffled()
        val out = ArrayList<VideoItem>(n); val max = maxOf(newer.size, middle.size, older.size)
        for (i in 0 until max) {
            if (i < newer.size) out.add(newer[i]); if (i < older.size) out.add(older[i]); if (i < middle.size) out.add(middle[i])
        }
        return out
    }

    private fun checkPermissionAndLoad() {
        val granted = when {
            Build.VERSION.SDK_INT >= 34 ->
                checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            Build.VERSION.SDK_INT >= 33 ->
                checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            else ->
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) { startLoad(); registerMediaObserver() }
        else {
            val perms = when {
                Build.VERSION.SDK_INT >= 34 -> arrayOf(
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
                Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
                else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            requestPermissions(perms, 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        val anyGranted = res.any { it == PackageManager.PERMISSION_GRANTED }
        if (anyGranted) { startLoad(); registerMediaObserver() }
        else Toast.makeText(this, "يجب منح صلاحية قراءة الفيديوهات لعرض مكتبتك", Toast.LENGTH_LONG).show()
    }

    private fun registerMediaObserver() {
        if (observerRegistered) return
        try {
            contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
            observerRegistered = true
        } catch (_: Exception) {}
    }

    private fun startLoad() { if (folderPath.isEmpty()) pickFolder() else loadVideos() }

    override fun onResume() {
        super.onResume()
        if (firstResume) { firstResume = false; return }
        if (playerRoot.visibility != View.VISIBLE) reloadVideos()
    }

    private fun loadVideos() {
        val folder = folderPath
        pool.execute {
            if (folder.isEmpty()) {
                ui.post { all.clear(); refresh(); finishRefresh() }
                return@execute
            }
            val list = ArrayList<VideoItem>()

            val proj: Array<String> = if (Build.VERSION.SDK_INT >= 29) {
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.RELATIVE_PATH
                )
            } else {
                @Suppress("DEPRECATION")
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATA
                )
            }

            try {
                contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    proj, null, null,
                    MediaStore.Video.Media.DATE_ADDED + " DESC"
                )?.use { c ->
                    val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                    @Suppress("DEPRECATION")
                    val iData = if (Build.VERSION.SDK_INT < 29) c.getColumnIndex(MediaStore.Video.Media.DATA) else -1
                    val iRel = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH) else -1

                    val normFolder = folder.trimEnd('/')
                    val primaryBase = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
                    val primaryTarget: String? = if (normFolder.startsWith(primaryBase)) {
                        normFolder.substring(primaryBase.length).trimStart('/')
                    } else null

                    while (c.moveToNext()) {
                        val id = c.getLong(iId)
                        var inFolder = false

                        if (iData >= 0) {
                            @Suppress("DEPRECATION")
                            val data = c.getString(iData)
                            if (data != null && (data == normFolder || data.startsWith("$normFolder/"))) inFolder = true
                        }
                        if (!inFolder && primaryTarget != null && iRel >= 0) {
                            val rel = c.getString(iRel)?.trimEnd('/') ?: ""
                            if (primaryTarget.isEmpty() || rel == primaryTarget || rel.startsWith("$primaryTarget/")) inFolder = true
                        }

                        if (!inFolder) continue

                        val name = (c.getString(iName) ?: "video").substringBeforeLast('.')
                        val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                        list.add(VideoItem(id, name, c.getLong(iDur), uri))
                    }
                }
            } catch (_: Exception) {}

            val mixed = interleave(list)
            ui.post {
                all.clear(); all.addAll(mixed); refresh(); finishRefresh()
                if (pendingOpenId > 0) { all.find { it.id == pendingOpenId }?.let { openPlayer(it) }; pendingOpenId = -1L }
            }
        }
    }

    private fun reloadVideos() {
        if (refreshing) return
        refreshing = true; refreshBar.text = "↻ جارٍ التحديث..."; refreshBar.visibility = View.VISIBLE; loadVideos()
    }

    private fun finishRefresh() {
        if (!refreshing) return; refreshing = false; refreshBar.text = "✓ تم التحديث"
        ui.postDelayed({ refreshBar.visibility = View.GONE; refreshBar.text = "↻ جارٍ التحديث..." }, 700)
    }

    private fun pickFolder() { try { pickFolderLauncher.launch(null) } catch (e: Exception) { Toast.makeText(this@MainActivity, "تعذّر فتح اختيار المجلد", Toast.LENGTH_SHORT).show() } }

    private fun treeToPath(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri); val parts = docId.split(":", limit = 2)
            val vol = parts[0]; val rel = if (parts.size > 1) parts[1] else ""
            val base = if (vol == "primary") Environment.getExternalStorageDirectory().absolutePath else "/storage/$vol"
            if (rel.isEmpty()) base else "$base/$rel"
        } catch (e: Exception) { null }
    }

    private fun confirmDelete(v: VideoItem) {
        AlertDialog.Builder(this)
            .setTitle("حذف الفيديو؟").setMessage("سيتم حذف \"${v.title}\" من الهاتف نهائياً.")
            .setPositiveButton("حذف") { _, _ -> deleteVideo(v) }.setNegativeButton("إلغاء", null).show()
    }

    private fun deleteVideo(v: VideoItem) {
        pendingDelete = v; deleteNeedsRetry = false
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val pi = MediaStore.createDeleteRequest(contentResolver, listOf(v.uri))
                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } else {
                val n = contentResolver.delete(v.uri, null, null); pendingDelete = null
                if (n > 0) afterDeleted(v) else Toast.makeText(this@MainActivity, "تعذّر الحذف", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                deleteNeedsRetry = true
                try { deleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()) }
                catch (_: Exception) { pendingDelete = null; deleteNeedsRetry = false; Toast.makeText(this@MainActivity, "تعذّر الحذف", Toast.LENGTH_SHORT).show() }
            } else { pendingDelete = null; Toast.makeText(this@MainActivity, "تعذّر الحذف", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun afterDeleted(v: VideoItem) {
        all.remove(v); cache.remove(v.id); try { thumbFile(v).delete() } catch (_: Exception) {}
        try { File(File(filesDir, "comments"), "${v.id}.txt").delete() } catch (_: Exception) {}
        expandedCommentIds.remove(v.id) // إزالة حالة التعليقات
        prefs.clearVideo(v.id); refresh(); Toast.makeText(this@MainActivity, "تم حذف الفيديو", Toast.LENGTH_SHORT).show()
    }

    inner class VideoAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = shown[p].id
        override fun getViewTypeCount() = 1
        override fun getItemViewType(p: Int) = 0
        override fun getView(p: Int, convertView: View?, parent: ViewGroup?): View = videoItemView(shown[p])
    }

    private fun screenW() = resources.displayMetrics.widthPixels

    private fun videoItemView(v: VideoItem): View {
        val w = screenW(); val th = w * 9 / 16
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, 0, 0, dp(6))

        val card = LinearLayout(this); card.orientation = LinearLayout.VERTICAL
        card.setBackgroundColor(bg)
        box.addView(card, LinearLayout.LayoutParams(-1, -2))

        card.addView(thumbFrame(v, w, th), LinearLayout.LayoutParams(-1, th))

        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(12), dp(8), dp(8), dp(8))

        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL
        val title = TextView(this); title.text = v.title; title.setTextColor(Color.WHITE); title.textSize = 15f; title.maxLines = 2; title.ellipsize = android.text.TextUtils.TruncateAt.END
        col.addView(title)
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))

        // حاوية التعليقات (مخفية افتراضياً)
        val commentsContainer = LinearLayout(this)
        commentsContainer.orientation = LinearLayout.VERTICAL
        commentsContainer.setPadding(dp(12), 0, dp(12), dp(12))
        commentsContainer.visibility = View.GONE
        card.addView(commentsContainer)

        // دالة داخلية لملء التعليقات
        fun populateComments(container: LinearLayout, id: Long) {
            container.removeAllViews()
            val comments = try {
                val f = File(File(filesDir, "comments"), "$id.txt")
                if (!f.exists()) emptyList()
                else f.readText().substringAfter("\n\n", "").split("\n\n──────────\n\n").filter { it.isNotBlank() }
            } catch (_: Exception) { emptyList() }

            if (comments.isEmpty()) {
                val empty = TextView(this)
                empty.text = "لا توجد تعليقات محفوظة."
                empty.setTextColor(gray)
                empty.textSize = 13f
                container.addView(empty)
            } else {
                for (cm in comments) {
                    val nl = cm.indexOf('\n')
                    val author = if (nl > 0) cm.substring(0, nl) else ""
                    val body = if (nl > 0) cm.substring(nl + 1) else cm
                    
                    val a = TextView(this)
                    a.text = author
                    a.setTextColor(Color.parseColor("#AAAAAA"))
                    a.textSize = 12f
                    a.typeface = fontBold
                    a.setPadding(0, dp(8), 0, dp(2))
                    a.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
                    
                    val b = TextView(this)
                    b.text = body
                    b.setTextColor(Color.WHITE)
                    b.textSize = 14f
                    b.setPadding(0, 0, 0, dp(4))
                    b.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
                    
                    container.addView(a)
                    container.addView(b)
                    
                    // خط فاصل خفيف
                    val divider = View(this)
                    divider.setBackgroundColor(Color.parseColor("#1AFFFFFF"))
                    container.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(0, dp(2), 0, dp(2)) })
                }
            }
        }

        // إذا كان هذا الفيديو مفتوحاً مسبقاً، قم بإظهار تعليقاته مباشرة
        if (expandedCommentIds.contains(v.id)) {
            populateComments(commentsContainer, v.id)
            commentsContainer.visibility = View.VISIBLE
        }

        // زر النقاط الثلاث
        val dots = TextView(this)
        dots.text = "•••"
        dots.setTextColor(Color.WHITE)
        dots.textSize = 12f
        dots.gravity = Gravity.CENTER
        dots.isFocusable = false
        dots.contentDescription = "خيارات"
        
        val dotsBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(Color.parseColor("#1AFFFFFF"))
        }
        dots.background = dotsBg

        dots.setOnClickListener {
            val pm = PopupMenu(this, dots)
            val cCount = countComments(v.id)
            val isExpanded = expandedCommentIds.contains(v.id)
            
            pm.menu.add(0, 1, 0, "إعادة المشاهدة من البداية")
            if (cCount > 0) {
                pm.menu.add(0, 2, 1, if (isExpanded) "إخفاء التعليقات" else "عرض التعليقات ($cCount)")
            } else {
                pm.menu.add(0, 2, 1, "لا توجد تعليقات").isEnabled = false
            }
            pm.menu.add(0, 3, 2, "حذف الفيديو")
            
            pm.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { prefs.resetProgress(v.id); refresh(); true }
                    2 -> {
                        if (isExpanded) {
                            expandedCommentIds.remove(v.id)
                            commentsContainer.visibility = View.GONE
                        } else {
                            expandedCommentIds.add(v.id)
                            populateComments(commentsContainer, v.id)
                            commentsContainer.visibility = View.VISIBLE
                        }
                        true
                    }
                    3 -> { confirmDelete(v); true }
                    else -> false
                }
            }
            pm.show()
        }

        row.addView(dots, LinearLayout.LayoutParams(dp(32), dp(32)).apply {
            setMargins(dp(8), 0, dp(4), 0)
        })

        card.addView(row); applyFont(box)

        if (animatedIds.add(v.id)) { box.alpha = 0f; box.translationY = dp(24).toFloat(); box.animate().alpha(1f).translationY(0f).setDuration(280).setInterpolator(DecelerateInterpolator()).start() }
        return box
    }

    private fun thumbFrame(v: VideoItem, w: Int, h: Int): FrameLayout {
        val f = FrameLayout(this); f.setBackgroundColor(Color.BLACK); f.setPadding(0, 0, 0, 0); f.layoutParams = ViewGroup.LayoutParams(-1, h)
        val shimmer = ShimmerView(this); f.addView(shimmer, FrameLayout.LayoutParams(-1, -1))
        val iv = ImageView(this); iv.scaleType = ImageView.ScaleType.CENTER_CROP; f.addView(iv, FrameLayout.LayoutParams(-1, -1)); loadThumb(v, iv, shimmer)

        val watched = prefs.watched(v.id)

        if (watched) {
            val dim = View(this)
            dim.setBackgroundColor(Color.parseColor("#99000000"))
            f.addView(dim, FrameLayout.LayoutParams(-1, -1))
        }

        val badge = TextView(this)
        badge.text = fmt(v.duration)
        badge.setTextColor(Color.WHITE)
        badge.textSize = 11f
        badge.typeface = Typeface.DEFAULT_BOLD
        badge.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val bShape = GradientDrawable()
        bShape.setColor(Color.parseColor("#CC000000"))
        bShape.cornerRadius = dp(4).toFloat()
        badge.background = bShape
        badge.setPadding(dp(5), dp(1), dp(5), dp(1))
        f.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.LEFT or Gravity.BOTTOM).apply {
            leftMargin = dp(8)
            bottomMargin = dp(8)
        })

        val pos = prefs.position(v.id); val dur = if (v.duration > 0) v.duration else prefs.duration(v.id)
        if (pos > 0 && dur > 0) {
            val frac = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            val line = LinearLayout(this); line.orientation = LinearLayout.HORIZONTAL; line.layoutDirection = View.LAYOUT_DIRECTION_LTR
            val done = View(this); done.setBackgroundColor(red); val rest = View(this); rest.setBackgroundColor(Color.parseColor("#55FFFFFF"))
            line.addView(done, LinearLayout.LayoutParams(0, -1, frac)); line.addView(rest, LinearLayout.LayoutParams(0, -1, 1f - frac))
            f.addView(line, FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM))
        }

        if (watched) {
            val tg = TextView(this)
            tg.text = "تم المشاهدة"
            tg.setTextColor(Color.WHITE)
            tg.textSize = 14f
            tg.typeface = Typeface.DEFAULT_BOLD
            tg.gravity = Gravity.CENTER
            val tgShape = GradientDrawable()
            tgShape.setColor(Color.parseColor("#B0000000"))
            tgShape.cornerRadius = dp(8).toFloat()
            tg.background = tgShape
            tg.setPadding(dp(14), dp(6), dp(14), dp(6))
            f.addView(tg, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        } else if (pos > 0 && dur > 0 && pos < dur - 3000) {
            val tg = TextView(this)
            tg.text = "-" + fmt(dur - pos)
            tg.setTextColor(Color.WHITE)
            tg.textSize = 11f
            tg.typeface = Typeface.DEFAULT_BOLD
            tg.layoutDirection = View.LAYOUT_DIRECTION_LTR
            val tgShape = GradientDrawable()
            tgShape.setColor(Color.parseColor("#99000000"))
            tgShape.cornerRadius = dp(4).toFloat()
            tg.background = tgShape
            tg.setPadding(dp(5), dp(1), dp(5), dp(1))
            f.addView(tg, FrameLayout.LayoutParams(-2, -2, Gravity.RIGHT or Gravity.TOP).apply {
                rightMargin = dp(8); topMargin = dp(8)
            })
        }
        return f
    }

    private fun loadThumb(v: VideoItem, iv: ImageView, shimmer: View?) {
        iv.tag = v.id; val cached = cache.get(v.id)
        if (cached != null) { iv.alpha = 1f; iv.setImageBitmap(cached); shimmer?.visibility = View.GONE; return }
        iv.setImageDrawable(null); iv.alpha = 0f
        pool.execute {
            var bmp: Bitmap? = readDisk(v); if (bmp == null) { bmp = extractFrame(v); if (bmp != null) writeDisk(v, bmp) }
            val result = bmp
            if (result != null) {
                cache.put(v.id, result)
                ui.post { if (iv.tag == v.id) { iv.setImageBitmap(result); iv.animate().alpha(1f).setDuration(220).withEndAction { shimmer?.visibility = View.GONE }.start() } }
            } else { ui.post { shimmer?.visibility = View.GONE } }
        }
    }

    private fun thumbFile(v: VideoItem) = File(thumbDir, v.id.toString() + "_" + thumbW + ".jpg")
    private fun readDisk(v: VideoItem): Bitmap? = try { val f = thumbFile(v); if (f.exists() && f.length() > 0) BitmapFactory.decodeFile(f.absolutePath) else null } catch (e: Throwable) { null }
    private fun writeDisk(v: VideoItem, bmp: Bitmap) { try { FileOutputStream(thumbFile(v)).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 100, out) } } catch (_: Exception) {} }

    private fun extractFrame(v: VideoItem): Bitmap? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(this, v.uri)

            var frame = r.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: r.getFrameAtTime(-1)

            if (frame != null) {
                val targetW = thumbW
                val targetH = (frame.height.toFloat() / frame.width * targetW).toInt()

                if (frame.width > targetW) {
                    val scaled = Bitmap.createScaledBitmap(frame, targetW, targetH, true)
                    if (scaled != frame) frame.recycle()
                    return scaled
                }
                return frame
            }
        } catch (_: Throwable) {} finally { try { r.release() } catch (_: Exception) {} }

        return try {
            if (Build.VERSION.SDK_INT >= 29) contentResolver.loadThumbnail(v.uri, Size(thumbW, thumbH), null)
            else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(contentResolver, v.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
            }
        } catch (_: Throwable) { null }
    }

    private fun setPlayIcon(playing: Boolean) { playPause.setImageDrawable(if (playing) pauseDrawable else playDrawable) }

    private fun buildPlayer() {
        playerRoot = FrameLayout(this); playerRoot.setBackgroundColor(Color.BLACK); playerRoot.isClickable = true

        aspectLayout = AspectRatioFrameLayout(this)
        aspectLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) {
                player?.setVideoSurfaceView(surfaceView)
                val waiting = pending
                if (waiting != null && player == null) {
                    pending = null
                    startPlayback(waiting)
                }
            }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
            override fun surfaceDestroyed(h: SurfaceHolder) {
                player?.clearVideoSurfaceView(surfaceView)
            }
        })

        aspectLayout.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
        playerRoot.addView(aspectLayout, FrameLayout.LayoutParams(-1, -1))

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean { gestureMode = 0; return true }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onPlayerTap(); return true }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (locked || !prepared) return true
                val left = e.x < playerRoot.width / 2f; seekBy(if (left) -10000 else 10000); showHud(if (left) "-10s" else "+10s"); playerRoot.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); return true
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (locked || e1 == null || playerRoot.height <= 0) return false
                if (gestureMode == 0) { if (Math.abs(dy) < Math.abs(dx)) return false; gestureMode = if (e1.x < playerRoot.width / 2f) 1 else 2; gestureValue = if (gestureMode == 1) currentBrightness() else currentVolume() }
                val delta = dy / playerRoot.height.toFloat() * 1.2f; gestureValue = (gestureValue + delta).coerceIn(0f, 1f)
                if (gestureMode == 1) { setBrightness(gestureValue); showHud("السطوع " + (gestureValue * 100).toInt() + "%") }
                else { setVolume(gestureValue); showHud("الصوت " + (gestureValue * 100).toInt() + "%") }
                return true
            }
        })

        gestureOverlay = View(this)
        gestureOverlay.setOnTouchListener { _, e -> gestures.onTouchEvent(e); true }
        playerRoot.addView(gestureOverlay, FrameLayout.LayoutParams(-1, -1))

        topBar = LinearLayout(this); topBar.orientation = LinearLayout.HORIZONTAL; topBar.gravity = Gravity.CENTER_VERTICAL; topBar.setBackgroundColor(Color.parseColor("#88000000"))
        val back = ImageView(this); back.scaleType = ImageView.ScaleType.FIT_CENTER; back.setImageDrawable(IconDrawable(IconDrawable.BACK, Color.WHITE))
        back.setPadding(dp(11), dp(9), dp(11), dp(13)); back.contentDescription = "رجوع"; back.setOnClickListener { closePlayer() }
        make3D(back, Color.parseColor("#3A3A3A"), Color.parseColor("#1C1C1C"), 14)
        topBar.addView(back, LinearLayout.LayoutParams(dp(46), dp(48)).apply { setMargins(dp(8), dp(6), dp(8), dp(6)) })
        playerTitle = TextView(this); playerTitle.setTextColor(Color.WHITE); playerTitle.textSize = 15f; playerTitle.maxLines = 1; playerTitle.ellipsize = android.text.TextUtils.TruncateAt.END
        topBar.addView(playerTitle, LinearLayout.LayoutParams(0, -2, 1f))
        playerRoot.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        controls = LinearLayout(this); controls.orientation = LinearLayout.VERTICAL; controls.setBackgroundColor(Color.parseColor("#88000000")); controls.setPadding(dp(8), dp(4), dp(8), dp(4))
        val seekRow = LinearLayout(this); seekRow.orientation = LinearLayout.HORIZONTAL; seekRow.gravity = Gravity.CENTER_VERTICAL
        seekBar = SeekBar(this)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(s: SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(s: SeekBar?) { userSeeking = false; player?.seekTo(seekBar.progress.toLong()) }
        })
        seekRow.addView(seekBar, LinearLayout.LayoutParams(0, -2, 1f))
        timeText = TextView(this); timeText.setTextColor(Color.WHITE); timeText.textSize = 12f; timeText.text = "0:00 / 0:00"; timeText.setPadding(dp(6), 0, dp(6), 0)
        seekRow.addView(timeText)

        fsBtn = ImageView(this)
        fsBtn.scaleType = ImageView.ScaleType.FIT_CENTER
        fsBtn.setImageDrawable(IconDrawable(IconDrawable.COMPRESS, Color.WHITE))
        fsBtn.setPadding(dp(8), dp(8), dp(8), dp(8))
        fsBtn.contentDescription = "الخروج من الفيديو"
        fsBtn.setOnClickListener { closePlayer() }
        seekRow.addView(fsBtn, LinearLayout.LayoutParams(dp(42), dp(42)))

        controls.addView(seekRow, LinearLayout.LayoutParams(-1, -2))

        val btnRow = LinearLayout(this); btnRow.orientation = LinearLayout.HORIZONTAL; btnRow.gravity = Gravity.CENTER

        playPause = makePlayerBtn(IconDrawable.PAUSE, Color.parseColor("#FF0000")) {
            try {
                val p = player
                if (p != null && prepared) {
                    if (p.isPlaying) {
                        p.pause(); setPlayIcon(false)
                    } else {
                        if (p.playbackState == Player.STATE_ENDED) {
                            p.seekTo(0L)
                            seekBar.progress = 0
                        }
                        p.play(); setPlayIcon(true)
                    }
                } else {
                    current?.let { startPlayback(it) }
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "فشل التشغيل: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        btnRow.addView(playPause, LinearLayout.LayoutParams(dp(52), dp(52)).apply { setMargins(dp(5), dp(2), dp(5), dp(2)) })

        speedBtn = TextView(this)
        speedBtn.text = "1x"
        speedBtn.setTextColor(Color.WHITE)
        speedBtn.textSize = 14f
        speedBtn.typeface = Typeface.DEFAULT_BOLD
        speedBtn.gravity = Gravity.CENTER
        val speedBg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#33FFFFFF")) }
        speedBtn.background = speedBg
        speedBtn.setOnClickListener { speedIdx = (speedIdx + 1) % speeds.size; applySpeed(); showHud("السرعة " + fmtSpeed(speeds[speedIdx])) }
        speedBtn.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            false
        }
        btnRow.addView(speedBtn, LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(dp(5), dp(2), dp(5), dp(2)) })

        btnRow.addView(makePlayerBtn(IconDrawable.LOCK) { setLocked(true) }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(dp(5), 0, dp(5), 0) })

        // زر التعليقات مع شارة العدد
        btnRow.addView(makeCommentButton(), LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(dp(5), 0, dp(5), 0) })

        if (Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            btnRow.addView(makePlayerBtn(IconDrawable.PIP) { enterPip() }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(dp(5), 0, dp(5), 0) })
        }

        btnRow.addView(makePlayerBtn(IconDrawable.EXPAND) {
            if (aspectLayout.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                aspectLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                showHud("ملء الشاشة (قص)")
            } else {
                aspectLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                showHud("عرض كامل الفيديو")
            }
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(dp(5), 0, dp(5), 0) })

        controls.addView(btnRow, LinearLayout.LayoutParams(-1, -2))
        playerRoot.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        commentsPanel = LinearLayout(this); commentsPanel.orientation = LinearLayout.VERTICAL
        commentsPanel.setBackgroundColor(Color.parseColor("#0F0F0F")); commentsPanel.isClickable = true
        commentsPanel.visibility = View.GONE
        val cHead = LinearLayout(this); cHead.orientation = LinearLayout.HORIZONTAL; cHead.gravity = Gravity.CENTER_VERTICAL
        cHead.setPadding(dp(14), dp(6), dp(6), dp(6))
        commentsTitle = TextView(this); commentsTitle.text = "التعليقات"; commentsTitle.setTextColor(Color.WHITE)
        commentsTitle.textSize = 16f; commentsTitle.typeface = Typeface.DEFAULT_BOLD
        cHead.addView(commentsTitle, LinearLayout.LayoutParams(0, -2, 1f))
        val cClose = TextView(this); cClose.text = "✕"; cClose.setTextColor(Color.WHITE); cClose.textSize = 18f
        cClose.gravity = Gravity.CENTER; cClose.setPadding(dp(14), dp(6), dp(14), dp(6)); cClose.setOnClickListener { hideComments() }
        cHead.addView(cClose)
        commentsPanel.addView(cHead)
        val cScroll = ScrollView(this)
        commentsList = LinearLayout(this); commentsList.orientation = LinearLayout.VERTICAL; commentsList.setPadding(dp(14), 0, dp(14), dp(10))
        cScroll.addView(commentsList)
        commentsPanel.addView(cScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        playerRoot.addView(commentsPanel, FrameLayout.LayoutParams(-1, -1))

        unlockBtn = makePlayerBtn(IconDrawable.UNLOCK, Color.parseColor("#FF0000")) { setLocked(false) }
        unlockBtn.visibility = View.GONE
        playerRoot.addView(unlockBtn, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.LEFT or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(24) })

        hudText = TextView(this); hudText.setTextColor(Color.WHITE); hudText.textSize = 18f; hudText.typeface = Typeface.DEFAULT_BOLD; hudText.gravity = Gravity.CENTER
        hudText.setPadding(dp(20), dp(10), dp(20), dp(10))
        hudText.background = GradientDrawable().apply { setColor(Color.parseColor("#B0000000")); cornerRadius = dp(14).toFloat() }
        hudText.visibility = View.GONE; playerRoot.addView(hudText, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setPlayIcon(true)
    }

    private fun onPlayerTap() {
        if (locked) { showUnlockBtn(); return }
        val show = controls.visibility != View.VISIBLE; controls.visibility = if (show) View.VISIBLE else View.GONE; topBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showHud(text: String) { hudText.text = text; hudText.visibility = View.VISIBLE; ui.removeCallbacks(hideHud); ui.postDelayed(hideHud, 700) }
    private fun showUnlockBtn() { unlockBtn.visibility = View.VISIBLE; ui.removeCallbacks(hideUnlock); ui.postDelayed(hideUnlock, 2500) }

    private fun hideComments() {
        commentsPanel.visibility = View.GONE
        if (!commentsOpen) return
        commentsOpen = false
        aspectLayout.resizeMode = savedResize
        applyCommentsLayout(false)
        requestedOrientation = if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                               else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun applyCommentsLayout(on: Boolean) {
        val dm = resources.displayMetrics
        val pw = minOf(dm.widthPixels, dm.heightPixels)
        val vh = pw * 9 / 16
        aspectLayout.layoutParams = FrameLayout.LayoutParams(-1, if (on) vh else -1, Gravity.TOP)
        gestureOverlay.layoutParams = FrameLayout.LayoutParams(-1, if (on) vh else -1, Gravity.TOP)
        if (on) {
            controls.measure(
                View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val cLp = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
            cLp.topMargin = (vh - controls.measuredHeight).coerceAtLeast(0)
            controls.layoutParams = cLp
            commentsPanel.layoutParams = FrameLayout.LayoutParams(-1, -1).apply { topMargin = vh }
        } else {
            controls.layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        }
    }

    private fun toggleComments() {
        if (commentsPanel.visibility == View.VISIBLE) { hideComments(); return }
        val c = current ?: return
        val f = File(File(filesDir, "comments"), "${c.id}.txt")
        val comments: List<String> = try {
            if (!f.exists()) emptyList()
            else f.readText().substringAfter("\n\n", "").split("\n\n──────────\n\n").filter { it.isNotBlank() }
        } catch (_: Exception) { emptyList() }
        if (comments.isEmpty()) { showHud("لا توجد تعليقات لهذا الفيديو"); return }

        commentsList.removeAllViews()
        commentsTitle.text = "التعليقات (" + comments.size + ")"
        for (cm in comments) {
            val nl = cm.indexOf('\n')
            val author = if (nl > 0) cm.substring(0, nl) else ""
            val body = if (nl > 0) cm.substring(nl + 1) else cm
            val a = TextView(this); a.text = author; a.setTextColor(Color.parseColor("#AAAAAA")); a.textSize = 12f
            a.typeface = Typeface.DEFAULT_BOLD; a.setPadding(0, dp(10), 0, dp(2))
            a.textDirection = View.TEXT_DIRECTION_FIRST_STRONG; a.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
            val b = TextView(this); b.text = body; b.setTextColor(Color.WHITE); b.textSize = 14f
            b.textDirection = View.TEXT_DIRECTION_FIRST_STRONG; b.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
            commentsList.addView(a); commentsList.addView(b)
        }
        applyFont(commentsPanel)
        savedResize = aspectLayout.resizeMode
        aspectLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        commentsOpen = true
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        controls.visibility = View.VISIBLE; topBar.visibility = View.VISIBLE
        applyCommentsLayout(true)
        commentsPanel.visibility = View.VISIBLE
    }

    private fun setLocked(l: Boolean) {
        locked = l; buzz(playerRoot)
        if (l) { controls.visibility = View.GONE; topBar.visibility = View.GONE; showUnlockBtn(); showHud("تم قفل الشاشة") }
        else { ui.removeCallbacks(hideUnlock); unlockBtn.visibility = View.GONE; controls.visibility = View.VISIBLE; topBar.visibility = View.VISIBLE }
    }

    private fun seekBy(delta: Int) {
        val p = player ?: return; if (!prepared) return
        try {
            val dur = p.duration
            if (dur != C.TIME_UNSET && dur > 0) {
                val t = (p.currentPosition + delta).coerceIn(0, dur)
                p.seekTo(t)
                seekBar.progress = t.toInt()
            }
        } catch (_: Exception) {}
    }

    private fun currentBrightness(): Float {
        val b = window.attributes.screenBrightness; if (b >= 0f) return b
        return try { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f } catch (_: Exception) { 0.5f }
    }

    private fun setBrightness(f: Float) { val lp = window.attributes; lp.screenBrightness = f.coerceAtLeast(0.02f); window.attributes = lp }
    private fun currentVolume(): Float { val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC); return if (max > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else 0f }
    private fun setVolume(f: Float) { try { val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC); audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(f * max), 0) } catch (_: Exception) {} }
    private fun fmtSpeed(s: Float): String = (if (s % 1f == 0f) s.toInt().toString() else s.toString()) + "x"

    private fun applySpeed() {
        val s = speeds[speedIdx]; speedBtn.text = fmtSpeed(s)
        val p = player ?: return
        p.setPlaybackSpeed(s)
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < 26) return
        try {
            var w = videoW; var h = videoH; if (w <= 0 || h <= 0) { w = 16; h = 9 }
            val ratio = w.toFloat() / h
            val r = when { ratio > 2.3f -> Rational(23, 10); ratio < 0.45f -> Rational(45, 100); else -> Rational(w, h) }
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(r).build())
        } catch (_: Exception) { Toast.makeText(this@MainActivity, "الوضع المصغّر غير متاح", Toast.LENGTH_SHORT).show() }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= 26 && playerRoot.visibility == View.VISIBLE && prepared && player?.isPlaying == true) { enterPip() }
    }

    @Suppress("DEPRECATION", "NewApi")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (playerRoot.visibility != View.VISIBLE) return
        if (isInPictureInPictureMode) { controls.visibility = View.GONE; topBar.visibility = View.GONE; unlockBtn.visibility = View.GONE; hudText.visibility = View.GONE }
        else if (!locked) { controls.visibility = View.VISIBLE; topBar.visibility = View.VISIBLE }
    }

    override fun onStop() {
        super.onStop()
        val p = player
        val c = current
        if (p != null && c != null && prepared) {
            saveProgress(p, c)
        }
    }

    private fun openPlayer(v: VideoItem) {
        releasePlayer(); current = v; videoW = 0; videoH = 0; locked = false; speedIdx = 2; speedBtn.text = "1x"
        aspectLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        hideComments()
        unlockBtn.visibility = View.GONE; hudText.visibility = View.GONE; playerTitle.text = v.title; setPlayIcon(true); seekBar.progress = 0
        controls.visibility = View.VISIBLE; topBar.visibility = View.VISIBLE; playerRoot.visibility = View.VISIBLE; hideSystemBars(true)

        // شارة عدد التعليقات
        commentsCount = countComments(v.id)
        commentBadge.text = when {
            commentsCount <= 0 -> ""
            commentsCount > 99 -> "99+"
            else -> commentsCount.toString()
        }
        commentBadge.visibility = if (commentsCount > 0) View.VISIBLE else View.GONE

        // إعادة فتح لوحة التعليقات إذا كانت مفتوحة قبل التدوير
        if (pendingRestoreComments) {
            pendingRestoreComments = false
            ui.postDelayed({
                if (playerRoot.visibility == View.VISIBLE && current != null && commentsPanel.visibility != View.VISIBLE) {
                    toggleComments()
                }
            }, 700)
        }

        if (surfaceView.holder.surface.isValid) { pending = null; startPlayback(v) } else { pending = v }
    }

    private fun startPlayback(v: VideoItem) {
        releasePlayer()

        val exoPlayer = ExoPlayer.Builder(this@MainActivity).build()
        player = exoPlayer

        exoPlayer.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoW = videoSize.width
                videoH = videoSize.height
                if (videoW > videoH && !isLandscape) {
                    isLandscape = true
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    prepared = true
                    val dur = exoPlayer.duration
                    if (dur != C.TIME_UNSET && dur > 0) {
                        seekBar.max = dur.toInt()
                    }
                    if (speedIdx != 2) applySpeed()
                } else if (playbackState == Player.STATE_ENDED) {
                    prepared = true
                    val dur = exoPlayer.duration
                    if (dur != C.TIME_UNSET && dur > 0) {
                        prefs.savePosition(v.id, 0L, dur, ended = true)
                        seekBar.max = dur.toInt()
                        seekBar.progress = dur.toInt()
                    }
                    setPlayIcon(false)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                setPlayIcon(isPlaying)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                setPlayIcon(false)
                Toast.makeText(this@MainActivity, "خطأ في الفيديو: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        })

        val saved = prefs.position(v.id)
        val knownDur = if (v.duration > 0) v.duration else prefs.duration(v.id)
        val startPos = if (saved > 0 && (knownDur <= 0 || saved < knownDur - 3000)) saved else 0L

        val mediaItem = MediaItem.fromUri(v.uri)
        exoPlayer.setMediaItem(mediaItem, startPos)
        exoPlayer.setVideoSurfaceView(surfaceView)

        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars(hide: Boolean) {
        window.decorView.systemUiVisibility = if (hide) {
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        } else { View.SYSTEM_UI_FLAG_VISIBLE }
    }

    private fun closePlayer() {
        hideComments()
        val p = player; val c = current
        if (p != null && c != null && prepared) {
            saveProgress(p, c)
        }
        releasePlayer(); current = null; pending = null; videoW = 0; videoH = 0; locked = false; speedIdx = 2
        ui.removeCallbacks(hideHud); ui.removeCallbacks(hideUnlock); hudText.visibility = View.GONE; unlockBtn.visibility = View.GONE
        val lp = window.attributes; lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; window.attributes = lp
        playerRoot.visibility = View.GONE; isLandscape = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; hideSystemBars(false); refresh()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        prepared = false
    }

    override fun onDestroy() {
        ui.removeCallbacks(ticker); ui.removeCallbacks(hideHud); ui.removeCallbacks(hideUnlock)
        if (observerRegistered) {
            try { contentResolver.unregisterContentObserver(mediaObserver) } catch (_: Exception) {}
            observerRegistered = false
        }
        releasePlayer(); ytTab.destroy(); pool.shutdown(); super.onDestroy()
    }

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun fmt(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    class Prefs(ctx: Context) {
        private val sp: SharedPreferences = ctx.getSharedPreferences("offlinetube", Context.MODE_PRIVATE)

        var folderPath: String
            get() = sp.getString("folder_path", "") ?: ""
            set(v) { sp.edit().putString("folder_path", v).apply() }

        var lastTab: Int
            get() = sp.getInt("last_tab", 0)
            set(v) { sp.edit().putInt("last_tab", v).apply() }

        fun position(id: Long): Long = sp.getLong("p_$id", 0L)
        fun duration(id: Long): Long = sp.getLong("d_$id", 0L)
        fun watched(id: Long): Boolean = sp.getBoolean("w_$id", false)
        fun lastTouched(id: Long): Long = sp.getLong("t_$id", 0L)

        fun savePosition(id: Long, position: Long, duration: Long, ended: Boolean = false) {
            if (duration <= 0) return
            val e = sp.edit().putLong("p_$id", position).putLong("d_$id", duration)
            if (ended) {
                e.putBoolean("w_$id", true).putLong("t_$id", System.currentTimeMillis())
            } else if (position > 3000) {
                e.putBoolean("w_$id", false).putLong("t_$id", System.currentTimeMillis())
            }
            e.apply()
        }

        fun markWatched(id: Long) = sp.edit().putBoolean("w_$id", true).putLong("t_$id", System.currentTimeMillis()).apply()
        fun resetProgress(id: Long) = sp.edit().remove("p_$id").remove("w_$id").apply()
        fun clearVideo(id: Long) = sp.edit().remove("p_$id").remove("d_$id").remove("w_$id").remove("t_$id").apply()
    }
}

/** Downloader مطلوب من NewPipeExtractor */
class YtDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val conn = URL(request.url()).openConnection() as HttpURLConnection
        conn.requestMethod = request.httpMethod()
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        )
        for ((k, vals) in request.headers()) {
            conn.setRequestProperty(k, vals.joinToString(", "))
        }
        val data = request.dataToSend()
        if (data != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(data) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..399) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        val headers = HashMap<String, List<String>>()
        for ((k, v) in conn.headerFields) { if (k != null) headers[k] = v }
        return Response(code, conn.responseMessage, headers, body, conn.url.toString())
    }
}

/**
 * تبويب YouTube: يلصق المستخدم الرابط → ينزّل الفيديو + أول 50 تعليق.
 * الفيديو يُحفظ في المجلد المختار، والتعليقات في filesDir/comments/<mediaId>.txt
 */
class YoutubeTab(
    private val act: AppCompatActivity,
    private val folder: () -> String,
    private val onDone: () -> Unit,
    private val onOpen: (VideoItem) -> Unit
) {
    companion object {
        const val MAX_COMMENTS = 50
        private var inited = false
        private const val HOME = "https://m.youtube.com/"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        private const val CHUNK = 4L * 1024 * 1024
        private const val BTN_TEXT = "⬇ تنزيل الفيديو + 50 تعليق"
    }

    private class Option(val label: String, val video: VideoStream, val audio: AudioStream?, val height: Int)
    private class Cancelled : Exception()

    val view = FrameLayout(act)
    private val web = WebView(act)
    private val btn = TextView(act)
    private val btnBg = GradientDrawable()
    private val status = TextView(act)
    private val bar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal)
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var busy = false
    @Volatile private var cancelled = false
    private var loadedOnce = false
    private var currentVideoUrl: String? = null
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    private fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()

    init {
        if (!inited) { NewPipe.init(YtDownloader()); inited = true }

        view.setBackgroundColor(Color.parseColor("#0F0F0F"))
        view.layoutDirection = View.LAYOUT_DIRECTION_LTR
        view.isClickable = true

        val st = web.settings
        st.javaScriptEnabled = true
        st.domStorageEnabled = true
        web.setBackgroundColor(Color.parseColor("#0F0F0F"))
        CookieManager.getInstance().setAcceptCookie(true)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String?) { updateButton(url) }
            override fun doUpdateVisitedHistory(v: WebView, url: String?, isReload: Boolean) { updateButton(url) }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(v: View, cb: WebChromeClient.CustomViewCallback) {
                if (customView != null) { cb.onCustomViewHidden(); return }
                v.setBackgroundColor(Color.BLACK)
                customView = v; customCb = cb
                (act.window.decorView as FrameLayout).addView(v, FrameLayout.LayoutParams(-1, -1))
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                setImmersive(true)
            }
            override fun onHideCustomView() { hideCustom() }
        }
        view.addView(web, FrameLayout.LayoutParams(-1, -1))

        bar.max = 100; bar.visibility = View.GONE
        view.addView(bar, FrameLayout.LayoutParams(-1, dp(4), Gravity.TOP))

        status.setTextColor(Color.WHITE); status.textSize = 13f; status.gravity = Gravity.CENTER
        status.setPadding(dp(14), dp(8), dp(14), dp(8))
        status.background = GradientDrawable().apply { setColor(Color.parseColor("#CC000000")); cornerRadius = dp(12).toFloat() }
        status.visibility = View.GONE
        view.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(76); leftMargin = dp(16); rightMargin = dp(16)
        })

        btn.text = BTN_TEXT
        btn.setTextColor(Color.WHITE); btn.textSize = 15f; btn.gravity = Gravity.CENTER
        btn.typeface = Typeface.DEFAULT_BOLD
        btn.setPadding(dp(20), dp(12), dp(20), dp(12))
        btnBg.setColor(Color.parseColor("#FF0000")); btnBg.cornerRadius = dp(26).toFloat()
        btn.background = btnBg
        btn.elevation = dp(8).toFloat()
        btn.visibility = View.GONE
        btn.setOnClickListener { onButton() }
        view.addView(btn, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(16) })
    }

    fun onShown() {
        if (!loadedOnce) { loadedOnce = true; web.loadUrl(HOME) } else web.onResume()
    }

    fun onHidden() {
        web.evaluateJavascript("document.querySelectorAll('video').forEach(function(v){v.pause()})", null)
        hideCustom()
        web.onPause()
    }

    fun handleBack(): Boolean {
        if (customView != null) { hideCustom(); return true }
        if (web.canGoBack()) { web.goBack(); return true }
        return false
    }

    fun destroy() {
        cancelled = true
        hideCustom()
        try { web.destroy() } catch (_: Exception) {}
        io.shutdown()
    }

    @Suppress("DEPRECATION")
    private fun setImmersive(on: Boolean) {
        act.window.decorView.systemUiVisibility = if (on) {
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        } else View.SYSTEM_UI_FLAG_VISIBLE
        if (on) act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hideCustom() {
        val v = customView ?: return
        (act.window.decorView as FrameLayout).removeView(v)
        customView = null
        customCb?.onCustomViewHidden(); customCb = null
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        setImmersive(false)
    }

    private fun videoUrlOf(u: String?): String? {
        if (u == null || !(u.contains("youtube.com") || u.contains("youtu.be"))) return null
        val id = Regex("[?&]v=([\\w-]{11})").find(u)?.groupValues?.get(1)
            ?: Regex("/shorts/([\\w-]{11})").find(u)?.groupValues?.get(1)
            ?: Regex("youtu\\.be/([\\w-]{11})").find(u)?.groupValues?.get(1)
        return id?.let { "https://www.youtube.com/watch?v=$it" }
    }

    private fun updateButton(url: String?) {
        currentVideoUrl = videoUrlOf(url)
        btn.visibility = if (busy || currentVideoUrl != null) View.VISIBLE else View.GONE
    }

    private fun setStatus(s: String, autoHide: Boolean = false) {
        ui.post {
            status.setOnClickListener(null); status.isClickable = false
            status.text = s; status.visibility = View.VISIBLE
            if (autoHide) ui.postDelayed({ status.visibility = View.GONE }, 4000)
        }
    }

    private fun setBusyUi(on: Boolean) {
        busy = on
        btn.text = if (on) "✕ إلغاء التنزيل" else BTN_TEXT
        btnBg.setColor(Color.parseColor(if (on) "#555555" else "#FF0000"))
        btn.visibility = if (on || currentVideoUrl != null) View.VISIBLE else View.GONE
        bar.visibility = if (on) View.VISIBLE else View.GONE
        if (on) bar.progress = 0
        view.keepScreenOn = on
    }

    private fun finishUi() { ui.post { setBusyUi(false) } }

    private fun fail(e: Throwable) {
        if (e is Cancelled) setStatus("تم إلغاء التنزيل", true)
        else setStatus("✗ فشل: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun onButton() {
        if (busy) { cancelled = true; setStatus("جارٍ الإلغاء..."); return }
        val url = currentVideoUrl
        if (url == null) { Toast.makeText(act, "افتح فيديو أولاً", Toast.LENGTH_SHORT).show(); return }
        if (folder().isEmpty()) {
            Toast.makeText(act, "اختر مجلد الفيديوهات أولاً (زر المجلد)", Toast.LENGTH_LONG).show(); return
        }
        cancelled = false
        setBusyUi(true)
        web.evaluateJavascript("document.querySelectorAll('video').forEach(function(v){v.pause()})", null)
        io.execute {
            var handedOff = false
            try {
                setStatus("جارٍ جلب معلومات الفيديو...")
                val info = StreamInfo.getInfo(ServiceList.YouTube, url)
                val options = buildOptions(info)
                if (options.isEmpty()) throw IllegalStateException("لا توجد صيغة قابلة للتنزيل لهذا الفيديو")
                handedOff = true
                if (options.size == 1) runDownload(info, url, options[0])
                else ui.post { askQuality(info, url, options) }
            } catch (e: Exception) {
                fail(e)
            } finally {
                if (!handedOff) finishUi()
            }
        }
    }

    private fun heightOf(res: String?): Int = Regex("\\d+").find(res ?: "")?.value?.toIntOrNull() ?: 0

    private fun buildOptions(info: StreamInfo): List<Option> {
        val map = LinkedHashMap<Int, Option>()
        for (v in info.videoStreams.filter { it.isUrl }) {
            val h = heightOf(v.resolution)
            if (!map.containsKey(h)) map[h] = Option(h.toString() + "p", v, null, h)
        }
        val audio = info.audioStreams
            .filter { it.isUrl && it.format == NpFormat.M4A }
            .maxByOrNull { it.averageBitrate }
        if (audio != null) {
            for (v in info.videoOnlyStreams.filter { it.isUrl && it.format == NpFormat.MPEG_4 }) {
                val h = heightOf(v.resolution)
                if (h > 0 && !map.containsKey(h)) map[h] = Option(h.toString() + "p", v, audio, h)
            }
        }
        return map.values.sortedByDescending { it.height }
    }

    private fun askQuality(info: StreamInfo, url: String, options: List<Option>) {
        val labels = options.map { it.label }.toTypedArray()
        AlertDialog.Builder(act)
            .setTitle("اختر الجودة")
            .setItems(labels) { _, i -> io.execute { runDownload(info, url, options[i]) } }
            .setOnCancelListener { fail(Cancelled()); finishUi() }
            .show()
    }

    private fun runDownload(info: StreamInfo, url: String, opt: Option) {
        val title = info.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)
        val tmpDir = File(act.cacheDir, "yt_dl").apply { mkdirs() }
        val vTmp = File(tmpDir, "v.tmp"); val aTmp = File(tmpDir, "a.tmp"); val outTmp = File(tmpDir, "out.mp4")
        try {
            val finalFile: File
            if (opt.audio == null) {
                fetch(opt.video.content, vTmp, "تنزيل الفيديو (" + opt.label + ")", 0, 80)
                finalFile = vTmp
            } else {
                fetch(opt.video.content, vTmp, "تنزيل الصورة (" + opt.label + ")", 0, 70)
                fetch(opt.audio.content, aTmp, "تنزيل الصوت", 70, 80)
                setStatus("جارٍ دمج الصوت والصورة...")
                mux(vTmp, aTmp, outTmp)
                finalFile = outTmp
            }
            ui.post { bar.progress = 85 }
            setStatus("جارٍ حفظ الفيديو في الهاتف...")
            val saved = saveToStore(title, finalFile)

            // --- جلب وتنزيل التعليقات ---
            setStatus("جارٍ جلب التعليقات (50)...")
            ui.post { bar.progress = 90 }
            
            var commentsCount = 0
            val savedCommentsOk = try {
                val comments = fetchComments(url) // جلب 50 تعليق
                commentsCount = comments.size
                saveComments(saved.first, title, comments) // حفظها في الهاتف
            } catch (e: Exception) {
                false
            }

            val item = VideoItem(saved.first, title, info.duration * 1000L, saved.second)
            
            ui.post {
                bar.progress = 100
                status.text = if (savedCommentsOk && commentsCount > 0)
                    "✓ تم تنزيل الفيديو و $commentsCount تعليق — اضغط للتشغيل ▶"
                else
                    "✓ تم تنزيل الفيديو — اضغط للتشغيل ▶ (تعذّر جلب التعليقات)"
                status.visibility = View.VISIBLE
                status.setOnClickListener {
                    status.visibility = View.GONE; status.setOnClickListener(null); status.isClickable = false
                    onOpen(item)
                }
                onDone()
            }
        } catch (e: Exception) {
            fail(e)
        } finally {
            vTmp.delete(); aTmp.delete(); outTmp.delete()
            finishUi()
        }
    }

    private fun report(label: String, pos: Long, total: Long, from: Int, to: Int) {
        val frac = if (total > 0) pos.toDouble() / total else 0.0
        val pct = from + ((to - from) * frac).toInt()
        ui.post { bar.progress = pct }
        val mb = 1024.0 * 1024.0
        setStatus(String.format(Locale.US, "%s %d%% — %.1f / %.1f MB", label, (frac * 100).toInt(), pos / mb, maxOf(total, 0L) / mb))
    }

    private fun fetch(link: String, dest: File, label: String, from: Int, to: Int) {
        var pos = 0L
        var total = -1L
        var lastUi = 0L
        FileOutputStream(dest).use { out ->
            val buf = ByteArray(64 * 1024)
            while (total < 0 || pos < total) {
                var attempt = 0
                while (true) {
                    if (cancelled) throw Cancelled()
                    val before = pos
                    try {
                        val conn = URL(link).openConnection() as HttpURLConnection
                        conn.setRequestProperty("User-Agent", UA)
                        conn.setRequestProperty("Range", "bytes=$pos-${pos + CHUNK - 1}")
                        conn.connectTimeout = 20000; conn.readTimeout = 20000
                        val code = conn.responseCode
                        if (code != 206 && !(code == 200 && pos == 0L)) throw IOException("HTTP $code")
                        val whole = code == 200
                        total = if (whole) conn.contentLengthLong
                        else conn.getHeaderField("Content-Range")?.substringAfter('/')?.trim()?.toLongOrNull()
                            ?: throw IOException("Content-Range")
                        conn.inputStream.use { inp ->
                            while (true) {
                                if (cancelled) throw Cancelled()
                                val n = inp.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n); pos += n
                                val now = System.currentTimeMillis()
                                if (now - lastUi > 400) { lastUi = now; report(label, pos, total, from, to) }
                            }
                        }
                        if (whole) total = pos
                        if (pos == before) throw IOException("empty")
                        break
                    } catch (e: IOException) {
                        if (++attempt > 3) throw e
                        Thread.sleep(800)
                    }
                }
            }
        }
        report(label, pos, total, from, to)
    }

    private fun mux(videoF: File, audioF: File, out: File) {
        val vEx = MediaExtractor(); val aEx = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            vEx.setDataSource(videoF.absolutePath); aEx.setDataSource(audioF.absolutePath)
            fun pick(ex: MediaExtractor, prefix: String): Int {
                for (i in 0 until ex.trackCount) {
                    if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true) return i
                }
                throw IllegalStateException("لا يوجد مسار $prefix")
            }
            val vt = pick(vEx, "video/"); val at = pick(aEx, "audio/")
            vEx.selectTrack(vt); aEx.selectTrack(at)
            val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val vo = m.addTrack(vEx.getTrackFormat(vt)); val ao = m.addTrack(aEx.getTrackFormat(at))
            m.start()
            val buf = java.nio.ByteBuffer.allocate(4 * 1024 * 1024)
            val bi = MediaCodec.BufferInfo()
            while (true) {
                if (cancelled) throw Cancelled()
                val vTime = vEx.sampleTime; val aTime = aEx.sampleTime
                if (vTime < 0 && aTime < 0) break
                val useV = aTime < 0 || (vTime in 0..aTime)
                val ex = if (useV) vEx else aEx
                val tr = if (useV) vo else ao
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) { ex.advance(); continue }
                bi.set(0, size, ex.sampleTime, ex.sampleFlags)
                m.writeSampleData(tr, buf, bi)
                ex.advance()
            }
            m.stop()
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            try { vEx.release() } catch (_: Exception) {}
            try { aEx.release() } catch (_: Exception) {}
        }
    }

    /** ينسخ الملف النهائي إلى المجلد المختار ويرجع (معرّف MediaStore، الرابط) */
    private fun saveToStore(title: String, src: File): Pair<Long, Uri> {
        val resolver = act.contentResolver
        val folderPath = folder()
        val base = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')

        if (Build.VERSION.SDK_INT >= 29) {
            fun insert(r: String): Uri? {
                val cv = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "$title.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, r)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                return resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)
            }
            val rel = if (folderPath.startsWith(base))
                folderPath.removePrefix(base).trim('/') + "/" else "Movies/OfflineTube/"
            var uri: Uri? = try { insert(rel) } catch (e: Exception) { null }
            if (uri == null) uri = insert("Movies/OfflineTube/")
            val target = uri ?: throw IllegalStateException("تعذّر إنشاء ملف الحفظ")
            try {
                val o = resolver.openOutputStream(target) ?: throw IllegalStateException("تعذّر فتح ملف الحفظ")
                o.use { os -> src.inputStream().use { it.copyTo(os, 256 * 1024) } }
            } catch (e: Exception) {
                resolver.delete(target, null, null); throw e
            }
            val cv = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(target, cv, null, null)
            return Pair(ContentUris.parseId(target), target)
        }

        var dest = File(folderPath, "$title.mp4")
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest = File(folderPath, title + "_" + System.currentTimeMillis() + ".mp4")
        src.inputStream().use { i -> FileOutputStream(dest).use { o -> i.copyTo(o, 256 * 1024) } }
        val latch = CountDownLatch(1)
        var scanned: Uri? = null
        MediaScannerConnection.scanFile(act, arrayOf(dest.absolutePath), null) { _, u -> scanned = u; latch.countDown() }
        latch.await(5, TimeUnit.SECONDS)
        val u = scanned
        val id = if (u != null) { try { ContentUris.parseId(u) } catch (_: Exception) { 0L } } else 0L
        val realId = if (id > 0) id else (dest.absolutePath.hashCode().toLong() and 0x7FFFFFFF)
        return Pair(realId, u ?: Uri.fromFile(dest))
    }

    // ---------------- التعليقات ----------------
    private fun fetchComments(url: String): List<String> {
        val out = ArrayList<String>()
        
        fun add(items: List<CommentsInfoItem>) {
            for (c in items) {
                if (out.size >= MAX_COMMENTS) return
                val text = c.commentText?.content?.trim().orEmpty()
                if (text.isEmpty()) continue
                val author = c.uploaderName?.ifBlank { "مجهول" } ?: "مجهول"
                out.add("$author (👍 ${c.likeCount}):\n$text")
            }
        }

        try {
            // جلب الصفحة الأولى من التعليقات
            val ci = CommentsInfo.getInfo(url)
            add(ci.relatedItems)
            
            // جلب المزيد من الصفحات حتى نصل إلى 50 تعليق
            var page = ci.nextPage
            while (out.size < MAX_COMMENTS && page != null) {
                val more = CommentsInfo.getMoreItems(ServiceList.YouTube, url, page)
                add(more.items)
                page = more.nextPage
            }
        } catch (e: Exception) {
            e.printStackTrace() // لطباعة الخطأ في اللوج إن حدث
        }
        
        return out
    }

    private fun saveComments(mediaId: Long, title: String, comments: List<String>): Boolean = try {
        val dir = File(act.filesDir, "comments").apply { mkdirs() }
        File(dir, "$mediaId.txt").writeText(
            title + "\n\n" + comments.joinToString("\n\n──────────\n\n")
        )
        true
    } catch (_: Exception) { false }
}
