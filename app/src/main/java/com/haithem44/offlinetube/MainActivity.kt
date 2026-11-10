
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
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

// بيانات الفيديو
data class Vid(
    val id: Long,
    val title: String,
    val duration: Long,
    val uri: Uri
)

// تخزين الصور المصغرة
object ThumbnailCache {
    private val maxMemory =
        (Runtime.getRuntime().maxMemory() / 1024).toInt()

    private val memoryCache =
        object : LruCache<Long, Bitmap>(maxMemory / 8) {
            override fun sizeOf(key: Long, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }

    fun get(key: Long): Bitmap? = memoryCache.get(key)

    fun put(key: Long, bitmap: Bitmap) {
        if (get(key) == null) memoryCache.put(key, bitmap)
    }
}

class MainActivity : Activity() {

    private val allVideos = ArrayList<Vid>()
    private val filteredVideos = ArrayList<Vid>()

    private val pool = Executors.newFixedThreadPool(4)

    private lateinit var mainAdapter: VideoAdapter
    private lateinit var input: EditText
    private lateinit var emptyText: TextView
    private lateinit var mainRecyclerView: RecyclerView

    private fun dp(x: Int): Int =
        (x * resources.displayMetrics.density).toInt()

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

        // شعار التطبيق
        val banner = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true

            try {
                setImageResource(R.drawable.app_banner)
            } catch (e: Exception) {
                setBackgroundColor(Color.parseColor("#1F1F1F"))
            }
        }

        root.addView(banner)

        // الهيدر
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }

        val appTitle = TextView(this).apply {
            text = "TUBE TV PRO"
            setTextColor(Color.WHITE)
            textSize = 21f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val searchIcon = TextView(this).apply {
            text = "🔍"
            textSize = 23f
            setPadding(dp(12), 0, dp(8), 0)

            setOnClickListener {
                if (input.visibility == View.GONE) {
                    input.visibility = View.VISIBLE
                    input.requestFocus()
                } else {
                    input.setText("")
                    input.visibility = View.GONE
                }
            }
        }

        header.addView(
            appTitle,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        header.addView(searchIcon)
        root.addView(header)

        // البحث
        input = EditText(this).apply {
            hint = "ابحث عن فيديو..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 15f
            visibility = View.GONE
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = null

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int,
                    count: Int, after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?, start: Int,
                    before: Int, count: Int
                ) {
                    filterVideos(s.toString())
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        }

        root.addView(input)

        // عنوان الفيديوهات
        val sectionTitle = TextView(this).apply {
            text = "الفيديوهات"
            setTextColor(Color.WHITE)
            textSize = 19f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }

        root.addView(sectionTitle)

        // رسالة عدم وجود فيديوهات
        emptyText = TextView(this).apply {
            text = "لا توجد فيديوهات في جهازك"
            setTextColor(Color.GRAY)
            textSize = 16f
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, dp(30), 0, dp(20))
        }

        root.addView(emptyText)

        // قائمة مثل يوتيوب: فيديو تحت فيديو
        mainRecyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                LinearLayoutManager.VERTICAL,
                false
            )

            setPadding(dp(8), 0, dp(8), dp(16))
            clipToPadding = false
            setBackgroundColor(bg)
        }

        root.addView(
            mainRecyclerView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        mainAdapter = VideoAdapter(filteredVideos) { vid ->
            playVideo(vid)
        }

        mainRecyclerView.adapter = mainAdapter

        setContentView(root)

        // صلاحيات قراءة الفيديوهات
        val permission =
            if (Build.VERSION.SDK_INT >= 33) {
                Manifest.permission.READ_MEDIA_VIDEO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

        if (checkSelfPermission(permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            loadVideos()
        } else {
            requestPermissions(arrayOf(permission), 1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )

        if (requestCode == 1 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            loadVideos()
        } else {
            emptyText.text =
                "تم رفض الصلاحية. لا يمكن تحميل الفيديوهات."
            emptyText.visibility = View.VISIBLE
        }
    }

    // تشغيل الفيديو
    private fun playVideo(vid: Vid) {
        val intent = Intent(this, PlayerActivity::class.java)

        intent.putExtra("video_uri", vid.uri.toString())
        intent.putExtra("video_title", vid.title)

        startActivity(intent)
    }

    // تحميل جميع الفيديوهات
    private fun loadVideos() {

        pool.execute {

            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION
            )

            val cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )

            val loadedVideos = ArrayList<Vid>()

            cursor?.use {

                val idCol = it.getColumnIndexOrThrow(
                    MediaStore.Video.Media._ID
                )

                val titleCol = it.getColumnIndexOrThrow(
                    MediaStore.Video.Media.DISPLAY_NAME
                )

                val durationCol = it.getColumnIndexOrThrow(
                    MediaStore.Video.Media.DURATION
                )

                while (it.moveToNext()) {

                    val id = it.getLong(idCol)

                    val title =
                        it.getString(titleCol) ?: "فيديو"

                    val duration = it.getLong(durationCol)

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    loadedVideos.add(
                        Vid(id, title, duration, uri)
                    )
                }
            }

            runOnUiThread {

                allVideos.clear()
                allVideos.addAll(loadedVideos)

                filterVideos(input.text.toString())
            }
        }
    }

    // البحث عن الفيديوهات
    private fun filterVideos(query: String) {

        filteredVideos.clear()

        val result = if (query.isEmpty()) {
            allVideos
        } else {
            allVideos.filter {
                it.title.contains(query, ignoreCase = true)
            }
        }

        filteredVideos.addAll(result)

        mainAdapter.notifyDataSetChanged()

        emptyText.visibility =
            if (filteredVideos.isEmpty()) View.VISIBLE
            else View.GONE
    }

    // محول الفيديوهات بتصميم يوتيوب
    inner class VideoAdapter(
        private val videos: List<Vid>,
        private val onClick: (Vid) -> Unit
    ) : RecyclerView.Adapter<VideoAdapter.VH>() {

        inner class VH(
            val card: LinearLayout,
            val img: ImageView,
            val title: TextView,
            val durationText: TextView
        ) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): VH {

            val card = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL

                setBackgroundColor(Color.parseColor("#181818"))

                layoutParams =
                    RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(
                            dp(4), dp(6),
                            dp(4), dp(12)
                        )
                    }
            }

            // إطار صورة الفيديو
            val imageFrame = FrameLayout(parent.context)

            val img = ImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(195)
                )

                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.BLACK)
            }

            imageFrame.addView(img)

            // مدة الفيديو على الصورة
            val durationText = TextView(parent.context).apply {
                setTextColor(Color.WHITE)
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setBackgroundColor(Color.parseColor("#CC000000"))
                setPadding(dp(6), dp(3), dp(6), dp(3))

                layoutParams = FrameLayout.LayoutParams(
                    -2, -2,
                    Gravity.BOTTOM or Gravity.END
                ).apply {
                    setMargins(0, 0, dp(8), dp(8))
                }
            }

            imageFrame.addView(durationText)
            card.addView(imageFrame)

            // اسم الفيديو
            val titleText = TextView(parent.context).apply {
                setTextColor(Color.WHITE)
                textSize = 15f
                maxLines = 2
                setPadding(
                    dp(12), dp(10),
                    dp(12), dp(14)
                )
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            card.addView(titleText)

            return VH(
                card,
                img,
                titleText,
                durationText
            )
        }

        override fun onBindViewHolder(
            holder: VH,
            position: Int
        ) {

            val vid = videos[position]

            holder.title.text = vid.title

            holder.durationText.text =
                formatDuration(vid.duration)

            holder.card.setOnClickListener {
                onClick(vid)
            }

            val cached = ThumbnailCache.get(vid.id)

            if (cached != null) {

                holder.img.setImageBitmap(cached)

            } else {

                holder.img.setImageResource(
                    android.R.color.darker_gray
                )

                pool.execute {

                    try {

                        val bitmap =
                            if (Build.VERSION.SDK_INT >= 29) {
                                contentResolver.loadThumbnail(
                                    vid.uri,
                                    Size(512, 512),
                                    null
                                )
                            } else {
                                MediaStore.Video.Thumbnails.getThumbnail(
                                    contentResolver,
                                    vid.id,
                                    MediaStore.Video.Thumbnails.MINI_KIND,
                                    null
                                )
                            }

                        if (bitmap != null) {

                            ThumbnailCache.put(
                                vid.id,
                                bitmap
                            )

                            runOnUiThread {

                                val currentPosition =
                                    holder.bindingAdapterPosition

                                if (
                                    currentPosition !=
                                    RecyclerView.NO_POSITION &&
                                    videos[currentPosition].id == vid.id
                                ) {
                                    holder.img.setImageBitmap(bitmap)
                                }
                            }
                        }

                    } catch (e: Exception) {
                        // تجاهل أخطاء استخراج الصورة
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return videos.size
        }
    }

    // تنسيق مدة الفيديو
    private fun formatDuration(ms: Long): String {

        val seconds = (ms / 1000) % 60
        val minutes = (ms / 60000) % 60
        val hours = ms / 3600000

        return if (hours > 0) {
            String.format(
                "%d:%02d:%02d",
                hours, minutes, seconds
            )
        } else {
            String.format(
                "%02d:%02d",
                minutes, seconds
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pool.shutdown()
    }
}
