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
        c.
