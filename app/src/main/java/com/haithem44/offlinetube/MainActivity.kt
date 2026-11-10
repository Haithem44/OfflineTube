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
data class RowHolder(val cells: List<Cell>, val type: Int)

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
                // التأكد من مطابقة نوع العرض لمنع تداخل واجهات الفيديوهات والشورتس
                val view = if (cv != null && cv.tag is Int && cv.tag == row.type) {
                    cv
                } else {
                    buildRow(row.type).apply { tag = row.type }
                }

                if (row.type == 4) {
                    setupShortsRow(view, row.items)
                } else {
                    val h = view.tag as? RowHolder
                    if (h != null && h.type == row.type) {
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
            
