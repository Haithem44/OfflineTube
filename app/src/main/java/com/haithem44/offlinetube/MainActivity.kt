package com.haithem44.offlinetube

import android.Manifest
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.app.RecoverableSecurityException
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
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors

data class VideoItem(val id: Long, val title: String, val duration: Long, val uri: Uri)

class IconDrawable(private val kind: Int, initial: Int) : Drawable() {
    companion object {
        const val HOME = 0; const val SEARCH = 1; const val PLAY = 2; const val REFRESH = 3
        const val FOLDER = 4; const val TRASH = 5; const val BACK = 6; const val LOCK = 7
        const val UNLOCK = 8; const val PAUSE = 9; const val PLAY_FILLED = 10; const val EXPAND = 11
        const val ROTATE = 12; const val PIP = 13
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
                p.moveTo(4f, 7f); p.lineTo(20f, 7f)
                p.moveTo(10f, 7f); p.lineTo(10f, 4f); p.lineTo(14f, 4f); p.lineTo(14f, 7f)
                p.moveTo(6.5f, 7f); p.lineTo(7.5f, 20f); p.lineTo(16.5f, 20f); p.lineTo(17.5f, 7f)
                p.moveTo(10f, 11f); p.lineTo(10f, 16f); p.moveTo(14f, 11f); p.lineTo(14f, 16f)
            }
            BACK -> { p.moveTo(20f, 12f); p.lineTo(4f, 12f); p.moveTo(10f, 6f); p.lineTo(4f, 12f); p.lineTo(10f, 18f) }
            LOCK -> { canvas.drawRoundRect(RectF(5f, 11f, 19f, 21f), 2f, 2f, paint); p.moveTo(8f, 11f); p.lineTo(8f, 8f); p.arcTo(RectF(8f, 4f, 16f, 12f), 180f, 180f); p.lineTo(16f, 11f) }
            UNLOCK -> { canvas.drawRoundRect(RectF(5f, 11f, 19f, 21f), 2f, 2f, paint); p.moveTo(8f, 11f); p.lineTo(8f, 8f); p.arcTo(RectF(8f, 4f, 16f, 12f), 180f, 180f) }
            PAUSE -> { paint.style = Paint.Style.FILL; canvas.drawRoundRect(RectF(6f, 4f, 10.5f, 20f), 1.5f, 1.5f, paint); canvas.drawRoundRect(RectF(13.5f, 4f, 18f, 20f), 1.5f, 1.5f, paint); paint.style = Paint.Style.STROKE }
            PLAY_FILLED -> { paint.style = Paint.Style.FILL; p.moveTo(7f, 4.5f); p.lineTo(19f, 12f); p.lineTo(7f, 19.5f); p.close(); canvas.drawPath(p, paint); p.reset(); paint.style = Paint.Style.STROKE }
            EXPAND -> { p.moveTo(4f, 9f); p.lineTo(4f, 4f); p.lineTo(9f, 4f); p.moveTo(15f, 4f); p.lineTo(20f, 4f); p.lineTo(20f, 9f); p.moveTo(20f, 15f); p.lineTo(20f, 20f); p.lineTo(15f, 20f); p.moveTo(9f, 20f); p.lineTo(4f, 20f); p.lineTo(4f, 15f) }
            ROTATE -> { canvas.drawRoundRect(RectF(3f, 6f, 21f, 18f), 2.5f, 2.5f, paint); p.moveTo(18f, 10f); p.lineTo(18f, 14f) }
            PIP -> { canvas.drawRoundRect(RectF(3f, 5f, 21f, 19f), 2f, 2f, paint); paint.style = Paint.Style.FILL; canvas.drawRoundRect(RectF(12f, 11f, 19f, 17f), 1f, 1f, paint); paint.style = Paint.Style.STROKE }
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

    private lateinit var prefs: Prefs
    private val bg = Color.parseColor("#0F0F0F")
    private val red = Color.parseColor("#FF0000")
    private val gray = Color.parseColor("#AAAAAA")
    private val navActive = Color.parseColor("#FF4D4D")
    private val navInactive = Color.parseColor("#E0E0E0")

    private val all = ArrayList<VideoItem>()
    private val shown = ArrayList<VideoItem>()

    private lateinit var listView: ListView
    private lateinit var adapter: VideoAdapter
    private lateinit var continueHeader: LinearLayout
    private lateinit var logo: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var emptyText: TextView
    private var hasBanner = false
    private var tab = 0
    private var query = ""

    private lateinit var refreshBar: TextView
    private var refreshing = false
    private var touchStartY = 0f
    private var pulling = false

    private var folderPath: String = ""
    private var pendingDelete: VideoItem? = null
    private var deleteNeedsRetry = false
    private var pendingOpenId: Long = -1L

    private val tabNames = arrayOf("الصفحة الرئيسية", "تابع المشاهدة", "الأطول", "الأقصر", "Shorts")
    private val tabTexts = ArrayList<TextView>()
    private val tabLines = ArrayList<View>()

    private class NavRef(val tabIdx: Int, val icon: IconDrawable, val label: TextView)
    private val navRefs = ArrayList<NavRef>()
    private val animatedIds = HashSet<Long>()

    private val avatarColors = intArrayOf(Color.parseColor("#E53935"), Color.parseColor("#8E24AA"), Color.parseColor("#3949AB"), Color.parseColor("#00897B"), Color.parseColor("#F4511E"), Color.parseColor("#6D4C41"))

    private val pool = Executors.newFixedThreadPool(3)
    private val ui = Handler(Looper.getMainLooper())

    private val mediaObserver = object : ContentObserver(ui) {
        override fun onChange(selfChange: Boolean) {
            if (playerRoot.visibility != View.VISIBLE && folderPath.isNotEmpty()) {
                loadVideos()
            }
        }
    }

    private val cache = object : LruCache<Long, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 6).toInt()) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount / 1024
        override fun entryRemoved(evicted: Boolean, key: Long, old: Bitmap, new: Bitmap?) { if (evicted) old.recycle() }
    }

    private val thumbW: Int by lazy { maxOf(resources.displayMetrics.widthPixels, 1080).coerceAtMost(2160) }
    private val thumbH: Int by lazy { thumbW * 9 / 16 }
    private val thumbDir: File by lazy { File(cacheDir, "thumbs_hq").apply { mkdirs() } }

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
    private lateinit var surfaceView: SurfaceView
    private lateinit var controls: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var playerTitle: TextView
    private lateinit var playPause: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var timeText: TextView
    private lateinit var speedBtn: TextView
    private lateinit var unlockBtn: ImageView
    private lateinit var hudText: TextView
    private lateinit var gestures: GestureDetector
    private val pauseDrawable = IconDrawable(IconDrawable.PAUSE, Color.WHITE)
    private val playDrawable = IconDrawable(IconDrawable.PLAY_FILLED, Color.WHITE)
    private var mp: MediaPlayer? = null
    private var prepared = false
    private var current: VideoItem? = null
    private var pending: VideoItem? = null
    private var videoW = 0
    private var videoH = 0
    private var isLandscape = false
    private var fillMode = false
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

    private val ticker = object : Runnable {
        override fun run() {
            val p = mp
            if (p != null && prepared) {
                try {
                    val pos = p.currentPosition
                    if (!userSeeking) { seekBar.max = p.duration; seekBar.progress = pos }
                    timeText.text = fmt(pos.toLong()) + " / " + fmt(p.duration.toLong())
                    val now = System.currentTimeMillis()
                    if (now - lastSaveTime > 5000) {
                        current?.let { prefs.savePosition(it.id, pos.toLong(), p.duration.toLong()) }
                        lastSaveTime = now
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (playerRoot.visibility == View.VISIBLE) {
                    if (locked) showUnlockBtn() else closePlayer()
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
        emptyText.text = "لا توجد فيديوهات"
        emptyText.setTextColor(gray)
        emptyText.textSize = 16f
        emptyText.gravity = Gravity.CENTER
        emptyText.setPadding(dp(24), dp(24), dp(24), dp(24))

        val listFrame = FrameLayout(this)
        listFrame.addView(listView, FrameLayout.LayoutParams(-1, -1))
        listFrame.addView(emptyText, FrameLayout.LayoutParams(-1, -1))
        listView.emptyView = emptyText

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
        current?.let { c ->
            outState.putLong("current_id", c.id)
            val p = mp
            if (p != null && prepared) { try { prefs.savePosition(c.id, p.currentPosition.toLong(), p.duration.toLong()) } catch (_: Exception) {} }
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
        sName.text = "OfflineTube"; sName.setTextColor(Color.WHITE); sName.textSize = 28f; sName.typeface = Typeface.DEFAULT_BOLD; sName.gravity = Gravity.CENTER
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

    private fun iconBtn(kind: Int, top: Int = Color.parseColor("#3A3A3A"), bottom: Int = Color.parseColor("#1C1C1C"), onClick: () -> Unit): ImageView {
        val b = ImageView(this)
        b.scaleType = ImageView.ScaleType.FIT_CENTER; b.setImageDrawable(IconDrawable(kind, Color.WHITE))
        b.setPadding(dp(11), dp(9), dp(11), dp(13)); b.setOnClickListener { onClick() }
        make3D(b, top, bottom, 24); return b
    }

    private fun btnLp() = LinearLayout.LayoutParams(dp(46), dp(48)).apply { setMargins(dp(5), dp(2), dp(5), dp(2)) }

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
        name.text = "OfflineTube"; name.setTextColor(Color.WHITE); name.textSize = 21f; name.typeface = Typeface.DEFAULT_BOLD; name.setPadding(dp(6), 0, 0, 0)
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
        tab = i; prefs.lastTab = i; updateTabStyles(); refresh(); listView.setSelection(0)
    }

    private fun navLp() = LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) }

    private fun buildBottomNav(): View {
        val bar = LinearLayout(this); bar.orientation = LinearLayout.VERTICAL; bar.setBackgroundColor(bg)
        val divider = View(this); divider.setBackgroundColor(Color.parseColor("#2A2A2A")); bar.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        row.addView(navItem(IconDrawable.HOME, "الرئيسية", 0) { selectTab(0) }, navLp())
        row.addView(navItem(IconDrawable.SEARCH, "بحث", -1) { openSearch() }, navLp())
        row.addView(navItem(IconDrawable.PLAY, "تابع", 1) { selectTab(1) }, navLp())
        row.addView(navItem(IconDrawable.REFRESH, "تحديث", -1) { reloadVideos() }, navLp())
        row.addView(navItem(IconDrawable.FOLDER, "المجلد", -1) { pickFolder() }, navLp())
        bar.addView(row, LinearLayout.LayoutParams(-1, 0, 1f)); updateNavStyles(); return bar
    }

    private fun navItem(kind: Int, label: String, tabIdx: Int, onClick: () -> Unit): View {
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.gravity = Gravity.CENTER
        val icon = IconDrawable(kind, navInactive); val iv = ImageView(this); iv.setImageDrawable(icon)
        val l = TextView(this); l.text = label; l.textSize = 11f; l.setTextColor(navInactive); l.gravity = Gravity.CENTER
        col.addView(iv, LinearLayout.LayoutParams(dp(24), dp(24)))
        col.addView(l, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        col.setPadding(dp(4), dp(4), dp(4), dp(6)); col.setOnClickListener { onClick() }
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
        val s = query.trim().lowercase()
        var list: List<VideoItem> = all.filter { s.isEmpty() || it.title.lowercase().contains(s) }
        list = when (tab) {
            1 -> list.filter { prefs.position(it.id) > 0L }
            2 -> list.sortedByDescending { it.duration }
            3 -> list.sortedBy { it.duration }
            4 -> list.filter { it.duration in 1L..60000L }
            else -> list
        }
        shown.clear(); shown.addAll(list); adapter.notifyDataSetChanged(); rebuildContinue(); updateEmptyState()
    }

    private fun updateEmptyState() {
        emptyText.text = when {
            folderPath.isEmpty() -> "لم يتم اختيار مجلد\nاضغط على \"المجلد\" في الأسفل"
            query.isNotEmpty() -> "لا توجد نتائج لـ \"$query\""
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
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) { startLoad(); registerMediaObserver() }
        else { requestPermissions(arrayOf(perm), 1) }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) { startLoad(); registerMediaObserver() }
    }

    private fun registerMediaObserver() {
        try { contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver) } catch (_: Exception) {}
    }

    private fun startLoad() { if (folderPath.isEmpty()) pickFolder() else loadVideos() }

    private fun loadVideos() {
        val folder = folderPath; if (folder.isEmpty()) { finishRefresh(); return }
        pool.execute {
            val list = ArrayList<VideoItem>()
            val proj = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION)
            val esc = folder.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            val sel = MediaStore.Video.Media.DATA + " LIKE ? ESCAPE '\\'"
            val args = arrayOf("$esc/%")
            try {
                contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, sel, args, MediaStore.Video.Media.DATE_ADDED + " DESC")?.use { c ->
                    val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID); val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME); val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    while (c.moveToNext()) {
                        val id = c.getLong(iId); val name = (c.getString(iName) ?: "video").substringBeforeLast('.')
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
        if (refreshing) return; if (folderPath.isEmpty()) { pickFolder(); return }
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
        prefs.clearVideo(v.id); refresh(); Toast.makeText(this@MainActivity, "تم حذف الفيديو", Toast.LENGTH_SHORT).show()
    }

    inner class VideoAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = shown[p].id
        override fun getViewTypeCount() = 1
        override fun getItemViewType(p: Int)
