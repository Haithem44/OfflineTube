
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

data class Vid(
    val id: Long,
    val title: String,
    val duration: Long,
    val uri: Uri
)

object ThumbnailCache {
    private val maxMemory =
        (Runtime.getRuntime().maxMemory() / 1024).toInt()

    private val memoryCache =
        object : LruCache<Long, Bitmap>(maxMemory / 8) {
            override fun sizeOf(key: Long, bitmap: Bitmap) =
                bitmap.byteCount / 1024
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

    private fun dp(x: Int) =
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

        header.addView(appTitle,
            LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(searchIcon)
        root.addView(header)

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

        val sectionTitle = TextView(this).apply {
            text = "الفيديوهات"
            setTextColor(Color.WHITE)
            textSize = 19f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        root.addView(sectionTitle)

        emptyText = TextView(this).apply {
            text = "لا توجد فيديوهات في جهازك"
            setTextColor(Color.GRAY)
            textSize = 16f
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, dp(30), 0, dp(20))
        }
        root.addView(emptyText)

        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                LinearLayoutManager.VERTICAL,
                false
            )
            setPadding(dp(4), 0, dp(4), dp(16))
            clipToPadding = false
            setBackgroundColor(bg)
        }

        root.addView(recycler,
            LinearLayout.LayoutParams(-1, 0, 1f))

        mainAdapter = VideoAdapter(filteredVideos) { vid ->
            playVideo(vid)
        }

        recycler.adapter = mainAdapter
        setContentView(root)

        val permission = if (Build.VERSION.SDK_INT >= 33) {
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
            emptyText.text = "تم رفض صلاحية قراءة الفيديوهات"
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
                MediaStore.Video.Media.DURATION
            )

            val cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )

            val loaded = ArrayList<Vid>()

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
                    val title = it.getString(titleCol) ?: "فيديو"
                    val duration = it.getLong(durationCol)

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    loaded.add(Vid(id, title, duration, uri))
                }
            }

            runOnUiThread {
                allVideos.clear()
                allVideos.addAll(loaded)
                filterVideos(input.text.toString())
            }
        }
    }

    private fun filterVideos(query: String) {
        filteredVideos.clear()

        filteredVideos.addAll(
            if (query.isEmpty()) allVideos
            else allVideos.filter {
                it.title.contains(query, ignoreCase = true)
            }
        )

        mainAdapter.notifyDataSetChanged()

        emptyText.visibility =
            if (filteredVideos.isEmpty()) View.VISIBLE
            else View.GONE
    }

    // عرض صورة كبيرة بدون كتابة تحتها
    inner class VideoAdapter(
        private val videos: List<Vid>,
        private val onClick: (Vid) -> Unit
    ) : RecyclerView.Adapter<VideoAdapter.VH>() {

        inner class VH(
            val image: ImageView,
            val duration: TextView
        ) : RecyclerView.ViewHolder(
            FrameLayout(this@MainActivity)
        )

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): VH {
            val frame = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(220)
                ).apply {
                    setMargins(dp(4), dp(5), dp(4), dp(12))
                }
                setBackgroundColor(Color.BLACK)
                isClickable = true
            }

            val image = ImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(-1, -1)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#222222"))
            }
            frame.addView(image)

            val duration = TextView(parent.context).apply {
                setTextColor(Color.WHITE)
                textSize = 12f
                setBackgroundColor(Color.parseColor("#CC000000"))
                setPadding(dp(6), dp(3), dp(6), dp(3))
                layoutParams = FrameLayout.LayoutParams(
                    -2, -2,
                    Gravity.BOTTOM or Gravity.END
                ).apply {
                    setMargins(0, 0, dp(8), dp(8))
                }
            }
            frame.addView(duration)

            return VH(image, duration).also {
                it.itemView.layoutParams = frame.layoutParams
                // الاحتفاظ بالإطار الذي يحتوي الصورة والمدة
                it.itemView.let { _ -> }
            }.let {
                FrameVH(frame, it.image, it.duration)
            }
        }

        inner class FrameVH(
            val frame: FrameLayout,
            val image: ImageView,
            val duration: TextView
        ) : VH(image, duration)

        override fun onBindViewHolder(holder: VH, position: Int) {
            val vid = videos[position]
            holder.duration.text = formatDuration(vid.duration)

            holder.itemView.setOnClickListener {
                onClick(vid)
            }

            val cached = ThumbnailCache.get(vid.id)

            if (cached != null) {
                holder.image.setImageBitmap(cached)
            } else {
                holder.image.setImageResource(
                    android.R.color.darker_gray
                )

                pool.execute {
                    try {
                        val bitmap = if (Build.VERSION.SDK_INT >= 29) {
                            contentResolver.loadThumbnail(
                                vid.uri, Size(640, 360), null
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
                            ThumbnailCache.put(vid.id, bitmap)

                            runOnUiThread {
                                val p = holder.bindingAdapterPosition
                                if (p != RecyclerView.NO_POSITION &&
                                    videos[p].id == vid.id
                                ) {
                                    holder.image.setImageBitmap(bitmap)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        override fun getItemCount() = videos.size
    }

    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 60000) % 60
        val hours = ms / 3600000

        return if (hours > 0)
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        else
            String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        pool.shutdown()
    }
}
