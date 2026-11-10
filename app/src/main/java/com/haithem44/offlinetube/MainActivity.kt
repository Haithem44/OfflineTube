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
import android.util.LruCache
import android.util.Size
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import java.util.concurrent.Executors

data class VideoItem(val id: Long, val title: String, val duration: Long, val uri: Uri)

class MainActivity : Activity() {

    private val bg = Color.parseColor("#0F0F0F")
    private val red = Color.parseColor("#FF0000")
    private val gray = Color.parseColor("#AAAAAA")

    private val all = ArrayList<VideoItem>()
    private val shown = ArrayList<VideoItem>()

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var listView: ListView
    private lateinit var adapter: VideoAdapter
    private lateinit var logo: LinearLayout
    private lateinit var searchBox: EditText
    private var hasBanner = false
    private var tab = 0
    private var query = ""

    private val tabNames = arrayOf("الصفحة الرئيسية", "تابع المشاهدة", "الأطول", "الأقصر")
    private val tabTexts = ArrayList<TextView>()
    private val tabLines = ArrayList<View>()

    private val avatarColors = intArrayOf(
        Color.parseColor("#E53935"), Color.parseColor("#8E24AA"),
        Color.parseColor("#3949AB"), Color.parseColor("#00897B"),
        Color.parseColor("#F4511E"), Color.parseColor("#6D4C41")
    )

    private val pool = Executors.newFixedThreadPool(3)
    private val ui = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<Long, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount / 1024
    }

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
        main.layoutDirection = View.LAYOUT_DIRECTION_RTL

        main.addView(buildTopBlock())
        main.addView(buildTabs(), LinearLayout.LayoutParams(-1, -2))

        listView = ListView(this)
        listView.divider = null
        listView.setBackgroundColor(bg)
        adapter = VideoAdapter()
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, pos, _ ->
            if (pos in 0 until shown.size) openPlayer(shown[pos])
        }

        val emptyText = TextView(this)
        emptyText.text = "لا توجد فيديوهات"
        emptyText.setTextColor(gray)
        emptyText.textSize = 16f
        emptyText.gravity = Gravity.CENTER

        val listFrame = FrameLayout(this)
        listFrame.addView(listView, FrameLayout.LayoutParams(-1, -1))
        listFrame.addView(emptyText, FrameLayout.LayoutParams(-1, -1))
        listView.emptyView = emptyText
        main.addView(listFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        main.addView(buildBottomNav(), LinearLayout.LayoutParams(-1, dp(58)))
        rootFrame.addView(main, FrameLayout.LayoutParams(-1, -1))

        buildPlayer()
        playerRoot.layoutDirection = View.LAYOUT_DIRECTION_LTR
        rootFrame.addView(playerRoot, FrameLayout.LayoutParams(-1, -1))
        playerRoot.visibility = View.GONE

        setContentView(rootFrame)
        ui.post(ticker)
        checkPermissionAndLoad()
    }

    // ---------------- Top block (logo / banner / search) ----------------

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
        block.setBackgroundColor(bg)
        block.layoutParams = LinearLayout.LayoutParams(-1, h)

        if (hasBanner) {
            val iv = ImageView(this)
            iv.setImageResource(resId)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            block.addView(iv, FrameLayout.LayoutParams(-1, -1))
            val scrim = View(this)
            scrim.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#99000000"), Color.TRANSPARENT)
            )
            block.addView(scrim, FrameLayout.LayoutParams(-1, dp(70), Gravity.TOP))
        }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(12), 0, dp(8), 0)

        // شعار على طريقة يوتيوب
        logo = LinearLayout(this)
        logo.orientation = LinearLayout.HORIZONTAL
        logo.gravity = Gravity.CENTER_VERTICAL
        logo.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val icon = TextView(this)
        icon.text = "▶"
        icon.setTextColor(Color.WHITE)
        icon.textSize = 12f
        icon.gravity = Gravity.CENTER
        val iconShape = GradientDrawable()
        iconShape.setColor(red)
        iconShape.cornerRadius = dp(7).toFloat()
        icon.background = iconShape
        icon.setPadding(dp(8), dp(3), dp(8), dp(3))
        logo.addView(icon)
        val name = TextView(this)
        name.text = "OfflineTube"
        name.setTextColor(Color.WHITE)
        name.textSize = 21f
        name.typeface = Typeface.DEFAULT_BOLD
        name.setPadding(dp(6), 0, 0, 0)
        logo.addView(name)
        logo.visibility = if (hasBanner) View.INVISIBLE else View.VISIBLE
        row.addView(logo, LinearLayout.LayoutParams(0, -2, 1f))

        searchBox = EditText(this)
        searchBox.hint = "ابحث عن فيديو"
        searchBox.setHintTextColor(Color.LTGRAY)
        searchBox.setTextColor(Color.WHITE)
        searchBox.isSingleLine = true
        searchBox.visibility = View.GONE
        val sbShape = GradientDrawable()
        sbShape.setColor(Color.parseColor("#B3303030"))
        sbShape.cornerRadius = dp(20).toFloat()
        searchBox.background = sbShape
        searchBox.setPadding(dp(14), dp(6), dp(14), dp(6))
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                refresh()
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
        lp.marginStart = dp(8)
        row.addView(searchBtn, lp)

        block.addView(row, FrameLayout.LayoutParams(-1, dp(56), Gravity.TOP))
        return block
    }

    private fun toggleSearch() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (searchBox.visibility == View.VISIBLE) {
            searchBox.setText("")
            searchBox.visibility = View.GONE
            logo.visibility = if (hasBanner) View.INVISIBLE else View.VISIBLE
            imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
        } else {
            openSearch()
        }
    }

    private fun openSearch() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        logo.visibility = View.GONE
        searchBox.visibility = View.VISIBLE
        searchBox.requestFocus()
        imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT)
    }

    // ---------------- Tabs ----------------

    private fun buildTabs(): View {
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.setBackgroundColor(bg)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(6), 0, dp(6), 0)

        for (i in tabNames.indices) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val t = TextView(this)
            t.text = tabNames[i]
            t.textSize = 16f
            t.setPadding(dp(14), dp(12), dp(14), dp(10))
            val line = View(this)
            col.addView(t)
            col.addView(line, LinearLayout.LayoutParams(-1, dp(3)))
            col.setOnClickListener { selectTab(i) }
            tabTexts.add(t)
            tabLines.add(line)
            row.addView(col)
        }
        scroll.addView(row)
        updateTabStyles()
        return scroll
    }

    private fun updateTabStyles() {
        for (i in tabTexts.indices) {
            val sel = i == tab
            tabTexts[i].setTextColor(if (sel) Color.WHITE else gray)
            tabTexts[i].typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            tabLines[i].setBackgroundColor(if (sel) red else Color.TRANSPARENT)
        }
    }

    private fun selectTab(i: Int) {
        tab = i
        updateTabStyles()
        refresh()
        listView.setSelection(0)
    }

    // ---------------- Bottom nav ----------------

    private fun buildBottomNav(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.setBackgroundColor(bg)

        val divider = View(this)
        divider.setBackgroundColor(Color.parseColor("#2A2A2A"))
        bar.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(navItem("🏠", "الرئيسية") {
            selectTab(0)
        }, LinearLayout.LayoutParams(0, -1, 1f))
        row.addView(navItem("🔍", "بحث") {
            openSearch()
        }, LinearLayout.LayoutParams(0, -1, 1f))
        row.addView(navItem("▶", "تابع") {
            selectTab(1)
        }, LinearLayout.LayoutParams(0, -1, 1f))
        bar.addView(row, LinearLayout.LayoutParams(-1, -1))
        return bar
    }

    private fun navItem(icon: String, label: String, onClick: () -> Unit): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        val i = TextView(this)
        i.text = icon
        i.textSize = 20f
        i.setTextColor(Color.WHITE)
        i.gravity = Gravity.CENTER
        val l = TextView(this)
        l.text = label
        l.textSize = 11f
        l.setTextColor(Color.WHITE)
        l.gravity = Gravity.CENTER
        col.addView(i)
        col.addView(l)
        col.setOnClickListener { onClick() }
        return col
    }

    // ---------------- Filtering ----------------

    private fun refresh() {
        val s = query.trim().lowercase()
        var list: List<VideoItem> = all.filter { s.isEmpty() || it.title.lowercase().contains(s) }
        list = when (tab) {
            1 -> list.filter { prefs.getLong("p_" + it.id, 0L) > 0L }
            2 -> list.sortedByDescending { it.duration }
            3 -> list.sortedBy { it.duration }
            else -> list
        }
        shown.clear()
        shown.addAll(list)
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
                refresh()
            }
        }
    }

    // ---------------- List adapter (YouTube-style items) ----------------

    inner class VideoAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = shown[p].id
        override fun getViewTypeCount() = 1
        override fun getItemViewType(p: Int) = 0
        override fun getView(p: Int, convertView: View?, parent: ViewGroup?): View {
            return videoItemView(shown[p])
        }
    }

    private fun screenW() = resources.displayMetrics.widthPixels

    private fun videoItemView(v: VideoItem): View {
        val w = screenW()
        val th = w * 9 / 16

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, 0, 0, dp(22))

        // صورة بعرض الشاشة كاملاً
        box.addView(thumbFrame(v, w, th), LinearLayout.LayoutParams(w, th))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.TOP
        row.setPadding(dp(12), dp(12), dp(6), 0)

        // دائرة ملوّنة بأول حرف من العنوان
        val avatar = TextView(this)
        val letter = v.title.trim().firstOrNull()?.toString()?.uppercase() ?: "V"
        avatar.text = letter
        avatar.setTextColor(Color.WHITE)
        avatar.textSize = 18f
        avatar.typeface = Typeface.DEFAULT_BOLD
        avatar.gravity = Gravity.CENTER
        val aShape = GradientDrawable()
        aShape.shape = GradientDrawable.OVAL
        aShape.setColor(avatarColors[Math.abs(v.title.hashCode()) % avatarColors.size])
        avatar.background = aShape
        val alp = LinearLayout.LayoutParams(dp(40), dp(40))
        alp.marginEnd = dp(12)
        row.addView(avatar, alp)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL

        val title = TextView(this)
        title.text = v.title
        title.setTextColor(Color.WHITE)
        title.textSize = 16f
        title.maxLines = 2
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        col.addView(title)

        val pos = prefs.getLong("p_" + v.id, 0L)
        val dur = if (v.duration > 0) v.duration else prefs.getLong("d_" + v.id, 0L)
        val pct = if (pos > 0 && dur > 0) ((pos * 100) / dur).toInt().coerceIn(0, 100) else 0
        val sub = TextView(this)
        sub.text = if (pct > 0) fmt(v.duration) + "  •  شوهد " + pct + "%" else fmt(v.duration)
        sub.setTextColor(gray)
        sub.textSize = 13f
        sub.setPadding(0, dp(3), 0, 0)
        col.addView(sub)
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))

        // زر القائمة
        val dots = TextView(this)
        dots.text = "•••"
        dots.setTextColor(Color.WHITE)
        dots.textSize = 14f
        dots.setPadding(dp(12), dp(6), dp(12), dp(10))
        dots.isFocusable = false
        dots.setOnClickListener {
            val pm = PopupMenu(this, dots)
            pm.menu.add(0, 1, 0, "إعادة المشاهدة من البداية")
            pm.setOnMenuItemClickListener {
                prefs.edit().remove("p_" + v.id).apply()
                refresh()
                true
            }
            pm.show()
        }
        row.addView(dots)

        box.addView(row)
        return box
    }

    // Thumbnail + duration badge (bottom-left) + red progress line
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
        badge.typeface = Typeface.DEFAULT_BOLD
        badge.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val bShape = GradientDrawable()
        bShape.setColor(Color.parseColor("#CC000000"))
        bShape.cornerRadius = dp(6).toFloat()
        badge.background = bShape
        badge.setPadding(dp(7), dp(2), dp(7), dp(2))
        f.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.LEFT or Gravity.BOTTOM).apply {
            leftMargin = dp(10); bottomMargin = dp(10)
        })

        val pos = prefs.getLong("p_" + v.id, 0L)
        val dur = if (v.duration > 0) v.duration else prefs.getLong("d_" + v.id, 0L)
        if (pos > 0 && dur > 0) {
            val frac = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            line.layoutDirection = View.LAYOUT_DIRECTION_LTR
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
        refresh()
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
