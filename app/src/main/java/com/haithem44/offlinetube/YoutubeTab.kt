package com.haithem44.offlinetube

import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.MediaFormat as NpFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class YoutubeTab(
    private val act: AppCompatActivity,
    private val folder: () -> String,
    private val onDone: () -> Unit,
    private val onOpen: (VideoItem) -> Unit
) {
    companion object {
        const val MAX_COMMENTS = 50
        private var inited = false
        private const val HOME = "https://m.youtube.com/"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
        private const val CHUNK = 4L * 1024 * 1024
        private const val THREADS = 6
        private const val MAX_CONCURRENT_DOWNLOADS = 5
        private const val BTN_TEXT = "⬇ تنزيل الفيديو + 50 تعليق"
    }

    private class Option(val label: String, val video: VideoStream, val audio: AudioStream?, val height: Int)
    private class Cancelled : Exception()
    private class Job(val id: String) { @Volatile var cancelled = false }

    class JobInfo(val id: String) {
        @Volatile var title: String = ""
        @Volatile var percent: Int = 0
        @Volatile var statusText: String = "في الانتظار..."
        @Volatile var cancelled: Boolean = false
    }

    val view = FrameLayout(act)
    private val web = WebView(act)
    private val btn = TextView(act)
    private val btnBg = GradientDrawable()
    private val status = TextView(act)
    private val bar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal)

    private val jobs = ConcurrentHashMap<String, Job>()
    private val jobInfos = ConcurrentHashMap<String, JobInfo>()

    private val preparePool = Executors.newFixedThreadPool(2)
    private val downloadPool = Executors.newFixedThreadPool(
        MAX_CONCURRENT_DOWNLOADS * (THREADS + 1)
    )
    private val downloadSlots = java.util.concurrent.Semaphore(MAX_CONCURRENT_DOWNLOADS)

    private var downloadsPanel: LinearLayout? = null
    private var downloadsListContainer: LinearLayout? = null
    private var downloadsFab: TextView? = null
    private val panelTicker = object : Runnable {
        override fun run() {
            refreshDownloadsPanel()
            if (downloadsPanel?.visibility == View.VISIBLE) ui.postDelayed(this, 500)
        }
    }

    private val ui = Handler(Looper.getMainLooper())
    private val dlPrefs = act.getSharedPreferences("yt_downloaded", Context.MODE_PRIVATE)
    private var loadedOnce = false
    private var currentVideoUrl: String? = null
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    private fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()

    init {
        if (!inited) { NewPipe.init(YtDownloader()); inited = true }

        view.setBackgroundColor(Color.parseColor("#0F0F0F"))
        view.layoutDirection = View.LAYOUT_DIRECTION_LTR
        view.isClickable = true

        val st = web.settings
        st.javaScriptEnabled = true
        st.domStorageEnabled = true
        web.setBackgroundColor(Color.parseColor("#0F0F0F"))
        CookieManager.getInstance().setAcceptCookie(true)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String?) { updateButton(url) }
            override fun doUpdateVisitedHistory(v: WebView, url: String?, isReload: Boolean) { updateButton(url) }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(v: View, cb: WebChromeClient.CustomViewCallback) {
                if (customView != null) { cb.onCustomViewHidden(); return }
                v.setBackgroundColor(Color.BLACK)
                customView = v; customCb = cb
                (act.window.decorView as FrameLayout).addView(v, FrameLayout.LayoutParams(-1, -1))
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                setImmersive(true)
            }
            override fun onHideCustomView() { hideCustom() }
        }
        view.addView(web, FrameLayout.LayoutParams(-1, -1))

        bar.max = 100; bar.visibility = View.GONE
        view.addView(bar, FrameLayout.LayoutParams(-1, dp(4), Gravity.TOP))

        status.setTextColor(Color.WHITE); status.textSize = 13f; status.gravity = Gravity.CENTER
        status.setPadding(dp(14), dp(8), dp(14), dp(8))
        status.background = GradientDrawable().apply { setColor(Color.parseColor("#CC000000")); cornerRadius = dp(12).toFloat() }
        status.visibility = View.GONE
        view.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(76); leftMargin = dp(16); rightMargin = dp(16)
        })

        btn.text = BTN_TEXT
        btn.setTextColor(Color.WHITE); btn.textSize = 15f; btn.gravity = Gravity.CENTER
        btn.typeface = Typeface.DEFAULT_BOLD
        btn.setPadding(dp(20), dp(12), dp(20), dp(12))
        btnBg.setColor(Color.parseColor("#FF0000")); btnBg.cornerRadius = dp(26).toFloat()
        btn.background = btnBg
        btn.elevation = dp(8).toFloat()
        btn.visibility = View.GONE
        btn.setOnClickListener { onButton() }
        btn.setOnLongClickListener {
            jobs.values.forEach { it.cancelled = true }
            jobInfos.values.forEach { it.cancelled = true }
            setStatus("تم الإيقاف — اضغط تنزيل مجدداً للاستئناف", true); true
        }
        view.addView(btn, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(16) })

        downloadsFab = TextView(act).apply {
            text = "☰ التنزيلات (0)"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply { setColor(Color.parseColor("#CC1F1F1F")); cornerRadius = dp(18).toFloat() }
            elevation = dp(6).toFloat()
            visibility = View.GONE
            setOnClickListener { toggleDownloadsPanel() }
        }
        view.addView(downloadsFab, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.RIGHT).apply {
            topMargin = dp(10); rightMargin = dp(10)
        })
    }

    fun onShown() {
        if (!loadedOnce) { loadedOnce = true; web.loadUrl(HOME) } else web.onResume()
        refreshBtn()
    }

    fun onHidden() {
        web.evaluateJavascript("document.querySelectorAll('video').forEach(function(v){v.pause()})", null)
        hideCustom()
        web.onPause()
    }

    fun handleBack(): Boolean {
        if (downloadsPanel?.visibility == View.VISIBLE) { hideDownloadsPanel(); return true }
        if (customView != null) { hideCustom(); return true }
        if (web.canGoBack()) { web.goBack(); return true }
        return false
    }

    fun destroy() {
        jobs.values.forEach { it.cancelled = true }
        preparePool.shutdownNow()
        downloadPool.shutdownNow()
        hideCustom()
        ui.removeCallbacks(panelTicker)
        try { web.destroy() } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun setImmersive(on: Boolean) {
        act.window.decorView.systemUiVisibility = if (on) {
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        } else View.SYSTEM_UI_FLAG_VISIBLE
        if (on) act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hideCustom() {
        val v = customView ?: return
        (act.window.decorView as FrameLayout).removeView(v)
        customView = null
        customCb?.onCustomViewHidden(); customCb = null
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        setImmersive(false)
    }

    private fun videoUrlOf(u: String?): String? {
        if (u == null || !(u.contains("youtube.com") || u.contains("youtu.be"))) return null
        val id = Regex("[?&]v=([\\w-]{11})").find(u)?.groupValues?.get(1)
            ?: Regex("/shorts/([\\w-]{11})").find(u)?.groupValues?.get(1)
            ?: Regex("youtu\\.be/([\\w-]{11})").find(u)?.groupValues?.get(1)
        return id?.let { "https://www.youtube.com/watch?v=$it" }
    }

    private fun vidOf(url: String?): String? =
        url?.let { Regex("v=([\\w-]{11})").find(it)?.groupValues?.get(1) }

    private fun downloadedItem(vid: String): VideoItem? {
        val id = dlPrefs.getLong(vid, -1L)
        if (id < 0) return null
        val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
        var item: VideoItem? = null
        try {
            act.contentResolver.query(
                uri,
                arrayOf(MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) item = VideoItem(id, (c.getString(0) ?: "video").substringBeforeLast('.'), c.getLong(1), uri)
            }
        } catch (_: Exception) {}
        if (item == null) dlPrefs.edit().remove(vid).apply()
        return item
    }

    private fun updateButton(url: String?) { currentVideoUrl = videoUrlOf(url); refreshBtn() }

    private fun refreshBtn() = ui.post {
        val n = jobs.size
        val vid = vidOf(currentVideoUrl)
        val done = if (vid != null && !jobs.containsKey(vid)) downloadedItem(vid) else null
        btn.text = when {
            done != null -> "✓ تم التنزيل — اضغط للخيارات"
            n > 0 -> "⬇ تنزيل ($n قيد التنفيذ)"
            else -> BTN_TEXT
        }
        btnBg.setColor(Color.parseColor(if (done != null) "#2E7D32" else "#FF0000"))
        btn.visibility = if (currentVideoUrl != null || n > 0) View.VISIBLE else View.GONE
        bar.visibility = if (n > 0) View.VISIBLE else View.GONE
        view.keepScreenOn = n > 0

        downloadsFab?.let { fab ->
            if (n > 0) {
                fab.text = "☰ التنزيلات ($n)"
                fab.visibility = View.VISIBLE
            } else {
                fab.visibility = View.GONE
                hideDownloadsPanel()
            }
        }
    }

    private fun endJob(job: Job) {
        jobs.remove(job.id)
        jobInfos.remove(job.id)
        refreshBtn()
        refreshDownloadsPanel()
    }

    private fun setStatus(s: String, autoHide: Boolean = false) {
        ui.post {
            status.setOnClickListener(null); status.isClickable = false
            status.text = s; status.visibility = View.VISIBLE
            if (autoHide) ui.postDelayed({ status.visibility = View.GONE }, 4000)
        }
    }

    private fun fail(e: Throwable) {
        if (e is Cancelled) setStatus("تم الإيقاف — يمكنك الاستئناف بالضغط على تنزيل", true)
        else setStatus("✗ فشل: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun onButton() {
        val url = currentVideoUrl
        if (url == null) { Toast.makeText(act, "افتح فيديو أولاً", Toast.LENGTH_SHORT).show(); return }
        if (folder().isEmpty()) {
            Toast.makeText(act, "اختر مجلد الفيديوهات أولاً (زر المجلد)", Toast.LENGTH_LONG).show(); return
        }
        val vid = vidOf(url) ?: return
        if (jobs.containsKey(vid)) { Toast.makeText(act, "هذا الفيديو قيد التنزيل بالفعل", Toast.LENGTH_SHORT).show(); return }

        val done = downloadedItem(vid)
        if (done != null) {
            AlertDialog.Builder(act)
                .setTitle("تم تنزيله سابقاً")
                .setMessage("«${done.title.take(40)}» موجود في مكتبتك.")
                .setPositiveButton("تشغيل") { _, _ -> onOpen(done) }
                .setNegativeButton("إعادة التنزيل") { _, _ -> startDownload(url, vid) }
                .setNeutralButton("إلغاء", null)
                .show()
            return
        }
        startDownload(url, vid)
    }

    private fun startDownload(url: String, vid: String) {
        if (jobs.isEmpty()) ui.post { bar.progress = 0 }
        val job = Job(vid); jobs[vid] = job

        jobInfos[vid] = JobInfo(vid).apply {
            title = "جارٍ جلب المعلومات..."
            percent = 0
            statusText = "جارٍ جلب المعلومات..."
        }

        refreshBtn()
        Toast.makeText(act, "أُضيف إلى التنزيلات", Toast.LENGTH_SHORT).show()

        preparePool.execute {
            var handedOff = false
            try {
                setStatus("جارٍ جلب معلومات الفيديو...")
                val info = StreamInfo.getInfo(ServiceList.YouTube, url)
                val options = buildOptions(info)
                if (options.isEmpty()) throw IllegalStateException("لا توجد صيغة قابلة للتنزيل لهذا الفيديو")
                handedOff = true
                if (options.size == 1) scheduleDownload(job, info, url, options[0])
                else ui.post { askQuality(job, info, url, options) }
            } catch (e: Exception) {
                fail(e); endJob(job)
            }
        }
    }

    private fun scheduleDownload(job: Job, info: StreamInfo, url: String, opt: Option) {
        jobInfos[job.id]?.apply {
            title = info.name
            statusText = "بانتظار دورها..."
        }
        refreshDownloadsPanel()

        downloadPool.execute {
            downloadSlots.acquire()
            try {
                if (job.cancelled) throw Cancelled()
                runDownload(job, info, url, opt)
            } catch (e: Throwable) {
                fail(e)
            } finally {
                downloadSlots.release()
                endJob(job)
            }
        }
    }

    private fun heightOf(res: String?): Int = Regex("\\d+").find(res ?: "")?.value?.toIntOrNull() ?: 0

    private fun buildOptions(info: StreamInfo): List<Option> {
        val map = LinkedHashMap<Int, Option>()
        for (v in info.videoStreams.filter { it.isUrl }) {
            val h = heightOf(v.resolution)
            if (!map.containsKey(h)) map[h] = Option(h.toString() + "p", v, null, h)
        }
        val audio = info.audioStreams
            .filter { it.isUrl && it.format == NpFormat.M4A }
            .maxByOrNull { it.averageBitrate }
            ?: info.audioStreams.filter { it.isUrl }.maxByOrNull { it.averageBitrate }
        if (audio != null) {
            for (v in info.videoOnlyStreams.filter { it.isUrl && it.format == NpFormat.MPEG_4 }) {
                val h = heightOf(v.resolution)
                if (h > 0 && !map.containsKey(h)) map[h] = Option(h.toString() + "p", v, audio, h)
            }
        }
        return map.values.sortedByDescending { it.height }
    }

    private fun askQuality(job: Job, info: StreamInfo, url: String, options: List<Option>) {
        val labels = options.map { it.label }.toTypedArray()
        AlertDialog.Builder(act)
            .setTitle("اختر الجودة")
            .setItems(labels) { _, i -> scheduleDownload(job, info, url, options[i]) }
            .setOnCancelListener { endJob(job) }
            .show()
    }

    private fun runDownload(job: Job, info: StreamInfo, url: String, opt: Option) {
        val title = info.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)
        val tmpDir = File(act.filesDir, "yt_dl").apply { mkdirs() }
        val key = "${job.id}_${opt.height}"
        val vTmp = File(tmpDir, "${key}_v.part")
        val aTmp = File(tmpDir, "${key}_a.part")
        val outTmp = File(tmpDir, "${key}_out.mp4")
        val tag = title.take(18)
        var success = false

        jobInfos[job.id]?.apply {
            this.title = title
            statusText = "جارٍ التنزيل..."
        }

        try {
            val finalFile: File
            val audioStream = opt.audio
            if (audioStream == null) {
                fetch(job, opt.video.content, vTmp, tag, 0, 80)
                finalFile = vTmp
            } else {
                fetch(job, opt.video.content, vTmp, tag, 0, 70)
                fetch(job, audioStream.content, aTmp, "$tag (صوت)", 70, 80)
                setStatus("جارٍ دمج الصوت والصورة...")
                jobInfos[job.id]?.statusText = "جارٍ الدمج..."
                mux(vTmp, aTmp, outTmp, job)
                finalFile = outTmp
            }
            ui.post { bar.progress = 85 }
            setStatus("جارٍ حفظ الفيديو في الهاتف...")
            jobInfos[job.id]?.statusText = "جارٍ الحفظ..."
            val saved = saveToStore(title, finalFile)
            success = true
            dlPrefs.edit().putLong(job.id, saved.first).apply()

            setStatus("جارٍ جلب التعليقات (50)...")
            jobInfos[job.id]?.statusText = "جارٍ جلب التعليقات..."
            ui.post { bar.progress = 90 }
            var commentsCount = 0
            val savedCommentsOk = try {
                val comments = fetchComments(url)
                commentsCount = comments.size
                saveComments(saved.first, title, comments)
            } catch (e: Exception) { false }

            val item = VideoItem(saved.first, title, info.duration * 1000L, saved.second)
            ui.post {
                bar.progress = 100
                status.text = if (savedCommentsOk && commentsCount > 0)
                    "✓ تم تنزيل «$tag» و $commentsCount تعليق — اضغط للتشغيل ▶"
                else "✓ تم تنزيل «$tag» — اضغط للتشغيل ▶ (تعذّر جلب التعليقات)"
                status.visibility = View.VISIBLE
                status.setOnClickListener {
                    status.visibility = View.GONE; status.setOnClickListener(null); status.isClickable = false
                    onOpen(item)
                }
                onDone()
            }
        } catch (e: Throwable) {
            fail(e)
        } finally {
            if (success) listOf(vTmp, aTmp, outTmp).forEach { it.delete(); File(it.path + ".meta").delete() }
            jobInfos.remove(job.id)
        }
    }

    private fun report(job: Job, label: String, pos: Long, total: Long, from: Int, to: Int) {
        val frac = if (total > 0) pos.toDouble() / total else 0.0
        val pct = from + ((to - from) * frac).toInt()
        ui.post { bar.progress = pct }
        val mb = 1024.0 * 1024.0
        setStatus(String.format(Locale.US, "%s %d%% — %.1f / %.1f MB", label, (frac * 100).toInt(), pos / mb, maxOf(total, 0L) / mb))
        jobInfos[job.id]?.apply {
            percent = pct
            statusText = String.format(Locale.US, "%s %.1f/%.1fMB", label, pos / mb, maxOf(total, 0L) / mb)
        }
    }

    private fun fetch(job: Job, link: String, dest: File, label: String, from: Int, to: Int) {
        val c0 = URL(link).openConnection() as HttpURLConnection
        val total: Long
        try {
            c0.setRequestProperty("User-Agent", UA)
            c0.setRequestProperty("Range", "bytes=0-0")
            c0.setRequestProperty("Accept-Encoding", "identity")
            c0.connectTimeout = 20000
            c0.readTimeout = 20000
            total = c0.getHeaderField("Content-Range")?.substringAfter('/')?.trim()?.toLongOrNull()
                ?: throw IOException("تعذّر معرفة حجم الملف")
        } finally { c0.disconnect() }

        val meta = File(dest.path + ".meta")
        val n = ((total + CHUNK - 1) / CHUNK).toInt()
        fun chunkLen(i: Int): Long = minOf(CHUNK, total - i * CHUNK)
        val done = java.util.Collections.synchronizedSet(HashSet<Int>())
        val resumable = dest.exists() && dest.length() == total && meta.exists() &&
                meta.readLines().firstOrNull()?.toLongOrNull() == total
        if (resumable) {
            meta.readLines().getOrNull(1)?.split(",")?.mapNotNullTo(done) { it.toIntOrNull() }
        } else {
            RandomAccessFile(dest, "rw").use { it.setLength(total) }
            meta.delete()
        }
        var initial = 0L
        for (i in done.toList()) initial += chunkLen(i)
        val bytes = AtomicLong(initial)
        val next = AtomicInteger(0)
        val error = AtomicReference<Throwable?>(null)
        val metaLock = Any()

        fun persistMeta() {
            synchronized(metaLock) {
                try {
                    meta.writeText("$total\n" + done.toList().sorted().joinToString(","))
                } catch (_: Exception) {}
            }
        }

        val workers = (1..THREADS).map {
            Thread {
                try {
                    RandomAccessFile(dest, "rw").use { raf ->
                        val buf = ByteArray(128 * 1024)
                        while (!job.cancelled && error.get() == null) {
                            val i = next.getAndIncrement(); if (i >= n) break
                            if (done.contains(i)) continue
                            val s = i * CHUNK; val len = chunkLen(i)
                            var off = 0L; var attempt = 0
                            while (off < len) {
                                try {
                                    val conn = URL(link).openConnection() as HttpURLConnection
                                    conn.setRequestProperty("User-Agent", UA)
                                    conn.setRequestProperty("Accept-Encoding", "identity")
                                    conn.setRequestProperty("Range", "bytes=${s + off}-${s + len - 1}")
                                    conn.connectTimeout = 20000; conn.readTimeout = 20000
                                    if (conn.responseCode != 206) throw IOException("HTTP ${conn.responseCode}")
                                    conn.inputStream.use { inp ->
                                        while (true) {
                                            if (job.cancelled) throw Cancelled()
                                            val r = inp.read(buf); if (r < 0) break
                                            raf.seek(s + off); raf.write(buf, 0, r)
                                            off += r; bytes.addAndGet(r.toLong())
                                        }
                                    }
                                    if (off < len) throw IOException("short")
                                } catch (e: IOException) {
                                    if (++attempt > 4) throw e
                                    Thread.sleep(800)
                                }
                            }
                            synchronized(done) { done.add(i) }
                            persistMeta()
                        }
                    }
                } catch (e: Cancelled) {
                } catch (e: Throwable) { error.compareAndSet(null, e) }
            }.also { it.start() }
        }

        while (workers.any { it.isAlive }) {
            report(job, label, bytes.get(), total, from, to)
            try { Thread.sleep(400) } catch (_: InterruptedException) { job.cancelled = true }
        }
        workers.forEach { it.join() }
        if (job.cancelled) throw Cancelled()
        error.get()?.let { throw it }
        report(job, label, total, total, from, to)
    }

    private fun mux(videoF: File, audioF: File, out: File, job: Job) {
        val vEx = MediaExtractor(); val aEx = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            vEx.setDataSource(videoF.absolutePath); aEx.setDataSource(audioF.absolutePath)
            fun pick(ex: MediaExtractor, prefix: String): Int {
                for (i in 0 until ex.trackCount) {
                    if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true) return i
                }
                throw IllegalStateException("لا يوجد مسار $prefix")
            }
            val vt = pick(vEx, "video/"); val at = pick(aEx, "audio/")
            vEx.selectTrack(vt); aEx.selectTrack(at)
            val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val vo = m.addTrack(vEx.getTrackFormat(vt)); val ao = m.addTrack(aEx.getTrackFormat(at))
            m.start()
            val buf = java.nio.ByteBuffer.allocate(4 * 1024 * 1024)
            val bi = MediaCodec.BufferInfo()
            while (true) {
                if (job.cancelled) throw Cancelled()
                val vTime = vEx.sampleTime; val aTime = aEx.sampleTime
                if (vTime < 0 && aTime < 0) break
                val useV = aTime < 0 || (vTime in 0..aTime)
                val ex = if (useV) vEx else aEx
                val tr = if (useV) vo else ao
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) { ex.advance(); continue }
                bi.set(0, size, ex.sampleTime, ex.sampleFlags)
                m.writeSampleData(tr, buf, bi)
                ex.advance()
            }
            m.stop()
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            try { vEx.release() } catch (_: Exception) {}
            try { aEx.release() } catch (_: Exception) {}
        }
    }

    private fun saveToStore(title: String, src: File): Pair<Long, Uri> {
        val resolver = act.contentResolver
        val folderPath = folder()
        val base = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')

        if (Build.VERSION.SDK_INT >= 29) {
            fun insert(r: String): Uri? {
                val cv = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "$title.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, r)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                return resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)
            }
            val rel = if (folderPath.startsWith(base))
                folderPath.removePrefix(base).trim('/') + "/" else "Movies/OfflineTube/"
            var uri: Uri? = try { insert(rel) } catch (e: Exception) { null }
            if (uri == null) uri = insert("Movies/OfflineTube/")
            val target = uri ?: throw IllegalStateException("تعذّر إنشاء ملف الحفظ")
            try {
                val o = resolver.openOutputStream(target) ?: throw IllegalStateException("تعذّر فتح ملف الحفظ")
                o.use { os -> src.inputStream().use { it.copyTo(os, 256 * 1024) } }
            } catch (e: Exception) {
                resolver.delete(target, null, null); throw e
            }
            val cv = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(target, cv, null, null)
            return Pair(ContentUris.parseId(target), target)
        }

        var dest = File(folderPath, "$title.mp4")
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest = File(folderPath, title + "_" + System.currentTimeMillis() + ".mp4")
        src.inputStream().use { i -> FileOutputStream(dest).use { o -> i.copyTo(o, 256 * 1024) } }
        val latch = CountDownLatch(1)
        var scanned: Uri? = null
        MediaScannerConnection.scanFile(act, arrayOf(dest.absolutePath), null) { _, u -> scanned = u; latch.countDown() }
        latch.await(5, TimeUnit.SECONDS)
        val u = scanned
        val id = if (u != null) { try { ContentUris.parseId(u) } catch (_: Exception) { 0L } } else 0L
        val realId = if (id > 0) id else (dest.absolutePath.hashCode().toLong() and 0x7FFFFFFF)
        return Pair(realId, u ?: Uri.fromFile(dest))
    }

    private fun fetchComments(url: String): List<String> {
        val out = ArrayList<String>()

        fun add(items: List<CommentsInfoItem>) {
            for (c in items) {
                if (out.size >= MAX_COMMENTS) return
                val text = c.commentText?.content?.trim().orEmpty()
                if (text.isEmpty()) continue
                val author = c.uploaderName?.ifBlank { "مجهول" } ?: "مجهول"
                out.add("$author (👍 ${c.likeCount}):\n$text")
            }
        }

        try {
            val ci = CommentsInfo.getInfo(url)
            add(ci.relatedItems)
            var page = ci.nextPage
            while (out.size < MAX_COMMENTS && page != null) {
                val more = CommentsInfo.getMoreItems(ServiceList.YouTube, url, page)
                add(more.items)
                page = more.nextPage
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return out
    }

    private fun saveComments(mediaId: Long, title: String, comments: List<String>): Boolean = try {
        val dir = File(act.filesDir, "comments").apply { mkdirs() }
        File(dir, "$mediaId.txt").writeText(
            title + "\n\n" + comments.joinToString("\n\n──────────\n\n")
        )
        true
    } catch (_: Exception) { false }

    // ============ 🚀 لوحة إدارة التنزيلات ============

    private fun toggleDownloadsPanel() {
        if (downloadsPanel?.visibility == View.VISIBLE) hideDownloadsPanel()
        else showDownloadsPanel()
    }

    private fun showDownloadsPanel() {
        if (jobs.isEmpty()) {
            Toast.makeText(act, "لا توجد تنزيلات نشطة", Toast.LENGTH_SHORT).show()
            return
        }
        if (downloadsPanel == null) buildDownloadsPanel()
        refreshDownloadsPanel()
        downloadsPanel?.visibility = View.VISIBLE
        ui.removeCallbacks(panelTicker)
        ui.postDelayed(panelTicker, 500)
    }

    private fun hideDownloadsPanel() {
        downloadsPanel?.visibility = View.GONE
        ui.removeCallbacks(panelTicker)
    }

    private fun buildDownloadsPanel() {
        val panel = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F00F0F0F"))
            isClickable = true
            visibility = View.GONE
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val header = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(8))
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }
        val title = TextView(act).apply {
            text = "التنزيلات النشطة"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))

        val stopAll = TextView(act).apply {
            text = "إيقاف الكل"
            setTextColor(Color.parseColor("#FF4D4D"))
            textSize = 13f
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener {
                jobs.values.forEach { it.cancelled = true }
                jobInfos.values.forEach { it.cancelled = true }
                setStatus("تم إيقاف كل التنزيلات", true)
                refreshDownloadsPanel()
            }
        }
        header.addView(stopAll)

        val closeBtn = TextView(act).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnClickListener { hideDownloadsPanel() }
        }
        header.addView(closeBtn)

        panel.addView(header, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(act)
        val list = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        scroll.addView(list)
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        downloadsListContainer = list
        downloadsPanel = panel
        view.addView(panel, FrameLayout.LayoutParams(-1, -1))
    }

    private fun refreshDownloadsPanel() {
        val container = downloadsListContainer ?: return
        container.removeAllViews()

        val active = jobInfos.values.filter { jobs.containsKey(it.id) }
        if (active.isEmpty()) {
            val empty = TextView(act).apply {
                text = "لا توجد تنزيلات نشطة الآن"
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(40), dp(20), dp(40))
            }
            container.addView(empty)
            return
        }

        for (info in active) {
            val row = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setBackgroundColor(Color.parseColor("#161616"))
            }

            val top = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val nameTv = TextView(act).apply {
                text = info.title.ifBlank { info.id }
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            top.addView(nameTv, LinearLayout.LayoutParams(0, -2, 1f))

            val cancelBtn = TextView(act).apply {
                text = "✕"
                setTextColor(Color.parseColor("#FF4D4D"))
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#33FF0000"))
                    cornerRadius = dp(12).toFloat()
                }
                setOnClickListener {
                    jobs[info.id]?.cancelled = true
                    info.cancelled = true
                    info.statusText = "تم الإيقاف"
                    setStatus("تم إيقاف تنزيل: ${info.title.take(20)}", true)
                    refreshDownloadsPanel()
                }
            }
            top.addView(cancelBtn)
            row.addView(top, LinearLayout.LayoutParams(-1, -2))

            val progressBar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = info.percent
            }
            row.addView(progressBar, LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(6) })

            val statusTv = TextView(act).apply {
                text = "${info.percent}% — ${info.statusText}"
                setTextColor(Color.parseColor("#BBBBBB"))
                textSize = 12f
                setPadding(0, dp(4), 0, 0)
            }
            row.addView(statusTv)

            container.addView(row, LinearLayout.LayoutParams(-1, -2))

            val divider = View(act).apply { setBackgroundColor(Color.parseColor("#1A1A1A")) }
            container.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        }
    }
}
