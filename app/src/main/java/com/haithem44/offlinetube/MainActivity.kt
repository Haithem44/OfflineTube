package com.haithem44.offlinetube

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.concurrent.Executors

// --- Data Model ---
data class Vid(val id: Long, val title: String, val duration: Long, val uri: Uri)

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

// --- Main Activity ---
class MainActivity : Activity() {
    private val allVideos = ArrayList<Vid>()
    private val filteredVideos = ArrayList<Vid>()
    private val pool = Executors.newFixedThreadPool(3)

    private lateinit var adapter: ArrayAdapter<Vid>
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

        // 1. صورة البانر
        val bannerImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            try {
                setImageResource(R.drawable.app_banner)
            } catch (e: Exception) {
                setBackgroundColor(Color.parseColor("#1F1F1F"))
            }
        }
        root.addView(bannerImageView)

        // 2. شريط العنوان والبحث
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(12), dp(8))
            setBackgroundColor(bg)
        }

        val subTitle = TextView(this).apply {
            setText("TUBE TV PRO")
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val searchIcon = SearchIcon(this).apply { 
            isClickable = true 
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
        header.addView(searchIcon, LinearLayout.LayoutParams(dp(40), dp(40)))
        root.addView(header)

        // 3. مربع البحث
        input = EditText(this).apply {
            hint = "ابحث عن فيديو..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            visibility = View.GONE 
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = null
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filterVideos(s.toString())
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(input)

        // 4. نص يظهر عند عدم وجود فيديوهات
        emptyText = TextView(this).apply {
            setText("لا توجد فيديوهات في جهازك")
            setTextColor(Color.GRAY)
            textSize = 16f
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, dp(32), 0, 0)
        }
        root.addView(emptyText)

        // 5. قائمة الفيديوهات
        listView = ListView(this).apply {
            divider = null
            dividerHeight = 0
        }
        root.addView(listView, LinearLayout.LayoutParams(-1, -1))

        // إعداد المحول (Adapter) للقائمة
        adapter = object : ArrayAdapter<Vid>(this, android.R.layout.simple_list_item_1, filteredVideos) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v: TextView = if (convertView == null) {
                    TextView(this@MainActivity).apply {
                        setPadding(dp(16), dp(24), dp(16), dp(24))
                        setTextColor(Color.WHITE)
                        textSize = 16f
                    }
                } else {
                    convertView as TextView
                }
                
                val vid = getItem(position)
                v.setText(vid?.title ?: "بدون عنوان")
                
                // الانتقال إلى مشغل الفيديو عند الضغط
                v.setOnClickListener {
                    val intent = Intent(this@MainActivity, PlayerActivity::class.java)
                    intent.putExtra("video_uri", vid?.uri.toString())
                    intent.putExtra("video_title", vid?.title)
                    startActivity(intent)
                }
                return v
            }
        }
        listView.adapter = adapter

        // إظهار الشاشة
        setContentView(root)

        // طلب الصلاحيات
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            loadVideos()
        } else {
            requestPermissions(arrayOf(permission), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadVideos()
        } else {
            emptyText.setText("تم رفض الصلاحية. لا يمكن تحميل الفيديوهات.")
            emptyText.visibility = View.VISIBLE
        }
    }

    // دالة قراءة الفيديوهات من ذاكرة الهاتف
    private fun loadVideos() {
        pool.execute {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION
            )
            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            val cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "فيديو غير معروف"
                    val duration = it.getLong(durationCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    allVideos.add(Vid(id, title, duration, contentUri))
                }
            }

            runOnUiThread {
                filteredVideos.clear()
                filteredVideos.addAll(allVideos)
                adapter.notifyDataSetChanged()
                emptyText.visibility = if (filteredVideos.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    // دالة تصفية الفيديوهات عند البحث
    private fun filterVideos(query: String) {
        filteredVideos.clear()
        if (query.isEmpty()) {
            filteredVideos.addAll(allVideos)
        } else {
            val lowerQuery = query.lowercase()
            for (vid in allVideos) {
                if (vid.title.lowercase().contains(lowerQuery)) {
                    filteredVideos.add(vid)
                }
            }
        }
        adapter.notifyDataSetChanged()
        emptyText.visibility = if (filteredVideos.isEmpty()) View.VISIBLE else View.GONE
    }
}
