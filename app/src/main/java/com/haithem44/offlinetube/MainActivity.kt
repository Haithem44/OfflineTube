package com.haithem44.offlinetube

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.LruCache
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.util.concurrent.Executors

data class Vid(val id: Long, val title: String, val duration: Long, val uri: Uri)
data class Row(val type: Int, val items: List<Vid>)

data class Cell(
    val root: View, val img: ImageView, val dur: TextView, val title: TextView,
    val track: LinearLayout, val red: View, val rest: View, val badge: TextView
)
data class RowHolder(val cells: List<Cell>)

class SearchIcon(c: Context) : View(c) {
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
        val cx = w * 0.45f
        val cy = h * 0.45f
        val r = w * 0.2f
        canvas.drawCircle(cx, cy, r, paint)
        val d = r * 0.7071f
        canvas.drawLine(cx + d, cy + d, w * 0.75f, h * 0.75f, paint)
    }
}

object ThumbnailCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val memoryCache = object : LruCache<Long, Bitmap>(cacheSize) {
        override fun sizeOf(key: Long, bitmap: Bitmap): Int = bitmap.byteCount / 1024
    }

    fun get(key: Long): Bitmap? = memoryCache.get(key)
    fun put(key: Long, bitmap: Bitmap) {
        if (get(key) == null) memoryCache.put(key, bitmap)
    }
}

class MainActivity : Activity() {
    private val allVideos = ArrayList<Vid>()
    private val rows = ArrayList<Row>()
    private val pool = Executors.newFixedThreadPool(4)
    private lateinit var adapter: BaseAdapter
    private lateinit var input: EditText
    private lateinit var emptyText: TextView
    private lateinit var listView: ListView
    private lateinit var searchBar: LinearLayout

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun hideKb() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec)
        else String.format("%d:%02d", m, sec)
    }

    @Suppress("DEPRECATION")
    private fun loadThumb(v: Vid, img: ImageView) {
        pool.execute {
            val bmp: Bitmap? = try {
                if (Build.VERSION.SDK_INT >= 29)
                    contentResolver.loadThumbnail(v.uri, Size(480, 270), null)
                else MediaStore.Video.Thumbnails.getThumbnail(
                    contentResolver, v.id, MediaStore.Video.Thumbnails.MINI_KIND, null
                )
            } catch (e: Exception) { null }
            if (bmp != null) {
                ThumbnailCache.put(v.id, bmp)
                runOnUiThread {
                    if (img.tag == v.id) {
                        img.setImageBitmap(bmp)
                    }
                }
            }
        }
    }

    private fun buildCell(style: Int): Cell {
        val small = style != 0
        val frame = FrameLayout(this)
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#1F1F1F"))
        }
        frame.addView(img, FrameLayout.LayoutParams(-1, -1))

        val dur = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = if (small) 10f else 12f
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(dp(5), dp(1), dp(5), dp(1))
        }
        val dlp = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END)
        dlp.setMargins(dp(6), dp(6), dp(6), dp(10))
        frame.addView(dur, dlp)

        val badge = TextView(this).apply {
            text = "✓ شوهد"
            setTextColor(Color.WHITE)
            textSize = if (small) 9f else 11f
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(dp(5), dp(1), dp(5), dp(1))
            visibility = View.GONE
        }
        val blp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START)
        blp.setMargins(dp(6), dp(6), dp(6), dp(6))
        frame.addView(badge, blp)

        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 1000f
            setBackgroundColor(Color.parseColor("#66FFFFFF"))
            visibility = View.GONE
        }
        val red = View(this).apply { setBackgroundColor(Color.parseColor("#FF0000")) }
        val rest = View(this)
        track.addView(red, LinearLayout.LayoutParams(0, -1, 0f))
        track.addView(rest, LinearLayout.LayoutParams(0, -1, 1000f))
        frame.addView(track, FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM))

        if (small) {
            val r = dp(8).toFloat()
            frame.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, r)
                }
            }
            frame.clipToOutline = true
        }

        val ts = when (style) {
            0 -> 15f
            1 -> 13f
            2 -> 14f
            else -> 12f
        }
        val title = TextView(this).apply {
            setTextColor(Color.parseColor("#F1F1F1"))
            textSize = ts
            maxLines = if (style == 2) 3 else 2
            ellipsize = TextUtils.TruncateAt.END
        }

        val root = LinearLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0F0F0F"))
        }
        
        if (style == 2) {
            root.orientation = LinearLayout.HORIZONTAL
            root.setPadding(dp(12), dp(8), dp(12), dp(8))
            root.addView(frame, LinearLayout.LayoutParams(dp(160), dp(92)))
            title.setPadding(dp(10), 0, 0, 0)
            root.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        } else {
            root.orientation = LinearLayout.VERTICAL
            val h = when (style) {
                0 -> dp(220)
                1 -> dp(115)
                else -> dp(200)
            }
            root.addView(frame, LinearLayout.LayoutParams(-1, h))
            val side = if (style == 0) dp(12) else dp(6)
            title.setPadding(side, dp(8), side, dp(8))
            root.addView(title, LinearLayout.LayoutParams(-1, -2))
        }
        return Cell(root, img, dur, title, track, red, rest, badge)
    }

    private fun buildRow(type: Int): View {
        if (type == 4) {
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(12))
                setBackgroundColor(Color.parseColor("#0F0F0F"))
            }
            val titleTv = TextView(this).apply {
                text = "⚡ Shorts"
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dp(4), 0, 0, dp(8))
            }
            val horizontalScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
            }
            val innerLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            horizontalScroll.addView(innerLayout, ViewGroup.LayoutParams(-2, -1))
            container.addView(titleTv)
            container.addView(horizontalScroll)
            return container
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cells = ArrayList<Cell>()
        when (type) {
            0 -> {
                val c = buildCell(0)
                row.setPadding(0, 0, 0, dp(14))
                row.addView(c.root, LinearLayout.LayoutParams(-1, -2))
                cells.add(c)
            }
            1 -> {
                row.setPadding(dp(6), 0, dp(6), dp(14))
                for (i in 0 until 2) {
                    val c = buildCell(1)
                    val lp = LinearLayout.LayoutParams(0, -2, 1f)
                    lp.setMargins(dp(6), 0, dp(6), 0)
                    row.addView(c.root, lp)
                    cells.add(c)
                }
            }
            2 -> {
                val c = buildCell(2)
                row.addView(c.root, LinearLayout.LayoutParams(-1, -2))
                cells.add(c)
            }
            else -> {
                row.setPadding(dp(8), 0, dp(8), dp(14))
                for (i in 0 until 3) {
                    val c = buildCell(3)
                    val lp = LinearLayout.LayoutParams(0, -2, 1f)
                    lp.setMargins(dp(4), 0, dp(4), 0)
                    row.addView(c.root, lp)
                    cells.add(c)
                }
            }
        }
        row.tag = RowHolder(cells)
        return row
    }

    private fun setupShortsRow(rowView: View, items: List<Vid>) {
        val container = (rowView as LinearLayout).getChildAt(1) as HorizontalScrollView
        val innerLayout = container.getChildAt(0) as LinearLayout
        innerLayout.removeAllViews()

        for (v in items) {
            val shortCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), 0, dp(4), 0)
            }
            val frame = FrameLayout(this)
            val img = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#1F1F1F"))
                tag = v.id
            }
            frame.addView(img, FrameLayout.LayoutParams(dp(110), dp(190)))

            val r = dp(8).toFloat()
            frame.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, r)
                }
            }
            frame.clipToOutline = true

            val title = TextView(this).apply {
                text = v.title
                setTextColor(Color.WHITE)
                textSize = 12f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                width = dp(110)
                setPadding(0, dp(4), 0, 0)
            }

            shortCard.addView(frame)
            shortCard.addView(title)

            shortCard.setOnClickListener {
                hideKb()
                startActivity(Intent(this, PlayerActivity::class.java).apply {
                    data = v.uri
                    putExtra("id", v.id)
                    putExtra("title", v.title)
                })
            }

            val b = ThumbnailCache.get(v.id)
            if (b != null) {
                img.setImageBitmap(b)
            } else {
                img.setImageDrawable(null)
                loadThumb(v, img)
            }

            innerLayout.addView(shortCard)
        }
    }

    private fun bind(c: Cell, v: Vid) {
        c.title.text = v.title
        c.dur.text = fmt(v.duration)
        c.img.tag = v.id

        val savedDur = Prog.dur(this, v.id)
        val d = if (savedDur > 0) savedDur else v.duration
        val pos = Prog.pos(this, v.id)
        if (pos <= 0 || d <= 0) {
            c.track.visibility = View.GONE
            c.badge.visibility = View.GONE
        } else {
            val frac = (pos.toDouble() / d).coerceIn(0.0, 1.0)
            c.track.visibility = View.VISIBLE
            val redParams = c.red.layoutParams as LinearLayout.LayoutParams
            val restParams = c.rest.layoutParams as LinearLayout.LayoutParams
            redParams.weight = (frac * 1000).toFloat()
            restParams.weight = (1000 - frac * 1000).toFloat()
            c.track.requestLayout()
            c.badge.visibility = if (frac > 0.95) View.VISIBLE else View.GONE
        }

        c.root.setOnClickListener {
            hideKb()
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                data = v.uri
                putExtra("id", v.id)
                putExtra("title", v.title)
            })
        }

        val b = ThumbnailCache.get(v.id)
        if (b != null) {
            c.img.setImageBitmap(b)
        } else {
            c.img.setImageDrawable(null)
            loadThumb(v, c.img)
        }
    }

    private fun rebuildRows(list: List<Vid>, searching: Boolean) {
        rows.clear()
        if (searching) {
            for (v in list) rows.add(Row(2, listOf(v)))
            return
        }
        val pattern = intArrayOf(0, 1, 3, 4, 2, 2)
        val sizes = intArrayOf(1, 2, 3, 4, 1, 1)
        var i = 0
        var k = 0
        while (i < list.size) {
            val t = pattern[k % pattern.size]
            val n = if (t == 4) minOf(4, list.size - i) else sizes[k % sizes.size]
            val end = minOf(i + n, list.size)
            rows.add(Row(t, list.subList(i, end).toList()))
            i = end
            k++
        }
    }

    private fun applyFilter(q: String) {
        val query = q.trim()
        val list = if (query.isEmpty()) allVideos
        else allVideos.filter { it.title.contains(query, ignoreCase = true) }
        rebuildRows(list, query.isNotEmpty())
        adapter.notifyDataSetChanged()
        emptyText.visibility =
            if (rows.isEmpty() && allVideos.isNotEmpty()) View.VISIBLE else View.GONE
        if (rows.isNotEmpty()) listView.setSelection(0)
    }

    private fun loadVideos() {
        allVideos.clear()
        val proj = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
        )
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, null, null,
            MediaStore.Video.Media.DATE_ADDED + " DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                allVideos.add(
                    Vid(
                        id,
                        (c.getString(1) ?: "video").substringBeforeLast('.'),
                        c.getLong(2),
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    )
                )
            }
        }
        applyFilter(input.text.toString())
        if (allVideos.isEmpty())
            Toast.makeText(this, "لا توجد فيديوهات على الهاتف", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = Color.parseColor("#0F0F0F")
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            fitsSystemWindows = true
        }

        // --- بانر الصورة العلوية المرفقة ---
        val bannerImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            try {
                setImageResource(R.drawable.app_banner)
            } catch (e: Exception) {
                setBackgroundColor(Color.parseColor("#1F1F1F"))
            }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(12), dp(8))
            setBackgroundColor(bg)
        }
        val subTitle = TextView(this).apply {
            text = "TUBE TV PRO"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val searchIcon = SearchIcon(this).apply { isClickable = true }
        header.addView(subTitle, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(searchIcon, LinearLayout.LayoutParams(dp(40), dp(40)))

        searchBar = LinearLayout(this).apply {
            visibility = View.GONE
            setPadding(dp(12), dp(4), dp(12), dp(8))
            setBackgroundColor(bg)
        }
        input = EditText(this).apply {
            hint = "البحث في الفيديوهات..."
            setHintTextColor(Color.parseColor("#888888"))
            setTextColor(Color.WHITE)
            textSize = 15f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#222222"))
                cornerRadius = dp(20).toFloat()
            }
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        searchBar.addView(input, LinearLayout.LayoutParams(-1, -2))

        listView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(bg)
            isVerticalScrollBarEnabled = true
        }
        emptyText = TextView(this).apply {
            text = "لا توجد فيديوهات"
            setTextColor(Color.parseColor("#888888"))
            textSize = 16f
            visibility = View.GONE
        }
        val listFrame = FrameLayout(this).apply {
            addView(listView, FrameLayout.LayoutParams(-1, -1))
            addView(emptyText, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }

        root.addView(bannerImageView, LinearLayout.LayoutParams(-1, dp(180)))
        root.addView(header)
        root.addView(searchBar)
        root.addView(listFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        adapter = object : BaseAdapter() {
            override fun getCount() = rows.size
            override fun getItem(p: Int) = rows[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getViewTypeCount() = 5
            override fun getItemViewType(p: Int) = rows[p].type

            override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
                val row = rows[p]
                val view = if (cv != null && cv.tag is Int && cv.tag == row.type) {
                    cv
                } else {
                    buildRow(row.type).apply { tag = row.type }
                }

                if (row.type == 4) {
                    setupShortsRow(view, row.items)
                } else {
                    val h = view.tag as? RowHolder
                    if (h != null) {
                        for (i in h.cells.indices) {
                            val c = h.cells[i]
                            if (i < row.items.size) {
                                c.root.visibility = View.VISIBLE
                                bind(c, row.items[i])
                            } else {
                                c.root.visibility = View.INVISIBLE
                                c.root.setOnClickListener(null)
                            }
                        }
                    }
                }
                return view
            }
        }
        listView.adapter = adapter

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s?.toString() ?: "")
            }
        })
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKb()
                true
            } else false
        }
        searchIcon.setOnClickListener {
            if (searchBar.visibility == View.VISIBLE) {
                input.setText("")
                searchBar.visibility = View.GONE
                hideKb()
            } else {
                searchBar.visibility = View.VISIBLE
                input.requestFocus()
                input.post {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }

        val perm = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) loadVideos()
        else requestPermissions(arrayOf(perm), 1)
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        if (r.isNotEmpty() && r[0] == PackageManager.PERMISSION_GRANTED) loadVideos()
        else Toast.makeText(this, "الرجاء السماح بالوصول للفيديوهات", Toast.LENGTH_LONG).show()
    }
}
