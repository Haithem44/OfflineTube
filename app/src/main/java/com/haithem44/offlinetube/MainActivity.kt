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

// --- Data Models ---
data class Vid(val id: Long, val title: String, val duration: Long, val uri: Uri)
data class Row(val type: Int, val items: List<Vid>)

data class Cell(
    val root: View,
    val img: ImageView,
    val dur: TextView,
    val title: TextView,
    val track: LinearLayout,
    val red: View,
    val rest: View,
    val badge: TextView
)

data class RowHolder(val cells: List<Cell>)

// --- Custom Search Icon ---
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

// --- Thumbnail Cache Manager ---
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

// --- Main Activity ---
class MainActivity : Activity() {
    private val allVideos = ArrayList<Vid>()
    private val rows = ArrayList<Row>()
    private val pool = Executors.newFixedThreadPool(3)

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

        // --- بانر الصورة العلوية المرفقة (يتم وضعه عبر استدعاء المورد drawable) ---
        val bannerImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            // استبدل app_banner باسم ملف الصورة بعد وضعها في مجلد drawable
            setImageResource(R.drawable.app_banner)
            setBackgroundColor(Color.parseColor("#0F0F0F"))
        }

        // --- هيدر التطبيق للبحث والعنوان ---
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(12), dp(8))
            setBackgroundColor(bg)
        }

        val subTitle = TextView(this).apply {
            text = "TUBE TV PRO"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val searchIcon = SearchIcon(this).apply { isClickable = true }
        
        header.addView(subTitle, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(searchIcon, LinearLayout.LayoutParams(dp(40), dp(40)))

        // شريط البحث المنسدل
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

        // القائمة الرئيسية مع دعم التمرير والنزول لأسفل
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
                val view = cv ?: buildRow(row.type)
                if (row.type == 4) {
                    setupShortsRow(view, row.items)
                } else {
                    val h = view.tag as RowHolder
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

    private fun hideKb() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
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
    
