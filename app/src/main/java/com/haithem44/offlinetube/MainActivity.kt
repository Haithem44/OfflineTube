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
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

// --- Data Model ---
data class Vid(val id: Long, val title: String, val duration: Long, val uri: Uri, val isShort: Boolean)

// --- Thumbnail Cache ---
object ThumbnailCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val memoryCache = object : LruCache<Long, Bitmap>(cacheSize) {
        override fun sizeOf(key: Long, bitmap: Bitmap): Int = bitmap.byteCount / 1024
    }
    fun get(key: Long): Bitmap? = memoryCache.get(key)
    fun put(key: Long, bitmap: Bitmap) { if (get(key) == null) memoryCache.put(key, bitmap) }
}

class MainActivity : Activity() {
    private val allVideos = ArrayList<Vid>()
    private val filteredVideos = ArrayList<Vid>()
    private val shortsList = ArrayList<Vid>()
    private val pool = Executors.newFixedThreadPool(4)

    private lateinit var mainAdapter: VideoAdapter
    private lateinit var shortsAdapter: VideoAdapter
    private lateinit var input: EditText
    private lateinit var emptyText: TextView
    private lateinit var shortsTitle: TextView
    private lateinit var shortsRecyclerView: RecyclerView

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

        // 1. البانر
        val bannerImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            try { setImageResource(R.drawable.app_banner) } catch (e: Exception) { setBackgroundColor(Color.parseColor("#1F1F1F")) }
        }
        root.addView(bannerImageView)

        // 2. الهيدر والبحث
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(12), dp(8))
        }

        val subTitle = TextView(this).apply {
            setText("TUBE TV PRO")
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // تم إصلاح جملة if-else هنا لتجنب خطأ Missing '}'
        val searchIcon = TextView(this).apply {
            setText("🔍")
            textSize = 20f
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener {
                if (input.visibility == View.GONE) {
                    input.visibility = View.VISIBLE
                    input.requestFocus()
                } else {
                    input.visibility = View.GONE
                    input.setText("")
                }
            }
        }
        
        header.addView(subTitle, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(searchIcon)
        root.addView(header)

        input = EditText(this).apply {
            hint = "ابحث عن فيديو..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            visibility = View.GONE 
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = null
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filterVideos(s.toString()) }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(input)

        // 3. قسم الشورت (Shorts)
        shortsTitle = TextView(this).apply {
            setText("شورت (Shorts)")
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(16), dp(16), dp(16), dp(8))
            visibility = View.GONE
        }
        root.addView(shortsTitle)

        shortsRecyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            visibility = View.GONE
            setPadding(dp(8), 0, dp(8), dp(16))
        }
        root.addView(shortsRecyclerView, LinearLayout.LayoutParams(-1, dp(220)))

        // 4. القائمة الرئيسية (Grid)
        val gridTitle = TextView(this).apply {
            setText("جميع الفيديوهات")
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        root.addView(gridTitle)

        emptyText = TextView(this).apply {
            setText("لا توجد فيديوهات في جهازك")
            setTextColor(Color.GRAY)
            textSize = 16f
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, dp(32), 0, 0)
        }
        root.addView(emptyText)

        val mainRecyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2) // شبكة من عمودين
            setPadding(dp(8), 0, dp(8), dp(16))
        }
        root.addView(mainRecyclerView, LinearLayout.LayoutParams(-1, -1))

        // إعداد الـ Adapters
        mainAdapter = VideoAdapter(filteredVideos, false) { vid -> playVideo(vid) }
        shortsAdapter = VideoAdapter(shortsList, true) { vid -> playVideo(vid) }
        
        mainRecyclerView.adapter = mainAdapter
        shortsRecyclerView.adapter = shortsAdapter

        setContentView(root)

        // طلب الصلاحيات
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            loadVideos()
        } else {
            requestPermissions(arrayOf(permission), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            recreate()
        } else {
            emptyText.setText("تم رفض الصلاحية. لا يمكن تحميل الفيديوهات.")
            emptyText.visibility = View.VISIBLE
        }
    }

    private fun playVideo(vid: Vid) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("video_uri", vid.uri.toString())
        intent.putExtra("video_title", vid.title)
        startActivity(intent)
    }

    private fun loadVideos() {
        pool.execute {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
            )
            val cursor = contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val widthCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "فيديو"
                    val duration = it.getLong(durationCol)
                    val width = it.getInt(widthCol)
                    val height = it.getInt(heightCol)
                    
                    val isShort = height > width || duration < 60000
                    
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val vid = Vid(id, title, duration, uri, isShort)

                    if (isShort) shortsList.add(vid) else allVideos.add(vid)
                }
            }

            runOnUiThread {
                filteredVideos.clear()
                filteredVideos.addAll(allVideos)
                mainAdapter.notifyDataSetChanged()

                if (shortsList.isNotEmpty()) {
                    shortsTitle.visibility = View.VISIBLE
                    shortsRecyclerView.visibility = View.VISIBLE
                    shortsAdapter.notifyDataSetChanged()
                }
                
                emptyText.visibility = if (filteredVideos.isEmpty() && shortsList.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun filterVideos(query: String) {
        filteredVideos.clear()
        val listToSearch = if (query.isEmpty()) allVideos else allVideos.filter { it.title.contains(query, ignoreCase = true) }
        filteredVideos.addAll(listToSearch)
        mainAdapter.notifyDataSetChanged()
        emptyText.visibility = if (filteredVideos.isEmpty()) View.VISIBLE else View.GONE
    }

    // --- محول العرض (Adapter) ---
    inner class VideoAdapter(private val videos: List<Vid>, private val isShortRow: Boolean, private val onClick: (Vid) -> Unit) : RecyclerView.Adapter<VideoAdapter.VH>() {

        inner class VH(
            val card: LinearLayout,
            val img: ImageView,
            val title: TextView,
            val durationText: TextView
        ) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(4), dp(4), dp(4), dp(16)) }
                setBackgroundColor(Color.parseColor("#1A1A1A"))
                
                val img = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(if (isShortRow) dp(120) else ViewGroup.LayoutParams.MATCH_PARENT, if (isShortRow) dp(160) else dp(120))
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(Color.BLACK)
                }
                addView(img)

                val durText = TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    setBackgroundColor(Color.parseColor("#CC000000"))
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                    layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; topMargin = -dp(24); rightMargin = dp(4) }
                }
                addView(durText)

                val titleText = TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    maxLines = 2
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }
                addView(titleText)
                
                tag = arrayOf(img, titleText, durText)
            }
            
            val views = card.tag as Array<View>
            return VH(card, views[0] as ImageView, views[1] as TextView, views[2] as TextView)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val vid = videos[position]
            holder.title.text = vid.title
            holder.durationText.text = formatDuration(vid.duration)
            holder.card.setOnClickListener { onClick(vid) }

            val cached = ThumbnailCache.get(vid.id)
            if (cached != null) {
                holder.img.setImageBitmap(cached)
            } else {
                holder.img.setImageResource(android.R.color.darker_gray)
                pool.execute {
                    try {
                        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentResolver.loadThumbnail(vid.uri, Size(512, 512), null)
                        } else {
                            MediaStore.Video.Thumbnails.getThumbnail(contentResolver, vid.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                        }
                        if (bitmap != null) {
                            ThumbnailCache.put(vid.id, bitmap)
                            runOnUiThread { if (holder.adapterPosition == position) holder.img.setImageBitmap(bitmap) }
                        }
                    } catch (e: Exception) {}
                }
            }
        }

        override fun getItemCount() = videos.size
    }

    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60))
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds) else String.format("%02d:%02d", minutes, seconds)
    }
}
