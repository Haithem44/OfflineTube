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
class Row(val type: Int, val items: List<Vid>)
class Cell(
    val root: View, val img: ImageView, val dur: TextView, val title: TextView,
    val track: LinearLayout, val red: View, val rest: View, val badge: TextView
)
class RowHolder(val cells: List<Cell>)

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

class MainActivity : Activity() {
    private val allVideos = ArrayList<Vid>()
    private val rows = ArrayList<Row>()
    private val pool = Executors.newFixedThreadPool(3)
    private val cache = HashMap<Long, Bitmap>()
    private lateinit var adapter: BaseAdapter
    private lateinit var input: EditText
    private lateinit var emptyText: TextView
    private lateinit var listView: ListView

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

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(6), dp(8), dp(6))
        }
        val titleTv = TextView(this).apply {
            text = "▶ OfflineTube"
            setTextColor(Color.WHITE)
            textSize = 22f
        }
        val searchIcon = SearchIcon(this).apply { isClickable = true }
        header.addView(titleTv, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(searchIcon, LinearLayout.LayoutParams(dp(44), dp(44)))

        val searchBar = LinearLayout(this).apply {
            visibility = View.GONE
            setPadding(dp(12), 0, dp(12), dp(8))
        }
        input = EditText(this).apply {
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
        searchBar.addView(input, LinearLayout.LayoutParams(-1, -2))

        listView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(bg)
        }
        emptyText = TextView(this).apply {
            text = "لا توجد نتائج"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 16f
            visibility = View.GONE
        }
        val listFrame = FrameLayout(this)
        listFrame.addView(listView, FrameLayout.LayoutParams(-1, -1))
        listFrame.addView(emptyText, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        root.addView(header)
        root.addView(searchBar)
        root.addView(listFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        adapter = object : BaseAdapter() {
            override fun getCount() = rows.size
            override fun getItem(p: Int) = rows[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getViewTypeCount() = 4
            override fun getItemViewType(p: Int) = rows[p].type
            override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
                val row = rows[p]
                val view = cv ?: buildRow(row.type)
                val h = view.tag as RowHolder
                for (i in h.cells.indices) {
                    val c = h.cells[i]
                    if (i
