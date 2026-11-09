package com.haithem44.offlinetube

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.concurrent.Executors

data class Vid(val id: Long, val title: String, val duration: Long, val uri: Uri)
class Holder(
    val img: ImageView, val dur: TextView, val title: TextView,
    val track: LinearLayout, val red: View, val rest: View, val badge: TextView
)

class MainActivity : Activity() {
    private val videos = ArrayList<Vid>()
    private val pool = Executors.newFixedThreadPool(3)
    private val cache = HashMap<Long, Bitmap>()
    private lateinit var adapter: BaseAdapter

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
        val header = TextView(this).apply {
            text = "▶ OfflineTube"
            setTextColor(Color.WHITE)
            textSize = 22f
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val list = ListView(this).apply {
            divider = null
            setBackgroundColor(bg)
        }
        root.addView(header)
        root.addView(list, LinearLayout.LayoutParams(-1, -1))
        setContentView(root)

        adapter = object : BaseAdapter() {
            override fun getCount() = videos.size
            override fun getItem(p: Int) = videos[p]
            override fun getItemId(p: Int) = videos[p].id
            override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
                val row = cv ?: buildRow()
                val h = row.tag as Holder
                val v = videos[p]
                h.title.text = v.title
                h.dur.text = fmt(v.duration)
                h.img.tag = v.id

                val savedDur = Prog.dur(this@MainActivity, v.id)
                val d = if (savedDur > 0) savedDur else v.duration
                val pos = Prog.pos(this@MainActivity, v.id)
                if (pos <= 0 || d <= 0) {
                    h.track.visibility = View.GONE
                    h.badge.visibility = View.GONE
                } else {
                    val frac = (pos.toDouble() / d).coerceIn(0.0, 1.0)
                    h.track.visibility = View.VISIBLE
                    (h.red.layoutParams as LinearLayout.LayoutParams).weight = (frac * 1000).toFloat()
                    (h.rest.layoutParams as LinearLayout.LayoutParams).weight = (1000 - frac * 1000).toFloat()
                    h.track.requestLayout()
                    h.badge.visibility = if (frac > 0.95) View.VISIBLE else View.GONE
                }

                val b = cache[v.id]
                if (b != null) {
                    h.img.setImageBitmap(b)
                } else {
                    h.img.setImageDrawable(null)
                    loadThumb(v, h.img)
                }
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val v = videos[pos]
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                data = v.uri
                putExtra("id", v.id)
                putExtra("title", v.title)
            })
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

    private fun buildRow(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        val frame = FrameLayout(this)
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#222222"))
        }
        frame.addView(img, FrameLayout.LayoutParams(-1, dp(220)))

        val dur = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        val lp = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END)
        lp.setMargins(dp(8), dp(8), dp(8), dp(12))
        frame.addView(dur, lp)

        val badge = TextView(this).apply {
            text = "✓ شوهد"
            setTextColor(Color.WHITE)
            textSize = 11f
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(dp(6), dp(2), dp(6), dp(2))
            visibility = View.GONE
        }
        val blp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START)
        blp.setMargins(dp(8), dp(8), dp(8), dp(8))
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
        frame.addView(track, FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM))

        val title = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 2
            setPadding(dp(12), dp(10), dp(12), 0)
        }
        root.addView(frame, LinearLayout.LayoutParams(-1, -2))
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.tag = Holder(img, dur, title, track, red, rest, badge)
        return root
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
                runOnUiThread {
                    cache[v.id] = bmp
                    if (img.tag == v.id) img.setImageBitmap(bmp)
                }
            }
        }
    }

    private fun loadVideos() {
        videos.clear()
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
                videos.add(
                    Vid(
                        id,
                        (c.getString(1) ?: "video").substringBeforeLast('.'),
                        c.getLong(2),
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    )
                )
            }
        }
        adapter.notifyDataSetChanged()
        if (videos.isEmpty())
            Toast.makeText(this, "لا توجد فيديوهات على الهاتف", Toast.LENGTH_LONG).show()
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec)
        else String.format("%d:%02d", m, sec)
    }
}
