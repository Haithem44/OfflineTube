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
        override fun getItemViewType(p: Int) = 0
        override fun getView(p: Int, convertView: View?, parent: ViewGroup?): View = videoItemView(shown[p])
    }

    private fun screenW() = resources.displayMetrics.widthPixels

    private fun videoItemView(v: VideoItem): View {
        val w = screenW() - dp(24); val th = w * 9 / 16
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(dp(12), dp(4), dp(12), dp(4))
        val card = LinearLayout(this); card.orientation = LinearLayout.VERTICAL; card.setPadding(0, 0, 0, dp(10))
        setBgKeepPad(card, raised(Color.parseColor("#2B2B2B"), Color.parseColor("#181818"), 18))
        card.elevation = dp(3).toFloat(); card.clipToOutline = true
        card.outlineProvider = object : ViewOutlineProvider() { override fun getOutline(view: View, o: Outline) { o.setRoundRect(0, 0, view.width, view.height, dp(18).toFloat()) } }
        box.addView(card, LinearLayout.LayoutParams(-1, -2))
        card.addView(thumbFrame(v, w, th), LinearLayout.LayoutParams(w, th))

        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.TOP; row.setPadding(dp(12), dp(12), dp(6), 0)
        val avatar = TextView(this)
        val letter = v.title.trim().firstOrNull()?.toString()?.uppercase() ?: "V"
        avatar.text = letter; avatar.setTextColor(Color.WHITE); avatar.textSize = 18f; avatar.typeface = Typeface.DEFAULT_BOLD; avatar.gravity = Gravity.CENTER
        val aShape = GradientDrawable(); aShape.shape = GradientDrawable.OVAL; aShape.setColor(avatarColors[Math.abs(v.title.hashCode()) % avatarColors.size])
        avatar.background = aShape; avatar.elevation = dp(3).toFloat()
        val alp = LinearLayout.LayoutParams(dp(40), dp(40)); alp.marginEnd = dp(12); row.addView(avatar, alp)

        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL
        val title = TextView(this); title.text = v.title; title.setTextColor(Color.WHITE); title.textSize = 16f; title.maxLines = 2; title.ellipsize = android.text.TextUtils.TruncateAt.END
        col.addView(title)
        val pos = prefs.position(v.id); val dur = if (v.duration > 0) v.duration else prefs.duration(v.id)
        val pct = if (pos > 0 && dur > 0) ((pos * 100) / dur).toInt().coerceIn(0, 100) else 0
        val sub = TextView(this); sub.text = if (pct > 0) fmt(v.duration) + "  •  شوهد " + pct + "%" else fmt(v.duration)
        sub.setTextColor(gray); sub.textSize = 13f; sub.setPadding(0, dp(3), 0, 0); col.addView(sub)
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))

        val trash = ImageView(this); trash.scaleType = ImageView.ScaleType.FIT_CENTER; trash.setImageDrawable(IconDrawable(IconDrawable.TRASH, Color.WHITE))
        trash.setPadding(dp(10), dp(8), dp(10), dp(12)); trash.isFocusable = false; trash.contentDescription = "حذف"
        trash.setOnClickListener { confirmDelete(v) }; make3D(trash, Color.parseColor("#C62828"), Color.parseColor("#7F0000"), 12)
        row.addView(trash, LinearLayout.LayoutParams(dp(40), dp(42)).apply { setMargins(dp(4), 0, dp(4), dp(4)) })

        val dots = TextView(this); dots.text = "•••"; dots.setTextColor(Color.WHITE); dots.textSize = 14f; dots.gravity = Gravity.CENTER
        dots.setPadding(dp(12), dp(6), dp(12), dp(10)); dots.isFocusable = false; dots.contentDescription = "خيارات"
        dots.setOnClickListener {
            val pm = PopupMenu(this, dots); pm.menu.add(0, 1, 0, "إعادة المشاهدة من البداية")
            pm.setOnMenuItemClickListener { prefs.resetProgress(v.id); refresh(); true }; pm.show()
        }
        make3D(dots, Color.parseColor("#3A3A3A"), Color.parseColor("#1C1C1C"), 12)
        row.addView(dots, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), 0, dp(8), dp(4)) })
        card.addView(row); applyFont(box)

        if (animatedIds.add(v.id)) { box.alpha = 0f; box.translationY = dp(24).toFloat(); box.animate().alpha(1f).translationY(0f).setDuration(280).setInterpolator(DecelerateInterpolator()).start() }
        return box
    }

    private fun thumbFrame(v: VideoItem, w: Int, h: Int): FrameLayout {
        val f = FrameLayout(this); f.setBackgroundColor(Color.parseColor("#222222")); f.layoutParams = ViewGroup.LayoutParams(w, h)
        val shimmer = ShimmerView(this); f.addView(shimmer, FrameLayout.LayoutParams(-1, -1))
        val iv = ImageView(this); iv.scaleType = ImageView.ScaleType.CENTER_CROP; f.addView(iv, FrameLayout.LayoutParams(-1, -1)); loadThumb(v, iv, shimmer)
        val badge = TextView(this); badge.text = fmt(v.duration); badge.setTextColor(Color.WHITE); badge.textSize = 12f; badge.typeface = Typeface.DEFAULT_BOLD; badge.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val bShape = GradientDrawable(); bShape.setColor(Color.parseColor("#CC000000")); bShape.cornerRadius = dp(6).toFloat(); badge.background = bShape; badge.setPadding(dp(7), dp(2), dp(7), dp(2))
        f.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.LEFT or Gravity.BOTTOM).apply { leftMargin = dp(10); bottomMargin = dp(10) })
        val pos = prefs.position(v.id); val dur = if (v.duration > 0) v.duration else prefs.duration(v.id)
        if (pos > 0 && dur > 0) {
            val frac = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            val line = LinearLayout(this); line.orientation = LinearLayout.HORIZONTAL; line.layoutDirection = View.LAYOUT_DIRECTION_LTR
            val done = View(this); done.setBackgroundColor(red); val rest = View(this); rest.setBackgroundColor(Color.parseColor("#55FFFFFF"))
            line.addView(done, LinearLayout.LayoutParams(0, -1, frac)); line.addView(rest, LinearLayout.LayoutParams(0, -1, 1f - frac))
            f.addView(line, FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM))
        }
        val watched = prefs.watched(v.id)
        val tagText: String? = when { watched -> "تم المشاهدة"; pos > 0 && dur > 0 && pos < dur - 3000 -> "-" + fmt(dur - pos); else -> null }
        if (tagText != null) {
            val tg = TextView(this); tg.text = tagText; tg.setTextColor(Color.WHITE); tg.textSize = 11f; tg.typeface = Typeface.DEFAULT_BOLD; tg.layoutDirection = View.LAYOUT_DIRECTION_LTR
            val tgShape = GradientDrawable(); tgShape.setColor(Color.parseColor("#99000000")); tgShape.cornerRadius = dp(6).toFloat(); tg.background = tgShape; tg.setPadding(dp(7), dp(2), dp(7), dp(2))
            val side = if (watched) Gravity.LEFT else Gravity.RIGHT
            f.addView(tg, FrameLayout.LayoutParams(-2, -2, side or Gravity.TOP).apply { leftMargin = dp(10); rightMargin = dp(10); topMargin = dp(10) })
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
    private fun writeDisk(v: VideoItem, bmp: Bitmap) { try { FileOutputStream(thumbFile(v)).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 95, out) } } catch (_: Exception) {} }

    private fun extractFrame(v: VideoItem): Bitmap? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(this, v.uri); val durUs = v.duration * 1000L; val t = if (durUs > 0) (durUs * 0.10).toLong() else 0L
            var vw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var vh = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val tmp = vw; vw = vh; vh = tmp }
            var frame: Bitmap? = null
            if (Build.VERSION.SDK_INT >= 27 && vw > 0 && vh > 0) {
                val targetW = minOf(thumbW, vw); val targetH = maxOf(1, (targetW.toLong() * vh / vw).toInt())
                frame = r.getScaledFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetW, targetH)
            }
            if (frame == null) {
                val raw = r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: r.getFrameAtTime(-1)
                if (raw != null) { frame = if (raw.width > thumbW) { val nh = (raw.height.toLong() * thumbW / raw.width).toInt(); Bitmap.createScaledBitmap(raw, thumbW, nh, true) } else raw }
            }
            if (frame != null) return frame
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
        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) { mp?.let { try { it.setDisplay(h) } catch (_: Exception) {} }; pending?.let { pending = null; startPlayback(it) } }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
            override fun surfaceDestroyed(h: SurfaceHolder) { mp?.let { try { it.setDisplay(null) } catch (_: Exception) {} } }
        })
        playerRoot.addView(surfaceView, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

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
        playerRoot.setOnTouchListener { _, e -> gestures.onTouchEvent(e); true }
        playerRoot.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitVideo() }

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
            override fun onStopTrackingTouch(s: SeekBar?) { userSeeking = false; if (prepared) mp?.seekTo(seekBar.progress) }
        })
        seekRow.addView(seekBar, LinearLayout.LayoutParams(0, -2, 1f))
        timeText = TextView(this); timeText.setTextColor(Color.WHITE); timeText.textSize = 12f; timeText.text = "0:00 / 0:00"; timeText.setPadding(dp(6), 0, dp(6), 0)
        seekRow.addView(timeText); controls.addView(seekRow, LinearLayout.LayoutParams(-1, -2))

        val btnRow = LinearLayout(this); btnRow.orientation = LinearLayout.HORIZONTAL; btnRow.gravity = Gravity.CENTER
        playPause = iconBtn(IconDrawable.PAUSE, Color.parseColor("#FF3D3D"), Color.parseColor("#B71C1C")) {
            val p = mp
            if (p != null && prepared) { if (p.isPlaying) { p.pause(); setPlayIcon(false) } else { p.start(); setPlayIcon(true) } }
        }
        btnRow.addView(playPause, LinearLayout.LayoutParams(dp(56), dp(58)).apply { setMargins(dp(5), dp(2), dp(5), dp(2)) })

        speedBtn = TextView(this); speedBtn.text = "1x"; speedBtn.setTextColor(Color.WHITE); speedBtn.textSize = 14f; speedBtn.typeface = Typeface.DEFAULT_BOLD; speedBtn.gravity = Gravity.CENTER; speedBtn.setPadding(dp(4), dp(6), dp(4), dp(10))
        speedBtn.setOnClickListener { speedIdx = (speedIdx + 1) % speeds.size; applySpeed(); showHud("السرعة " + fmtSpeed(speeds[speedIdx])) }
        make3D(speedBtn, Color.parseColor("#3A3A3A"), Color.parseColor("#1C1C1C"), 24)
        btnRow.addView(speedBtn, LinearLayout.LayoutParams(dp(54), dp(48)).apply { setMargins(dp(5), dp(2), dp(5), dp(2)) })
        btnRow.addView(iconBtn(IconDrawable.LOCK) { setLocked(true) }, btnLp())

        if (Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            btnRow.addView(iconBtn(IconDrawable.PIP) { enterPip() }, btnLp())
        }
        btnRow.addView(iconBtn(IconDrawable.EXPAND) { fillMode = !fillMode; fitVideo() }, btnLp())
        btnRow.addView(iconBtn(IconDrawable.ROTATE) { toggleFullscreen() }, btnLp())
        controls.addView(btnRow, LinearLayout.LayoutParams(-1, -2))
        playerRoot.addView(controls, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        unlockBtn = iconBtn(IconDrawable.UNLOCK, Color.parseColor("#FF3D3D"), Color.parseColor("#B71C1C")) { setLocked(false) }
        unlockBtn.visibility = View.GONE
        playerRoot.addView(unlockBtn, FrameLayout.LayoutParams(dp(54), dp(56), Gravity.LEFT or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(24) })

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

    private fun setLocked(l: Boolean) {
        locked = l; buzz(playerRoot)
        if (l) { controls.visibility = View.GONE; topBar.visibility = View.GONE; showUnlockBtn(); showHud("تم قفل الشاشة") }
        else { ui.removeCallbacks(hideUnlock); unlockBtn.visibility = View.GONE; controls.visibility = View.VISIBLE; topBar.visibility = View.VISIBLE }
    }

    private fun seekBy(delta: Int) {
        val p = mp ?: return; if (!prepared) return
        try {
            val t = (p.currentPosition + delta).coerceIn(0, p.duration)
            if (Build.VERSION.SDK_INT >= 26) p.seekTo(t.toLong(), MediaPlayer.SEEK_CLOSEST) else p.seekTo(t)
            seekBar.progress = t
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
        val p = mp ?: return; if (!prepared || Build.VERSION.SDK_INT < 23) return
        try { val was = p.isPlaying; p.playbackParams = p.playbackParams.setSpeed(s); if (!was) p.pause() } catch (_: Exception) {}
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
        if (Build.VERSION.SDK_INT >= 26 && playerRoot.visibility == View.VISIBLE && prepared && mp?.isPlaying == true) { enterPip() }
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
        if (Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode) {
            try { val p = mp; if (p != null && prepared && p.isPlaying) p.pause() } catch (_: Exception) {}
            setPlayIcon(false)
        }
    }

    private fun openPlayer(v: VideoItem) {
        releasePlayer(); current = v; videoW = 0; videoH = 0; fillMode = false; locked = false; speedIdx = 2; speedBtn.text = "1x"
        unlockBtn.visibility = View.GONE; hudText.visibility = View.GONE; playerTitle.text = v.title; setPlayIcon(true); seekBar.progress = 0
        controls.visibility = View.VISIBLE; topBar.visibility = View.VISIBLE; playerRoot.visibility = View.VISIBLE; hideSystemBars(true)
        if (surfaceView.holder.surface.isValid) { startPlayback(v) } else { pending = v }
    }

    private fun startPlayback(v: VideoItem) {
        releasePlayer(); val p = MediaPlayer(); mp = p; prepared = false
        try {
            p.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).setUsage(AudioAttributes.USAGE_MEDIA).build())
            try { p.setWakeMode(this@MainActivity.applicationContext, PowerManager.PARTIAL_WAKE_LOCK) } catch (_: Exception) {}
            p.setDataSource(this, v.uri); p.setDisplay(surfaceView.holder)
            p.setOnVideoSizeChangedListener { _, w, h ->
                videoW = w; videoH = h
                if (w > h && !isLandscape) { isLandscape = true; requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
                fitVideo()
            }
            p.setOnPreparedListener { m ->
                prepared = true; seekBar.max = m.duration
                val saved = prefs.position(v.id); if (saved > 0 && saved < m.duration - 3000) m.seekTo(saved.toInt())
                m.start(); setPlayIcon(true); if (speedIdx != 2) applySpeed()
            }
            p.setOnCompletionListener { prefs.savePosition(v.id, 0L, it.duration.toLong()); prefs.markWatched(v.id); setPlayIcon(false) }
            p.setOnErrorListener { _, _, _ -> setPlayIcon(false); true }
            p.prepareAsync()
        } catch (_: Exception) { setPlayIcon(false) }
    }

    private fun fitVideo() {
        val cw = playerRoot.width; val ch = playerRoot.height
        if (videoW <= 0 || videoH <= 0 || cw <= 0 || ch <= 0) return
        val sx = cw.toFloat() / videoW; val sy = ch.toFloat() / videoH
        val scale = if (fillMode) maxOf(sx, sy) else minOf(sx, sy)
        val nw = (videoW * scale).toInt(); val nh = (videoH * scale).toInt()
        val lp = surfaceView.layoutParams as FrameLayout.LayoutParams
        if (lp.width != nw || lp.height != nh) { lp.width = nw; lp.height = nh; lp.gravity = Gravity.CENTER; surfaceView.layoutParams = lp }
    }

    private fun toggleFullscreen() {
        isLandscape = !isLandscape
        requestedOrientation = if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars(hide: Boolean) {
        window.decorView.systemUiVisibility = if (hide) {
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        } else { View.SYSTEM_UI_FLAG_VISIBLE }
    }

    private fun closePlayer() {
        val p = mp; val c = current
        if (p != null && c != null && prepared) { try { prefs.savePosition(c.id, p.currentPosition.toLong(), p.duration.toLong()) } catch (_: Exception) {} }
        releasePlayer(); current = null; pending = null; videoW = 0; videoH = 0; fillMode = false; locked = false; speedIdx = 2
        ui.removeCallbacks(hideHud); ui.removeCallbacks(hideUnlock); hudText.visibility = View.GONE; unlockBtn.visibility = View.GONE
        val lp = window.attributes; lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; window.attributes = lp
        playerRoot.visibility = View.GONE; isLandscape = false; requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; hideSystemBars(false); refresh()
    }

    private fun releasePlayer() {
        val p = mp ?: return; mp = null; prepared = false
        try { p.setOnPreparedListener(null); p.setOnCompletionListener(null); p.release() } catch (_: Exception) {}
    }

    override fun onDestroy() {
        ui.removeCallbacks(ticker); ui.removeCallbacks(hideHud); ui.removeCallbacks(hideUnlock)
        try { contentResolver.unregisterContentObserver(mediaObserver) } catch (_: Exception) {}
        releasePlayer(); pool.shutdown(); super.onDestroy()
    }

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun fmt(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    class Prefs(ctx: Context) {
        private val sp: SharedPreferences = ctx.getSharedPreferences("offlinetube", Context.MODE_PRIVATE)
        var folderPath: String get() = sp.getString("folder_path", "") ?: "" set(v) = sp.edit().putString("folder_path", v).apply()
        var lastTab: Int get() = sp.getInt("last_tab", 0) set(v) = sp.edit().putInt("last_tab", v).apply()
        fun position(id: Long): Long = sp.getLong("p_$id", 0L)
        fun duration(id: Long): Long = sp.getLong("d_$id", 0L)
        fun watched(id: Long): Boolean = sp.getBoolean("w_$id", false)
        fun lastTouched(id: Long): Long = sp.getLong("t_$id", 0L)
        fun savePosition(id: Long, position: Long, duration: Long) {
            val e = sp.edit().putLong("p_$id", position).putLong("d_$id", duration)
            if (duration > 0 && position >= duration - 3000) e.putBoolean("w_$id", true)
            else if (position > 3000) { e.putBoolean("w_$id", false); e.putLong("t_$id", System.currentTimeMillis()) }
            e.apply()
        }
        fun markWatched(id: Long) = sp.edit().putBoolean("w_$id", true).apply()
        fun resetProgress(id: Long) = sp.edit().remove("p_$id").remove("w_$id").apply()
        fun clearVideo(id: Long) = sp.edit().remove("p_$id").remove("d_$id").remove("w_$id").remove("t_$id").apply()
    }
}
