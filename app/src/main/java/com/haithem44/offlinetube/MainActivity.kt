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
data class VideoItem(val id: Long, val title: String, val duration: Long, val uri: Uri)
data class VideoRow(val type: Int, val items: List<VideoItem>)

data class VideoCell(
    val root: View,
    val img: ImageView,
    val dur: TextView,
    val title: TextView,
    val track: LinearLayout,
    val red: View,
    val rest: View,
    val badge: TextView
)

data class RowViewHolder(val cells: List<VideoCell>)

// --- Custom Search Icon ---
class SearchIconView(context: Context) : View(context) {
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
    private val cacheSize = maxMemory / 8 // استخدام 12.5% من الذاكرة المتاحة
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

    private val allVideos = ArrayList<VideoItem>()
    private val rows = ArrayList<VideoRow>()
    private val executor = Executors.newFixedThreadPool(3)

    private lateinit var adapter: BaseAdapter
    private lateinit var searchInput: EditText
    private lateinit var emptyTextView: TextView
    private lateinit var videoListView: ListView
    private lateinit var searchBarLayout: LinearLayout

    private fun dp(valInDp: Int): Int = (valInDp * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val backgroundColor = Color.parseColor("#0F0F0F")
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        initUI(backgroundColor)
        setupListeners()
        checkPermissionsAndLoad()
    }

    private fun initUI(bg: Int) {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            fitsSystemWindows = true
        }

        // Header
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(6), dp(8), dp(6))
        }
        val appTitle = TextView(this).apply {
            text = "▶ OfflineTube"
            setTextColor(Color.WHITE)
            textSize = 22f
        }
        val searchIcon = SearchIconView(this).apply { isClickable = true }
        headerLayout.addView(appTitle, LinearLayout.LayoutParams(0, -2, 1f))
        headerLayout.addView(searchIcon, LinearLayout.LayoutParams(dp(44), dp(44)))

        // Search Bar
        searchBarLayout = LinearLayout(this).apply {
            visibility = View.GONE
            setPadding(dp(12), 0, dp(12), dp(8))
        }
        searchInput = EditText(this).apply {
            hint = "ابحث عن فيديو..."
            setHintTextColor(Color.parseColor("#888888"))
            setTextColor(Color.WHITE)
            textSize = 15f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#272727"))
                cornerRadius = dp(22).toFloat()
            }
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        searchBarLayout.addView(searchInput, LinearLayout.LayoutParams(-1, -2))

        // Content List & Empty View
        videoListView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(bg)
        }
        emptyTextView = TextView(this).apply {
            text = "لا توجد نتائج"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 16f
            visibility = View.GONE
        }
        val listFrame = FrameLayout(this).apply {
            addView(videoListView, FrameLayout.LayoutParams(-1, -1))
            addView(emptyTextView, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }

        rootLayout.addView(headerLayout)
        rootLayout.addView(searchBarLayout)
        rootLayout.addView(listFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(rootLayout)

        // Adapter Setup
        adapter = object : BaseAdapter() {
            override fun getCount() = rows.size
            override fun getItem(p: Int) = rows[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getViewTypeCount() = 4
            override fun getItemViewType(p: Int) = rows[p].type

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = rows[position]
                val view = convertView ?: buildRowView(row.type)
                val holder = view.tag as RowViewHolder
                
                for (i in holder.cells.indices) {
                    val cell = holder.cells[i]
                    if (i < row.items.size) {
                        cell.root.visibility = View.VISIBLE
                        bindCellData(cell, row.items[i])
                    } else {
                        cell.root.visibility = View.INVISIBLE
                        cell.root.setOnClickListener(null)
                    }
                }
                return view
            }
        }
        videoListView.adapter = adapter

        searchIcon.setOnClickListener { toggleSearch(!searchBarLayout.isVisible()) }
    }

    private fun View.isVisible() = visibility == View.VISIBLE

    private fun toggleSearch(show: Boolean) {
        if (show) {
            searchBarLayout.visibility = View.VISIBLE
            searchInput.requestFocus()
            searchInput.post {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            searchInput.setText("")
            searchBarLayout.visibility = View.GONE
            hideKeyboard()
        }
    }

    private fun setupListeners() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s?.toString() ?: "")
            }
        })

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else false
        }
    }

    private fun checkPermissionsAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            loadVideosFromDevice()
        } else {
            requestPermissions(arrayOf(permission), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadVideosFromDevice()
        } else {
            Toast.makeText(this, "الرجاء السماح بالوصول للفيديوهات لعرضها", Toast.LENGTH_LONG).show()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    private fun applyFilter(queryText: String) {
        val query = queryText.trim()
        val filteredList = if (query.isEmpty()) allVideos
        else allVideos.filter { it.title.contains(query, ignoreCase = true) }
        
        rebuildRows(filteredList, query.isNotEmpty())
        adapter.notifyDataSetChanged()
        emptyTextView.visibility = if (rows.isEmpty() && allVideos.isNotEmpty()) View.VISIBLE else View.GONE
        if (rows.isNotEmpty()) videoListView.setSelection(0)
    }

    private fun rebuildRows(list: List<VideoItem>, isSearching: Boolean) {
        rows.clear()
        if (isSearching) {
            for (v in list) rows.add(VideoRow(2, listOf(v)))
            return
        }
        val pattern = intArrayOf(0, 1, 3, 2, 2)
        val sizes = intArrayOf(1, 2, 3, 1, 1)
        var index = 0
        var step = 0
        while (index < list.size) {
            val type = pattern[step % pattern.size]
            val size = sizes[step % sizes.size]
            val end = minOf(index + size, list.size)
            rows.add(VideoRow(type, list.subList(index, end).toList()))
            index = end
            step++
        }
    }

    private fun buildRowView(type: Int): View {
        val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cellsList = ArrayList<VideoCell>()
        
        when (type) {
            0 -> {
                val cell = createCellView(0)
                rowLayout.setPadding(0, 0, 0, dp(14))
                rowLayout.addView(cell.root, LinearLayout.LayoutParams(-1, -2))
                cellsList.add(cell)
            }
            1 -> {
                rowLayout.setPadding(dp(6), 0, dp(6), dp(14))
                for (i in 0 until 2) {
                    val cell = createCellView(1)
                    val lp = LinearLayout.LayoutParams(0, -2, 1f).apply {
                        setMargins(dp(6), 0, dp(6), 0)
                    }
                    rowLayout.addView(cell.root, lp)
                    cellsList.add(cell)
                }
            }
            2 -> {
                val cell = createCellView(2)
                rowLayout.addView(cell.root, LinearLayout.LayoutParams(-1, -2))
                cellsList.add(cell)
            }
            else -> {
                rowLayout.setPadding(dp(8), 0, dp(8), dp(14))
                for (i in 0 until 3) {
                    val cell = createCellView(3)
                    val lp = LinearLayout.LayoutParams(0, -2, 1f).apply {
                        setMargins(dp(4), 0, dp(4), 0)
                    }
                    rowLayout.addView(cell.root, lp)
                    cellsList.add(cell)
                }
            }
        }
        rowLayout.tag = RowViewHolder(cellsList)
        return rowLayout
    }

    private fun createCellView(style: Int): VideoCell {
        val isSmall = style != 0
        val frameLayout = FrameLayout(this)
        
        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#222222"))
        }
        frameLayout.addView(imageView, FrameLayout.LayoutParams(-1, -1))

        val durationTv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = if (isSmall) 10f else 12f
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(dp(5), dp(1), dp(5), dp(1))
        }
        val durParams = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(dp(6), dp(6), dp(6), dp(10))
        }
        frameLayout.addView(durationTv, durParams)

        val badgeTv = TextView(this).apply {
            text = "✓ شوهد"
            setTextColor(Color.WHITE)
            textSize = if (isSmall) 9f else 11f
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(dp(5), dp(1), dp(5), dp(1))
            visibility = View.GONE
        }
        val badgeParams = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(6), dp(6), dp(6), dp(6))
        }
        frameLayout.addView(badgeTv, badgeParams)

        val trackLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 1000f
            setBackgroundColor(Color.parseColor("#66FFFFFF"))
            visibility = View.GONE
        }
        val redProgress = View(this).apply { setBackgroundColor(Color.parseColor("#FF0000")) }
