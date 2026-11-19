package com.haithem44.offlinetube

import android.Manifest
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.util.Rational
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.nio.ByteBuffer
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import org.schabi.newpipe.extractor.MediaFormat as NpFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.RandomAccessFile
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import android.content.ContentValues
import android.media.MediaScannerConnection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo

data class VideoItem(val id: Long, val title: String, val duration: Long, val uri: Uri)

class IconDrawable(private val kind: Int, initial: Int) : Drawable() {
    companion object {
        const val HOME = 0; const val SEARCH = 1; const val PLAY = 2; const val REFRESH = 3
        const val FOLDER = 4; const val TRASH = 5; const val BACK = 6; const val LOCK = 7
        const val UNLOCK = 8; const val PAUSE = 9; const val PLAY_FILLED = 10; const val EXPAND = 11
        const val ROTATE = 12; const val PIP = 13; const val COMPRESS = 14; const val COMMENT = 15
        const val DOWNLOAD = 16
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND; color = initial
    }

    fun setIconColor(c: Int) { paint.color = c; invalidateSelf() }

    override fun draw(canvas: Canvas) {
        val b = bounds; val size = minOf(b.width(), b.height()).toFloat()
        if (size <= 0f) return
        val s = size / 24f
        canvas.save()
        canvas.translate(b.left + (b.width() - size) / 2f, b.top + (b.height() - size) / 2f)
        canvas.scale(s, s); paint.strokeWidth = 2f; paint.style = Paint.Style.STROKE
        val p = Path()
        when (kind) {
            HOME -> {
                p.moveTo(3f, 11f); p.lineTo(12f, 3f); p.lineTo(21f, 11f)
                p.moveTo(5f, 10f); p.lineTo(5f, 20f); p.lineTo(10f, 20f)
                p.lineTo(10f, 14f); p.lineTo(14f, 14f); p.lineTo(14f, 20f)
                p.lineTo(19f, 20f); p.lineTo(19f, 10f)
            }
            SEARCH -> { canvas.drawCircle(10.5f, 10.5f, 6.5f, paint); p.moveTo(15.3f, 15.3f); p.lineTo(21f, 21f) }
            PLAY -> { p.moveTo(8f, 5f); p.lineTo(19f, 12f); p.lineTo(8f, 19f); p.close() }
            REFRESH -> { canvas.drawArc(RectF(4f, 4f, 20f, 20f), 25f, 300f, false, paint); p.moveTo(18.5f, 2.5f); p.lineTo(18.5f, 7.5f); p.lineTo(13.5f, 7.5f) }
            FOLDER -> { p.moveTo(3f, 6f); p.lineTo(9f, 6f); p.lineTo(11f, 8f); p.lineTo(21f, 8f); p.lineTo(21f, 19f); p.lineTo(3f, 19f); p.close() }
            TRASH -> {
                p.moveTo(3.5f, 6f); p.lineTo(20.5f, 6f)
                p.moveTo(9f, 6f); p.lineTo(9f, 4.5f); p.quadTo(9f, 3.5f, 10f, 3.5f); p.lineTo(14f, 3.5f); p.quadTo(15f, 3.5f, 15f, 4.5f); p.lineTo(15f, 6f)
                p.moveTo(5.5f, 6f); p.lineTo(6.4f, 19.2f); p.quadTo(6.5f, 20.5f, 7.8f, 20.5f); p.lineTo(16.2f, 20.5f); p.quadTo(17.5f, 20.5f, 17.6f, 19.2f); p.lineTo(18.5f, 6f)
                p.moveTo(10f, 10f); p.lineTo(10f, 16.5f)
                p.moveTo(14f, 10f); p.lineTo(14f, 16.5f)
            }
            BACK -> { p.moveTo(20f, 12f); p.lineTo(4f, 12f); p.moveTo(10f, 6f); p.lineTo(4f, 12f); p.lineTo(10f, 18f) }
            LOCK -> { canvas.drawRoundRect(RectF(5f, 11f, 19f, 21f), 2f, 2f, paint); p.moveTo(8f, 11f); p.lineTo(8f, 8f); p.arcTo(RectF(8f, 4f, 16f, 12f), 180f, 180f); p.lineTo(16f, 11f) }
            UNLOCK -> { canvas.drawRoundRect(RectF(5f, 11f, 19f, 21f), 2f, 2f, paint); p.moveTo(8f, 11f); p.lineTo(8f, 8f); p.arcTo(RectF(8f, 4f, 16f, 12f), 180f, 180f) }
            PAUSE -> { paint.style = Paint.Style.FILL; canvas.drawRoundRect(RectF(6f, 4f, 10.5f, 20f), 1.5f, 1.5f, paint); canvas.drawRoundRect(RectF(13.5f, 4f, 18f, 20f), 1.5f, 1.5f, paint); paint.style = Paint.Style.STROKE }
            PLAY_FILLED -> { paint.style = Paint.Style.FILL; p.moveTo(7f, 4.5f); p.lineTo(19f, 12f); p.lineTo(7f, 19.5f); p.close(); canvas.drawPath(p, paint); p.reset(); paint.style = Paint.Style.STROKE }
            EXPAND -> { p.moveTo(4f, 9f); p.lineTo(4f, 4f); p.lineTo(9f, 4f); p.moveTo(15f, 4f); p.lineTo(20f, 4f); p.lineTo(20f, 9f); p.moveTo(20f, 15f); p.lineTo(20f, 20f); p.lineTo(15f, 20f); p.moveTo(9f, 20f); p.lineTo(4f, 20f); p.lineTo(4f, 15f) }
            ROTATE -> { canvas.drawRoundRect(RectF(3f, 6f, 21f, 18f), 2.5f, 2.5f, paint); p.moveTo(18f, 10f); p.lineTo(18f, 14f) }
            PIP -> { canvas.drawRoundRect(RectF(3f, 5f, 21f, 19f), 2f, 2f, paint); paint.style = Paint.Style.FILL; canvas.drawRoundRect(RectF(12f, 11f, 19f, 17f), 1f, 1f, paint); paint.style = Paint.Style.STROKE }
            COMPRESS -> { p.moveTo(9f, 4f); p.lineTo(9f, 9f); p.lineTo(4f, 9f); p.moveTo(15f, 4f); p.lineTo(15f, 9f); p.lineTo(20f, 9f); p.moveTo(20f, 15f); p.lineTo(15f, 15f); p.lineTo(15f, 20f); p.moveTo(4f, 15f); p.lineTo(9f, 15f); p.lineTo(9f, 20f) }
            COMMENT -> { canvas.drawRoundRect(RectF(3f, 4f, 21f, 17f), 3f, 3f, paint); p.moveTo(8f, 17f); p.lineTo(8f, 21f); p.lineTo(12.5f, 17f) }
            DOWNLOAD -> { p.moveTo(12f, 4f); p.lineTo(12f, 15f); p.moveTo(7f, 10.5f); p.lineTo(12f, 15.5f); p.lineTo(17f, 10.5f); p.moveTo(5f, 19.5f); p.lineTo(19f, 19.5f) }
        }
        canvas.drawPath(p, paint); canvas.restore()
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Downloader مطلوب لتهيئة NewPipe قبل أي استخراج (جلب الجودات والتعليقات). */
class SimpleDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val conn = URL(request.url()).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20000
            conn.readTimeout = 20000
            conn.requestMethod = request.httpMethod()
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0")
            for ((k, values) in request.headers()) {
                if (k.equals("Accept-Encoding", true)) continue
                conn.setRequestProperty(k, values.joinToString(", "))
            }
            val data = request.dataToSend()
            if (data != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(data) }
            }
            val code = conn.responseCode
            if (code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val headers = HashMap<String, List<String>>()
            for ((k, v) in conn.headerFields) { if (k != null) headers[k] = v }
            return Response(code, conn.responseMessage, headers, body, conn.url.toString())
        } finally {
            conn.disconnect()
        }
    }
}

class DownloadTask(val id: Int, val title: String, val url: String, val fileName: String, val audioUrl: String? = null) {
    companion object {
        const val QUEUED = 0; const val DOWNLOADING = 1; const val SAVING = 2
        const val DONE = 3; const val FAILED = 4; const val CANCELED = 5
    }
    @Volatile var state = QUEUED
    @Volatile var total = -1L
    @Volatile var segments = 1
    @Volatile var error: String? = null
    val downloaded = AtomicLong(0L)
    val abort = AtomicBoolean(false)
    val cancelled = AtomicBoolean(false)
    val isActive: Boolean get() = state == QUEUED || state == DOWNLOADING || state == SAVING
}

/** مدير تنزيل على مستوى التطبيق: عدة فيديوهات معًا، وكل فيديو يُقسَّم إلى 6 أجزاء متوازية. */
object DownloadCenter {
    const val SEGMENTS = 6
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    private val executor = Executors.newCachedThreadPool()
    private var appCtx: Context? = null
    private var nextId = 1
    val tasks = CopyOnWriteArrayList<DownloadTask>()

    fun init(ctx: Context) { if (appCtx == null) appCtx = ctx.applicationContext }

    @Synchronized
    fun enqueue(title: String, url: String, ext: String, audioUrl: String? = null): DownloadTask {
        val safe = title.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim().take(80).ifEmpty { "video" }
        val t = DownloadTask(nextId++, title, url, "$safe.$ext", audioUrl)
        tasks.add(0, t)
        executor.execute { process(t) }
        return t
    }

    fun cancel(t: DownloadTask) { t.cancelled.set(true); t.abort.set(true) }
    fun remove(t: DownloadTask) { if (!t.isActive) tasks.remove(t) }

    private fun fail(t: DownloadTask, e: Exception) {
        if (t.cancelled.get()) return
        if (t.abort.compareAndSet(false, true)) t.error = e.message ?: e.javaClass.simpleName
    }

    private fun fetchFile(t: DownloadTask, url: String, tmp: File, total: Long, ranges: Boolean) {
        if (ranges && total > 1_000_000L) {
            t.segments = SEGMENTS
            RandomAccessFile(tmp, "rw").use { it.setLength(total) }
            val part = total / SEGMENTS
            val futures = (0 until SEGMENTS).map { i ->
                val start = i * part
                val end = if (i == SEGMENTS - 1) total - 1 else start + part - 1
                executor.submit {
                    try { downloadRange(t, url, tmp, start, end) } catch (e: Exception) { fail(t, e) }
                }
            }
            futures.forEach { try { it.get() } catch (_: Exception) {} }
        } else {
            t.segments = 1
            try { downloadRange(t, url, tmp, 0L, -1L) } catch (e: Exception) { fail(t, e) }
        }
    }

    private fun process(t: DownloadTask) {
        val ctx = appCtx ?: return
        val stamp = System.currentTimeMillis()
        val vTmp = File(ctx.cacheDir, "dl_${t.id}_${stamp}_v.part")
        val aTmp = File(ctx.cacheDir, "dl_${t.id}_${stamp}_a.part")
        val oTmp = File(ctx.cacheDir, "dl_${t.id}_${stamp}_o.mp4")
        try {
            val v = probe(t.url)
            val a = if (t.audioUrl != null) probe(t.audioUrl) else null
            t.total = if (v.first > 0 && (a == null || a.first > 0)) v.first + (a?.first ?: 0L) else -1L
            t.state = DownloadTask.DOWNLOADING

            fetchFile(t, t.url, vTmp, v.first, v.second)
            if (a != null && !t.abort.get()) fetchFile(t, t.audioUrl!!, aTmp, a.first, a.second)

            if (t.cancelled.get()) { t.state = DownloadTask.CANCELED; return }
            if (t.abort.get()) { t.state = DownloadTask.FAILED; return }

            t.state = DownloadTask.SAVING
            val finalFile = if (a != null) { mux(vTmp, aTmp, oTmp); oTmp } else vTmp
            saveToGallery(ctx, t, finalFile)
            t.state = DownloadTask.DONE
        } catch (e: Exception) {
            if (t.cancelled.get()) t.state = DownloadTask.CANCELED
            else { t.error = e.message ?: e.javaClass.simpleName; t.state = DownloadTask.FAILED }
        } finally {
            vTmp.delete(); aTmp.delete(); oTmp.delete()
        }
    }

    /** دمج مسار الفيديو مع مسار الصوت في ملف MP4 واحد */
    private fun mux(video: File, audio: File, out: File) {
        val vEx = MediaExtractor()
        val aEx = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            vEx.setDataSource(video.path)
            aEx.setDataSource(audio.path)
            val vi = findTrack(vEx, "video/")
            val ai = findTrack(aEx, "audio/")
            if (vi < 0 || ai < 0) throw IOException("تعذّر دمج الصوت والصورة (صيغة غير مدعومة)")
            vEx.selectTrack(vi); aEx.selectTrack(ai)
            muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val vt = muxer.addTrack(vEx.getTrackFormat(vi))
            val at = muxer.addTrack(aEx.getTrackFormat(ai))
            muxer.start()
            copyTrack(vEx, muxer, vt)
            copyTrack(aEx, muxer, at)
            muxer.stop()
        } finally {
            try { vEx.release() } catch (_: Exception) {}
            try { aEx.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun findTrack(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            if (ex.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME)?.startsWith(prefix) == true) return i
        }
        return -1
    }

    private fun copyTrack(ex: MediaExtractor, muxer: MediaMuxer, track: Int) {
        val buf = ByteBuffer.allocate(4 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            buf.clear()
            val size = ex.readSampleData(buf, 0)
            if (size < 0) break
            val flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            info.set(0, size, ex.sampleTime, flags)
            muxer.writeSampleData(track, buf, info)
            ex.advance()
        }
    }

    /** يرجع (الحجم الكلي، هل الخادم يدعم Range) */
    private fun probe(url: String): Pair<Long, Boolean> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000; c.readTimeout = 15000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Range", "bytes=0-0")
            c.connect()
            val code = c.responseCode
            if (code == 206) {
                val total = c.getHeaderField("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull() ?: -1L
                if (total > 0) return Pair(total, true)
            }
            if (code in 200..299) return Pair(c.contentLengthLong, false)
            throw IOException("HTTP $code")
        } finally { c.disconnect() }
    }

    private fun downloadRange(t: DownloadTask, url: String, file: File, start: Long, end: Long) {
        var pos = start
        var attempt = 0
        while (!t.abort.get()) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 20000
                conn.setRequestProperty("User-Agent", UA)
                if (end >= 0) conn.setRequestProperty("Range", "bytes=$pos-$end")
                conn.connect()
                val code = conn.responseCode
                if (end >= 0 && code != 206) throw IOException("HTTP $code")
                if (end < 0 && code !in 200..299) throw IOException("HTTP $code")
                conn.inputStream.use { input ->
                    RandomAccessFile(file, "rw").use { raf ->
                        raf.seek(pos)
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (t.abort.get()) return
                            val n = input.read(buf)
                            if (n < 0) break
                            raf.write(buf, 0, n)
                            pos += n
                            t.downloaded.addAndGet(n.toLong())
                        }
                    }
                }
                if (end < 0 || pos > end) return
                throw IOException("انقطع الاتصال")
            } catch (e: Exception) {
                if (t.abort.get()) return
                if (end < 0 || ++attempt >= 4) throw e
                Thread.sleep(1000L * attempt)
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun saveToGallery(ctx: Context, t: DownloadTask, src: File) {
        val mime = if (t.fileName.endsWith(".webm")) "video/webm" else "video/mp4"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, t.fileName)
                put(MediaStore.Video.Media.MIME_TYPE, mime)
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/TubeTV")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val coll = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = ctx.contentResolver.insert(coll, values) ?: throw IOException("تعذّر إنشاء الملف")
            val out = ctx.contentResolver.openOutputStream(uri) ?: throw IOException("تعذّر فتح الملف للكتابة")
            out.use { o -> src.inputStream().use { it.copyTo(o, 256 * 1024) } }
            values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
            ctx.contentResolver.update(uri, values, null, null)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "TubeTV")
            dir.mkdirs()
            var dest = File(dir, t.fileName)
            var n = 1
            while (dest.exists()) { dest = File(dir, t.fileName.substringBeforeLast('.') + " ($n)." + t.fileName.substringAfterLast('.')); n++ }
            src.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(ctx, arrayOf(dest.path), arrayOf(mime), null)
        }
    }
}

class ShimmerView(ctx: Context) : View(ctx) {
    private val paint = Paint(); private val shMatrix = Matrix()
    private var shader: LinearGradient? = null; private var phase = -1f
    private val anim = ValueAnimator.ofFloat(-1f, 1f).apply {
        duration = 1300; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        shader = LinearGradient(0f, 0f, w.toFloat(), 0f, intArrayOf(Color.parseColor("#1F1F1F"), Color.parseColor("#363636"), Color.parseColor("#1F1F1F")), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        paint.shader = shader
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        shMatrix.setTranslate(phase * width, 0f); shader?.setLocalMatrix(shMatrix)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}

class MainActivity : AppCompatActivity() {

    private val appName: String by lazy {
        try { getString(R.string.app_name) } catch (_: Exception) { "TUBE TV PRO" }
    }

    private lateinit var prefs: Prefs
    private val bg = Color.parseColor("#0F0F0F")
    private val red = Color.parseColor("#FF0000")
    private val gray = Color.parseColor("#AAAAAA")
    private val navActive = Color.parseColor("#FF4D4D")
    private val navInactive = Color.parseColor("#E0E0E0")

    private val all = ArrayList<VideoItem>()
    private val shown = ArrayList<VideoItem>()
    private val expandedCommentIds = HashSet<Long>()

    private lateinit var listView: ListView
    private lateinit var adapter: VideoAdapter
    private lateinit var continueHeader: LinearLayout
    private lateinit var logo: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var emptyText: TextView
    private lateinit var ytTab: YoutubeTab
    private lateinit var ytPanel: FrameLayout
    private val YT_TAB = 5
    private var tab = 0
    private var query = ""

    private lateinit var refreshBar: TextView
    private var refreshing = false
    private var touchStartY = 0f
    private var pulling = false
    private var firstResume = true

    private var folderPath: String = ""
    private var pendingDelete: VideoItem? = null
    private var deleteNeedsRetry = false
    private var pendingOpenId: Long = -1L
    private var pendingRestoreComments = false
    private var observerRegistered = false

    private lateinit var dlPanel: LinearLayout
    private lateinit var dlList: LinearLayout
    private lateinit var dlEmpty: TextView
    private lateinit var dlBadge: TextView
    private val dlRows = HashMap<Int, DlRow>()
    private val notifiedDone = HashSet<Int>()
    private val refreshViews = ArrayList<View>()
    private var spinAnim: ValueAnimator? = null

    private class DlRow(val root: LinearLayout, val title: TextView, val bar: ProgressBar, val status: TextView, val action: TextView) {
        var lastBytes = 0L; var lastTime = 0L; var speed = 0L
    }

    private val dlTicker = object : Runnable {
        override fun run() {
            try { refreshDownloads() } catch (_: Exception) {}
            ui.postDelayed(this, 500)
        }
    }

    private val tabNames = arrayOf("الصفحة الرئيسية", "تابع المشاهدة", "الأطول", "تمت المشاهدة", "Shorts", "YouTube")
    private val tabTexts = ArrayList<TextView>()
    private val tabLines = ArrayList<View>()

    private class NavRef(val tabIdx: Int, val icon: IconDrawable, val label: TextView)
    private val navRefs = ArrayList<NavRef>()

    private val pool = Executors.newFixedThreadPool(3)
    private val ui = Handler(Looper.getMainLooper())

    private val mediaObserver = object : ContentObserver(ui) {
        override fun onChange(selfChange: Boolean) {
            if (playerRoot.visibility != View.VISIBLE) {
                loadVideos()
            }
        }
    }

    private val cache = object : LruCache<Long, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 6).toInt()) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount / 1024
    }

    private val thumbW: Int by lazy { maxOf(resources.displayMetrics.widthPixels, 1080).coerceAtMost(2160) }
    private val thumbH: Int by lazy { thumbW * 9 / 16 }
    private val thumbDir: File by lazy { File(cacheDir, "thumbs_hq_v2").apply { mkdirs() } }

    private fun loadFont(name: String): Typeface? {
        if (Build.VERSION.SDK_INT < 26) return null
        return try {
            val id = resources.getIdentifier(name, "font", packageName)
            if (id == 0) null else resources.getFont(id)
        } catch (e: Exception) { null }
    }
    private val fontRegular: Typeface by lazy { loadFont("cairo_regular") ?: Typeface.DEFAULT }
    private val fontBold: Typeface by lazy { loadFont("cairo_bold") ?: Typeface.DEFAULT_BOLD }

    private fun applyFont(v: View) {
        if (v is TextView) {
            val bold = v.typeface === fontBold || v.typeface?.isBold == true
            v.typeface = if (bold) fontBold else fontRegular
        }
        if (v is ViewGroup) { for (i in 0 until v.childCount) applyFont(v.getChildAt(i)) }
    }

    private fun buzz(v: View) { try { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } catch (_: Exception) {} }

    private lateinit var rootFrame: FrameLayout
    private lateinit var playerRoot: FrameLayout
    private lateinit var aspectLayout: AspectRatioFrameLayout
    private lateinit var surfaceView: SurfaceView
    private lateinit var controls: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var playerTitle: TextView
    private lateinit var playPause: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var timeText: TextView
    private lateinit var speedBtn: TextView
    private lateinit var fsBtn: ImageView
    private lateinit var unlockBtn: ImageView
    private lateinit var hudText: TextView
    private lateinit var commentsPanel: LinearLayout
    private lateinit var commentsList: LinearLayout
    private lateinit var commentsTitle: TextView
    private lateinit var commentBadge: TextView
    private lateinit var gestureOverlay: View
    private var commentsOpen = false
    private var commentsCount = 0
    private lateinit var gestures: GestureDetector
    private val pauseDrawable = IconDrawable(IconDrawable.PAUSE, Color.WHITE)
    private val playDrawable = IconDrawable(IconDrawable.PLAY_FILLED, Color.WHITE)

    private var player: ExoPlayer? = null
    private var prepared = false
    private var current: VideoItem? = null
    private var videoW = 0
    private var videoH = 0
    private var userSeeking = false
    private var lastSaveTime = 0L
    private var locked = false
    private var fullscreen = false
    private val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private var speedIdx = 2
    private var gestureMode = 0
    private val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val hideHud = Runnable { hudText.visibility = View.GONE }
    private val hideUnlock = Runnable { if (locked) unlockBtn.visibility = View.GONE }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            val p = player ?: return
            if (state == Player.STATE_READY) {
                prepared = true
                val dur = p.duration
                if (dur != C.TIME_UNSET && dur > 0) {
                    seekBar.max = dur.toInt()
                }
            } else if (state == Player.STATE_ENDED) {
                current?.let { saveProgress(p, it) }
            }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playPause.setImageDrawable(if (isPlaying) pauseDrawable else playDrawable)
            showControls()
        }
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            videoW = videoSize.width; videoH = videoSize.height
            if (videoH > 0) {
                aspectLayout.setAspectRatio(videoW.toFloat() / videoH.toFloat())
            }
        }
    }

    private fun saveProgress(p: ExoPlayer, c: VideoItem) {
        try {
            val dur = p.duration
            if (dur == C.TIME_UNSET || dur <= 0) return
            val ended = p.playbackState == Player.STATE_ENDED
            val pos = if (ended) 0L else p.currentPosition
            prefs.savePosition(c.id, pos, dur, ended)
        } catch (_: Exception) {}
    }

    private val ticker = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && prepared) {
                try {
                    val pos = p.currentPosition
                    val dur = p.duration
                    if (dur != C.TIME_UNSET && dur > 0) {
                        if (!userSeeking) {
                            seekBar.max = dur.toInt()
                            seekBar.progress = pos.toInt()
                        }
                        timeText.text = fmt(pos) + " / " + fmt(dur)
                        val now = System.currentTimeMillis()
                        if (now - lastSaveTime > 5000) {
                            current?.let { saveProgress(p, it) }
                            lastSaveTime = now
                        }
                    }
                } catch (_: Exception) {}
            }
            ui.postDelayed(this, 500)
        }
    }

    private val pickFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Toast.makeText(this@MainActivity, "تعذّر حفظ صلاحية المجلد", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        val path = treeToPath(uri)
        if (path == null) {
            Toast.makeText(this@MainActivity, "تعذّر قراءة المجلد", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        folderPath = path; prefs.folderPath = path; loadVideos()
    }

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val v = pendingDelete ?: return@registerForActivityResult
        val needsRetry = deleteNeedsRetry
        if (result.resultCode == RESULT_OK) {
            if (needsRetry) {
                try {
                    if (contentResolver.delete(v.uri, null, null) > 0) afterDeleted(v)
                    else Toast.makeText(this@MainActivity, "تعذّر الحذف", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) { Toast.makeText(this@MainActivity, "تعذّر الحذف", Toast.LENGTH_SHORT).show() }
            } else { afterDeleted(v) }
        }
        pendingDelete = null; deleteNeedsRetry = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        DownloadCenter.init(this)
        try { NewPipe.init(SimpleDownloader()) } catch (_: Exception) {}
        folderPath = prefs.folderPath
        tab = savedInstanceState?.getInt("tab") ?: prefs.lastTab
        query = savedInstanceState?.getString("query") ?: ""
        pendingOpenId = savedInstanceState?.let { if (it.getBoolean("player_open")) it.getLong("current_id", -1L) else -1L } ?: -1L
        pendingRestoreComments = savedInstanceState?.getBoolean("comments_open") == true

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::dlPanel.isInitialized && dlPanel.visibility == View.VISIBLE) {
                    hideDownloads()
                } else if (playerRoot.visibility == View.VISIBLE) {
                    if (locked) showUnlockBtn()
                    else if (commentsPanel.visibility == View.VISIBLE) hideComments()
                    else if (fullscreen) setFullscreen(false)
                    else closePlayer()
                } else if (tab == YT_TAB) {
                    if (!ytTab.handleBack()) selectTab(0)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        rootFrame = FrameLayout(this)
        rootFrame.setBackgroundColor(bg)

        val main = LinearLayout(this)
        main.orientation = LinearLayout.VERTICAL
        main.layoutDirection = View.LAYOUT_DIRECTION_RTL

        main.addView(buildTopBlock())
        main.addView(buildTabs(), LinearLayout.LayoutParams(-1, -2))

        listView = ListView(this)
        listView.divider = null
        listView.dividerHeight = 0
        listView.setPadding(0, 0, 0, 0)
        listView.clipToPadding = false
        listView.setBackgroundColor(bg)

        continueHeader = LinearLayout(this)
        continueHeader.orientation = LinearLayout.VERTICAL
        listView.addHeaderView(continueHeader, null, false)

        adapter = VideoAdapter()
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, pos, _ ->
            val idx = pos - listView.headerViewsCount
            if (idx in 0 until shown.size) openPlayer(shown[idx])
        }

        emptyText = TextView(this)
        emptyText.text = "جارٍ تحميل الفيديوهات..."
        emptyText.setTextColor(gray)
        emptyText.textSize = 16f
        emptyText.gravity = Gravity.CENTER
        emptyText.setPadding(dp(24), dp(24), dp(24), dp(24))

        val listFrame = FrameLayout(this)
        listFrame.addView(listView, FrameLayout.LayoutParams(-1, -1))
        listFrame.addView(emptyText, FrameLayout.LayoutParams(-1, -1))
        listView.emptyView = emptyText

        ytTab = YoutubeTab(this, { folderPath }, { reloadVideos() }, { openPlayer(it) }, { startDownloadFromUrl(it) })
        ytPanel = ytTab.view
        listFrame.addView(ytPanel, FrameLayout.LayoutParams(-1, -1))
        ytPanel.visibility = if (tab == YT_TAB) View.VISIBLE else View.GONE
        if (tab == YT_TAB) ytTab.onShown()

        refreshBar = TextView(this)
        refreshBar.text = "↻ جارٍ التحديث..."
        refreshBar.setTextColor(Color.WHITE)
        refreshBar.textSize = 14f
        refreshBar.gravity = Gravity.CENTER
        refreshBar.setPadding(dp(16), dp(8), dp(16), dp(8))
        refreshBar.background = GradientDrawable().apply { setColor(red); cornerRadius = dp(20).toFloat() }
        refreshBar.elevation = dp(8).toFloat()
        refreshBar.visibility = View.GONE
        listFrame.addView(refreshBar, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(10) })

        listView.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { touchStartY = e.y; pulling = false }
                MotionEvent.ACTION_MOVE -> {
                    val atTop = listView.childCount == 0 || (listView.firstVisiblePosition == 0 && listView.getChildAt(0).top >= 0)
                    if (atTop && !refreshing && e.y - touchStartY > dp(120)) pulling = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if (pulling) { pulling = false; reloadVideos() } }
            }
            false
        }

        main.addView(listFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        main.addView(buildBottomNav(), LinearLayout.LayoutParams(-1, dp(68)))
        rootFrame.addView(main, FrameLayout.LayoutParams(-1, -1))
        buildDownloadsPanel()

        buildPlayer()
        playerRoot.layoutDirection = View.LAYOUT_DIRECTION_LTR
        rootFrame.addView(playerRoot, FrameLayout.LayoutParams(-1, -1))
        playerRoot.visibility = View.GONE

        addSplash()
        setContentView(rootFrame)
        applyFont(rootFrame)
        ui.post(ticker)
        ui.post(dlTicker)
        checkPermissionAndLoad()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
        outState.putString("query", query)
        outState.putBoolean("player_open", playerRoot.visibility == View.VISIBLE)
        outState.putBoolean("fullscreen", fullscreen)
        outState.putBoolean("comments_open", commentsOpen && commentsPanel.visibility == View.VISIBLE)
        current?.let { c ->
            outState.putLong("current_id", c.id)
            val p = player
            if (p != null && prepared) saveProgress(p, c)
        }
    }

    private fun addSplash() {
        val splash = LinearLayout(this)
        splash.orientation = LinearLayout.VERTICAL
        splash.gravity = Gravity.CENTER
        splash.setBackgroundColor(bg)
        splash.isClickable = true

        val sIcon = TextView(this)
        sIcon.text = "▶"; sIcon.setTextColor(Color.WHITE); sIcon.textSize = 30f; sIcon.gravity = Gravity.CENTER
        sIcon.background = GradientDrawable().apply { setColor(red); cornerRadius = dp(16).toFloat() }
        sIcon.setPadding(dp(22), dp(10), dp(22), dp(10))
        splash.addView(sIcon, LinearLayout.LayoutParams(-2, -2))

        val sName = TextView(this)
        sName.text = appName; sName.setTextColor(Color.WHITE); sName.textSize = 28f; sName.typeface = Typeface.DEFAULT_BOLD; sName.gravity = Gravity.CENTER
        splash.addView(sName, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) })

        rootFrame.addView(splash, FrameLayout.LayoutParams(-1, -1))

        sIcon.scaleX = 0.7f; sIcon.scaleY = 0.7f
        sIcon.animate().scaleX(1f).scaleY(1f).setDuration(500).setInterpolator(DecelerateInterpolator()).start()
        ui.postDelayed({ splash.animate().alpha(0f).setDuration(350).withEndAction { rootFrame.removeView(splash) }.start() }, 900)
    }

    private fun raised(top: Int, bottom: Int, radius: Int, pressed: Boolean = false): Drawable {
        val r = dp(radius).toFloat()
        val shadow = GradientDrawable().apply { setColor(Color.parseColor("#66000000")); cornerRadius = r }
        val edge = GradientDrawable().apply { setColor(Color.parseColor("#33FFFFFF")); cornerRadius = r }
        val face = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, if (pressed) intArrayOf(bottom, top) else intArrayOf(top, bottom)).apply { cornerRadius = r }
        val layers = LayerDrawable(arrayOf<Drawable>(shadow, edge, face))
        layers.setLayerInset(0, 0, dp(4), 0, 0); layers.setLayerInset(1, 0, 0, 0, dp(3))
        layers.setLayerInset(2, dp(1), dp(1), dp(1), if (pressed) dp(3) else dp(4))
        return layers
    }

    private fun setBgKeepPad(v: View, d: Drawable) {
        val l = v.paddingLeft; val t = v.paddingTop
        val r = v.paddingRight; val b = v.paddingBottom
        v.background = d; v.setPadding(l, t, r, b)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun fmt(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%02d:%02d", m, sec)
    }

    private fun buildTopBlock(): View {
        val top = LinearLayout(this)
        top.orientation = LinearLayout.VERTICAL
        top.setBackgroundColor(bg)
        top.setPadding(dp(14), dp(14), dp(14), dp(6))

        logo = LinearLayout(this)
        logo.orientation = LinearLayout.HORIZONTAL
        logo.gravity = Gravity.CENTER_VERTICAL

        val iconBox = TextView(this)
        iconBox.text = "▶"; iconBox.setTextColor(Color.WHITE); iconBox.textSize = 15f
        iconBox.gravity = Gravity.CENTER
        iconBox.background = GradientDrawable().apply { setColor(red); cornerRadius = dp(10).toFloat() }
        val ibLp = LinearLayout.LayoutParams(dp(34), dp(34))
        logo.addView(iconBox, ibLp)

        val title = TextView(this)
        title.text = appName; title.setTextColor(Color.WHITE); title.textSize = 19f; title.typeface = fontBold
        title.setPadding(dp(10), 0, 0, 0)
        logo.addView(title)

        val spacer = View(this)
        logo.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))

        val refreshBtn = ImageButton(this)
        refreshBtn.setImageDrawable(IconDrawable(IconDrawable.REFRESH, navInactive))
        refreshBtn.background = null
        refreshBtn.setOnClickListener { buzz(it); reloadVideos() }
        logo.addView(refreshBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
        refreshViews.add(refreshBtn)

        val folderBtn = ImageButton(this)
        folderBtn.setImageDrawable(IconDrawable(IconDrawable.FOLDER, navInactive))
        folderBtn.background = null
        folderBtn.setOnClickListener { buzz(it); pickFolderLauncher.launch(null) }
        logo.addView(folderBtn, LinearLayout.LayoutParams(dp(40), dp(40)))

        val dlBox = FrameLayout(this)
        val dlBtn = ImageButton(this)
        dlBtn.setImageDrawable(IconDrawable(IconDrawable.DOWNLOAD, navInactive))
        dlBtn.background = null
        dlBtn.setOnClickListener { buzz(it); if (dlPanel.visibility == View.VISIBLE) hideDownloads() else showDownloads() }
        dlBox.addView(dlBtn, FrameLayout.LayoutParams(dp(40), dp(40)))
        dlBadge = TextView(this)
        dlBadge.setTextColor(Color.WHITE); dlBadge.textSize = 10f; dlBadge.gravity = Gravity.CENTER
        dlBadge.background = GradientDrawable().apply { setColor(red); shape = GradientDrawable.OVAL }
        dlBadge.visibility = View.GONE
        dlBox.addView(dlBadge, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.TOP or Gravity.END))
        logo.addView(dlBox, LinearLayout.LayoutParams(dp(40), dp(40)))

        top.addView(logo)

        searchBox = EditText(this)
        searchBox.hint = "ابحث عن فيديو..."
        searchBox.setTextColor(Color.WHITE); searchBox.setHintTextColor(Color.parseColor("#777777"))
        searchBox.textSize = 15f; searchBox.isSingleLine = true
        searchBox.background = GradientDrawable().apply {
            setColor(Color.parseColor("#1F1F1F")); cornerRadius = dp(22).toFloat()
            setStroke(dp(1), Color.parseColor("#333333"))
        }
        searchBox.setPadding(dp(18), dp(10), dp(18), dp(10))
        searchBox.setText(query)
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                applyFilter()
            }
        })
        top.addView(searchBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        return top
    }

    private fun buildTabs(): View {
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.setBackgroundColor(bg)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(10), dp(6), dp(10), dp(6))
        row.layoutDirection = View.LAYOUT_DIRECTION_RTL

        for ((i, name) in tabNames.withIndex()) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER

            val tv = TextView(this)
            tv.text = name; tv.textSize = 14f; tv.setPadding(dp(14), dp(8), dp(14), dp(6))
            tv.setTextColor(if (i == tab) Color.WHITE else gray)
            tv.typeface = if (i == tab) fontBold else fontRegular

            val line = View(this)
            line.setBackgroundColor(red)
            val lp = LinearLayout.LayoutParams(-1, dp(3))
            lp.marginStart = dp(10); lp.marginEnd = dp(10)
            line.layoutParams = lp
            line.visibility = if (i == tab) View.VISIBLE else View.INVISIBLE

            col.addView(tv); col.addView(line)
            tv.setOnClickListener { buzz(it); selectTab(i) }
            tabTexts.add(tv); tabLines.add(line)
            row.addView(col, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(2) })
        }
        scroll.addView(row)
        return scroll
    }

    private fun selectTab(idx: Int) {
        if (tab == idx && idx != YT_TAB) return
        val old = tab
        tab = idx; prefs.lastTab = idx
        for (i in tabTexts.indices) {
            tabTexts[i].setTextColor(if (i == idx) Color.WHITE else gray)
            tabTexts[i].typeface = if (i == idx) fontBold else fontRegular
            tabLines[i].visibility = if (i == idx) View.VISIBLE else View.INVISIBLE
        }
        ytPanel.visibility = if (idx == YT_TAB) View.VISIBLE else View.GONE
        if (idx == YT_TAB) {
            ytTab.onShown()
        } else if (old == YT_TAB) {
            ytTab.onHidden()
        }
        applyFilter()
    }

    private fun buildBottomNav(): View {
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.setBackgroundColor(Color.parseColor("#151515"))
        nav.gravity = Gravity.CENTER
        nav.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val items = arrayOf(
            "الرئيسية" to IconDrawable.HOME,
            "بحث" to IconDrawable.SEARCH,
            "المجلد" to IconDrawable.FOLDER,
            "YouTube" to IconDrawable.PLAY,
            "تحديث" to IconDrawable.REFRESH
        )
        for ((i, item) in items.withIndex()) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER
            col.setPadding(0, dp(8), 0, dp(6))

            val isRefresh = i == 4
            val icon = IconDrawable(item.second, if (isRefresh) Color.WHITE else if (i == 0) navActive else navInactive)
            val iv = ImageView(this)
            iv.setImageDrawable(icon)
            if (isRefresh) {
                iv.background = GradientDrawable().apply { setColor(red); shape = GradientDrawable.OVAL }
                iv.setPadding(dp(6), dp(6), dp(6), dp(6))
                iv.elevation = dp(3).toFloat()
                col.addView(iv, LinearLayout.LayoutParams(dp(32), dp(32)))
                refreshViews.add(iv)
            } else {
                col.addView(iv, LinearLayout.LayoutParams(dp(24), dp(24)))
            }

            val tv = TextView(this)
            tv.text = item.first; tv.textSize = 11f
            tv.setTextColor(if (i == 0) navActive else navInactive)
            col.addView(tv)

            col.setOnClickListener { buzz(it); handleNav(i) }
            if (!isRefresh) navRefs.add(NavRef(i, icon, tv))
            nav.addView(col, LinearLayout.LayoutParams(0, -1, 1f))
        }
        return nav
    }

    private fun handleNav(i: Int) {
        if (i == 4) { reloadVideos(); return }
        when (i) {
            0 -> selectTab(0)
            1 -> {
                searchBox.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT)
            }
            2 -> pickFolderLauncher.launch(null)
            3 -> selectTab(YT_TAB)
        }
        for (ref in navRefs) {
            val active = ref.tabIdx == i
            ref.icon.setIconColor(if (active) navActive else navInactive)
            ref.label.setTextColor(if (active) navActive else navInactive)
        }
    }

    private fun updateNavForTab() {
        val activeIdx = if (tab == YT_TAB) 3 else if (tab == 0) 0 else -1
        for (ref in navRefs) {
            val active = ref.tabIdx == activeIdx
            ref.icon.setIconColor(if (active) navActive else navInactive)
            ref.label.setTextColor(if (active) navActive else navInactive)
        }
    }

    private fun applyFilter() {
        if (tab == YT_TAB) return
        shown.clear()
        val q = query.trim().lowercase(Locale.getDefault())
        val base = if (q.isEmpty()) all else all.filter { it.title.lowercase(Locale.getDefault()).contains(q) }
        when (tab) {
            0 -> shown.addAll(base)
            1 -> shown.addAll(base.filter { val pos = prefs.getPosition(it.id); pos > 0L && pos < it.duration })
            2 -> shown.addAll(base.sortedByDescending { it.duration })
            3 -> shown.addAll(base.filter { prefs.isWatched(it.id) })
            4 -> shown.addAll(base.filter { it.duration in 1L..60_000L })
        }
        updateContinueHeader()
        adapter.notifyDataSetChanged()
        emptyText.text = if (shown.isEmpty()) {
            if (tab == 1) "لا توجد فيديوهات قيد المشاهدة" else "لا توجد فيديوهات"
        } else ""
        updateNavForTab()
    }

    private fun updateContinueHeader() {
        continueHeader.removeAllViews()
        if (tab != 1 || shown.isEmpty()) { continueHeader.visibility = View.GONE; return }
        continueHeader.visibility = View.VISIBLE
        val title = TextView(this)
        title.text = "تابع من حيث توقفت"; title.setTextColor(Color.WHITE); title.textSize = 16f; title.typeface = fontBold
        title.setPadding(dp(16), dp(12), dp(16), dp(8))
        continueHeader.addView(title)
    }

    private fun setSpinning(on: Boolean) {
        spinAnim?.cancel(); spinAnim = null
        refreshViews.forEach { it.rotation = 0f }
        if (!on) return
        spinAnim = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 800; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { a -> val r = a.animatedValue as Float; refreshViews.forEach { v -> v.rotation = r } }
            start()
        }
    }

    /** يفحص المجلدات ليُدخل أي فيديوهات جديدة في مكتبة الجهاز قبل قراءة القائمة */
    private fun scanMediaFolders() {
        try {
            val dirs = ArrayList<File>()
            if (folderPath.isNotEmpty()) dirs.add(File(folderPath))
            val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            dirs.add(movies)
            dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            val exts = setOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v")
            val files = ArrayList<String>()
            for (d in dirs.distinct()) {
                d.walkTopDown().maxDepth(3)
                    .filter { it.isFile && it.extension.lowercase() in exts }
                    .forEach { if (files.size < 300) files.add(it.path) }
            }
            if (files.isEmpty()) return
            val latch = CountDownLatch(files.size)
            MediaScannerConnection.scanFile(this, files.toTypedArray(), null) { _, _ -> latch.countDown() }
            latch.await(4, TimeUnit.SECONDS)
        } catch (_: Exception) {}
    }

    private fun reloadVideos(manual: Boolean = true) {
        if (tab == YT_TAB && manual) {
            ytTab.reload()
            Toast.makeText(this, "تم تحديث صفحة YouTube", Toast.LENGTH_SHORT).show()
            return
        }
        if (refreshing) return
        refreshing = true
        refreshBar.visibility = View.VISIBLE
        setSpinning(true)
        val started = System.currentTimeMillis()
        pool.execute {
            scanMediaFolders()
            val wait = 700L - (System.currentTimeMillis() - started)
            if (wait > 0) { try { Thread.sleep(wait) } catch (_: Exception) {} }
            ui.post { loadVideos() }
        }
    }

    private fun afterDeleted(v: VideoItem) {
        all.removeAll { it.id == v.id }
        prefs.removePosition(v.id)
        applyFilter()
    }

    private fun checkPermissionAndLoad() {
        if (Build.VERSION.SDK_INT >= 33) {
            val perm = Manifest.permission.READ_MEDIA_VIDEO
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(perm), 100)
                return
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
                return
            }
        }
        loadVideos()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) loadVideos()
    }

    private fun loadVideos() {
        pool.execute {
            val result = ArrayList<VideoItem>()
            try {
                val collection = if (Build.VERSION.SDK_INT >= 29) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                val proj = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DURATION)
                val sort = MediaStore.Video.Media.DATE_ADDED + " DESC"

                contentResolver.query(collection, proj, null, null, sort)?.use { c ->
                    val idIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val tIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                    val dIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    while (c.moveToNext()) {
                        val id = c.getLong(idIdx)
                        val title = c.getString(tIdx) ?: "فيديو"
                        val dur = c.getLong(dIdx)
                        val uri = ContentUris.withAppendedId(collection, id)
                        result.add(VideoItem(id, title, dur, uri))
                    }
                }
            } catch (_: Exception) {}
            ui.post {
                all.clear(); all.addAll(result)
                applyFilter()
                if (refreshing) {
                    refreshing = false
                    refreshBar.visibility = View.GONE
                    setSpinning(false)
                    Toast.makeText(this@MainActivity, "تم التحديث ✓  (" + all.size + " فيديو)", Toast.LENGTH_SHORT).show()
                }
                restorePendingPlayer()
            }
        }
    }

    private fun restorePendingPlayer() {
        if (pendingOpenId != -1L) {
            val v = all.find { it.id == pendingOpenId }
            pendingOpenId = -1L
            if (v != null) openPlayer(v)
        }
        updateNavForTab()
    }

    private fun buildPlayer() {
        playerRoot = FrameLayout(this)
        playerRoot.setBackgroundColor(Color.BLACK)

        aspectLayout = AspectRatioFrameLayout(this)
        aspectLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {}
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })
        aspectLayout.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
        playerRoot.addView(aspectLayout, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

        gestureOverlay = View(this)
        playerRoot.addView(gestureOverlay, FrameLayout.LayoutParams(-1, -1))

        hudText = TextView(this)
        hudText.setTextColor(Color.WHITE); hudText.textSize = 16f; hudText.typeface = fontBold
        hudText.background = GradientDrawable().apply { setColor(Color.parseColor("#CC000000")); cornerRadius = dp(12).toFloat() }
        hudText.setPadding(dp(16), dp(10), dp(16), dp(10))
        hudText.visibility = View.GONE
        playerRoot.addView(hudText, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        controls = LinearLayout(this)
        controls.orientation = LinearLayout.VERTICAL
        controls.gravity = Gravity.CENTER
        controls.setBackgroundColor(Color.TRANSPARENT)

        topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        topBar.setPadding(dp(12), dp(10), dp(12), dp(10))
        topBar.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#CC000000"), Color.TRANSPARENT))

        val backBtn = ImageButton(this)
        backBtn.setImageDrawable(IconDrawable(IconDrawable.BACK, Color.WHITE))
        backBtn.background = null
        backBtn.setOnClickListener { buzz(it); closePlayer() }
        topBar.addView(backBtn, LinearLayout.LayoutParams(dp(40), dp(40)))

        playerTitle = TextView(this)
        playerTitle.setTextColor(Color.WHITE); playerTitle.textSize = 14f; playerTitle.typeface = fontBold
        playerTitle.isSingleLine = true; playerTitle.ellipsize = android.text.TextUtils.TruncateAt.END
        topBar.addView(playerTitle, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })

        unlockBtn = ImageButton(this)
        unlockBtn.setImageDrawable(IconDrawable(IconDrawable.UNLOCK, Color.WHITE))
        unlockBtn.background = null
        unlockBtn.visibility = View.GONE
        unlockBtn.setOnClickListener { buzz(it); unlockPlayer() }
        topBar.addView(unlockBtn, LinearLayout.LayoutParams(dp(40), dp(40)))

        controls.addView(topBar, LinearLayout.LayoutParams(-1, -2))

        val midSpace = View(this)
        controls.addView(midSpace, LinearLayout.LayoutParams(-1, 0, 1f))

        val midRow = LinearLayout(this)
        midRow.orientation = LinearLayout.HORIZONTAL
        midRow.gravity = Gravity.CENTER

        val rewind = ImageButton(this)
        rewind.setImageDrawable(IconDrawable(IconDrawable.ROTATE, Color.WHITE))
        rewind.background = null
        rewind.scaleX = -1f
        rewind.setOnClickListener { buzz(it); player?.seekBack(); showControls() }
        midRow.addView(rewind, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(30) })

        playPause = ImageView(this)
        playPause.setImageDrawable(pauseDrawable)
        playPause.background = GradientDrawable().apply { setColor(Color.parseColor("#55000000")); shape = GradientDrawable.OVAL }
        playPause.setPadding(dp(14), dp(14), dp(14), dp(14))
        playPause.setOnClickListener { buzz(it); togglePlay() }
        midRow.addView(playPause, LinearLayout.LayoutParams(dp(72), dp(72)))

        val forward = ImageButton(this)
        forward.setImageDrawable(IconDrawable(IconDrawable.ROTATE, Color.WHITE))
        forward.background = null
        forward.setOnClickListener { buzz(it); player?.seekForward(); showControls() }
        midRow.addView(forward, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(30) })

        controls.addView(midRow, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER })

        val midSpace2 = View(this)
        controls.addView(midSpace2, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottomBar = LinearLayout(this)
        bottomBar.orientation = LinearLayout.VERTICAL
        bottomBar.setPadding(dp(12), dp(8), dp(12), dp(12))
        bottomBar.background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(Color.parseColor("#CC000000"), Color.TRANSPARENT))

        val timeRow = LinearLayout(this)
        timeRow.orientation = LinearLayout.HORIZONTAL
        timeRow.gravity = Gravity.CENTER_VERTICAL

        timeText = TextView(this)
        timeText.setTextColor(Color.WHITE); timeText.textSize = 12f
        timeRow.addView(timeText, LinearLayout.LayoutParams(-2, -2))

        val sp = View(this)
        timeRow.addView(sp, LinearLayout.LayoutParams(0, 1, 1f))

        speedBtn = TextView(this)
        speedBtn.text = "1x"; speedBtn.setTextColor(Color.WHITE); speedBtn.textSize = 13f
        speedBtn.setPadding(dp(12), dp(4), dp(12), dp(4))
        speedBtn.background = GradientDrawable().apply { setColor(Color.parseColor("#44FFFFFF")); cornerRadius = dp(12).toFloat() }
        speedBtn.setOnClickListener { buzz(it); cycleSpeed() }
        timeRow.addView(speedBtn)

        commentBadge = TextView(this)
        commentBadge.text = "💬"; commentBadge.textSize = 16f; commentBadge.gravity = Gravity.CENTER
        commentBadge.setPadding(dp(10), dp(4), dp(10), dp(4))
        commentBadge.background = GradientDrawable().apply { setColor(Color.parseColor("#44FFFFFF")); cornerRadius = dp(12).toFloat() }
        commentBadge.setOnClickListener { buzz(it); toggleComments() }
        timeRow.addView(commentBadge, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })

        fsBtn = ImageButton(this)
        fsBtn.setImageDrawable(IconDrawable(IconDrawable.EXPAND, Color.WHITE))
        fsBtn.background = null
        fsBtn.setOnClickListener { buzz(it); setFullscreen(!fullscreen) }
        timeRow.addView(fsBtn, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginStart = dp(4) })

        bottomBar.addView(timeRow)

        seekBar = SeekBar(this)
        seekBar.max = 1000
        seekBar.progressTintList = android.content.res.ColorStateList.valueOf(red)
        seekBar.thumbTintList = android.content.res.ColorStateList.valueOf(red)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val d = player?.duration ?: 0L
                    timeText.text = fmt(progress.toLong()) + " / " + fmt(if (d > 0L) d else 0L)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                userSeeking = false
                player?.seekTo((sb?.progress ?: 0).toLong())
            }
        })
        bottomBar.addView(seekBar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        controls.addView(bottomBar, LinearLayout.LayoutParams(-1, -2))

        playerRoot.addView(controls, FrameLayout.LayoutParams(-1, -1))

        commentsPanel = LinearLayout(this)
        commentsPanel.orientation = LinearLayout.VERTICAL
        commentsPanel.setBackgroundColor(Color.parseColor("#F0121212"))
        commentsPanel.visibility = View.GONE

        val cHead = LinearLayout(this)
        cHead.orientation = LinearLayout.HORIZONTAL
        cHead.gravity = Gravity.CENTER_VERTICAL
        cHead.setPadding(dp(14), dp(12), dp(14), dp(12))

        commentsTitle = TextView(this)
        commentsTitle.text = "التعليقات"; commentsTitle.setTextColor(Color.WHITE); commentsTitle.textSize = 16f; commentsTitle.typeface = fontBold
        cHead.addView(commentsTitle, LinearLayout.LayoutParams(0, -2, 1f))

        val closeC = ImageButton(this)
        closeC.setImageDrawable(IconDrawable(IconDrawable.BACK, Color.WHITE))
        closeC.background = null
        closeC.setOnClickListener { hideComments() }
        cHead.addView(closeC, LinearLayout.LayoutParams(dp(40), dp(40)))

        commentsPanel.addView(cHead)

        val cScroll = ScrollView(this)
        commentsList = LinearLayout(this)
        commentsList.orientation = LinearLayout.VERTICAL
        cScroll.addView(commentsList)
        commentsPanel.addView(cScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        playerRoot.addView(commentsPanel, FrameLayout.LayoutParams(dp(320), -1, Gravity.END))

        setupGestures()
    }

    private fun setupGestures() {
        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (controls.visibility == View.VISIBLE) hideControls() else showControls()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val w = gestureOverlay.width
                if (e.x < w / 3f) {
                    player?.seekBack(); hud("⏪ 10 ثوانٍ")
                } else if (e.x > 2 * w / 3f) {
                    player?.seekForward(); hud("⏩ 10 ثوانٍ")
                } else {
                    togglePlay()
                }
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (e1 == null) return false
                val w = gestureOverlay.width.toFloat()
                val h = gestureOverlay.height.toFloat()
                if (w <= 0f || h <= 0f) return false

                if (gestureMode == 0) {
                    val ddx = Math.abs(e2.x - e1.x)
                    val ddy = Math.abs(e2.y - e1.y)
                    gestureMode = if (ddx > ddy) 2 else 1
                }

                if (gestureMode == 1) {
                    if (e1.x < w / 2f) {
                        val lp = window.attributes
                        val cur = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                        val nb = (cur + dy / h).coerceIn(0.05f, 1f)
                        lp.screenBrightness = nb
                        window.attributes = lp
                        hud("🔆 السطوع " + (nb * 100).toInt() + "%")
                    } else {
                        val cur = player?.volume ?: 1f
                        val nv = (cur + dy / h).coerceIn(0f, 1f)
                        player?.volume = nv
                        hud("🔊 الصوت " + (nv * 100).toInt() + "%")
                    }
                } else if (gestureMode == 2) {
                    val p = player ?: return false
                    val dur = p.duration
                    if (dur != C.TIME_UNSET && dur > 0L) {
                        val delta = (dx / w * 60000f).toLong()
                        val np = (p.currentPosition - delta).coerceIn(0L, dur)
                        p.seekTo(np)
                        hud(fmt(np) + " / " + fmt(dur))
                    }
                }
                return true
            }

            override fun onDown(e: MotionEvent): Boolean = true
        })

        gestureOverlay.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { gestureMode = 0; v.performClick() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { gestureMode = 0; ui.postDelayed(hideHud, 800) }
            }
            gestures.onTouchEvent(e)
        }
    }

    private fun hud(msg: String) {
        hudText.text = msg; hudText.visibility = View.VISIBLE
        ui.removeCallbacks(hideHud); ui.postDelayed(hideHud, 1200)
    }

    private fun showControls() {
        controls.animate().alpha(1f).setDuration(150).withStartAction { controls.visibility = View.VISIBLE }.start()
        ui.removeCallbacks(hideControlsRunnable)
        ui.postDelayed(hideControlsRunnable, 3500)
    }

    private val hideControlsRunnable = Runnable {
        if (!commentsOpen && player?.isPlaying == true) {
            controls.animate().alpha(0f).setDuration(200).withEndAction { if (controls.alpha == 0f) controls.visibility = View.INVISIBLE }.start()
        }
    }

    private fun hideControls() {
        controls.animate().alpha(0f).setDuration(200).withEndAction { controls.visibility = View.INVISIBLE }.start()
    }

    private fun showUnlockBtn() {
        unlockBtn.visibility = View.VISIBLE
        ui.removeCallbacks(hideUnlock)
        ui.postDelayed(hideUnlock, 2500)
    }

    private fun unlockPlayer() {
        locked = false
        unlockBtn.visibility = View.GONE
        gestureOverlay.visibility = View.VISIBLE
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) { p.pause(); playPause.setImageDrawable(playDrawable) }
        else { p.play(); playPause.setImageDrawable(pauseDrawable) }
        showControls()
    }

    private fun cycleSpeed() {
        speedIdx = (speedIdx + 1) % speeds.size
        val sp = speeds[speedIdx]
        player?.setPlaybackSpeed(sp)
        speedBtn.text = if (sp == sp.toInt().toFloat()) "${sp.toInt()}x" else "${sp}x"
        showControls()
    }

    private fun setCutoutMode(always: Boolean) {
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                if (always && Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
    }

    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (on) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(window, false)
            setCutoutMode(true)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            // الصورة تملأ الشاشة كاملة بلا حواف سوداء
            aspectLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            topBar.visibility = View.GONE
            fsBtn.setImageDrawable(IconDrawable(IconDrawable.COMPRESS, Color.WHITE))
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
            setCutoutMode(false)
            aspectLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            topBar.visibility = View.VISIBLE
            fsBtn.setImageDrawable(IconDrawable(IconDrawable.EXPAND, Color.WHITE))
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && fullscreen) {
            WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun openPlayer(v: VideoItem) {
        current = v
        playerTitle.text = v.title
        commentsOpen = false
        commentsPanel.visibility = View.GONE
        commentsList.removeAllViews()
        commentsCount = 0
        expandedCommentIds.clear()
        commentBadge.text = "💬"

        playerRoot.visibility = View.VISIBLE
        controls.visibility = View.VISIBLE
        controls.alpha = 1f
        playPause.setImageDrawable(pauseDrawable)
        speedIdx = 2; speedBtn.text = "1x"

        val p = player ?: ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .also { player = it }

        prepared = false
        p.setVideoSurfaceView(surfaceView)
        p.removeListener(playerListener)
        p.addListener(playerListener)
        p.setPlaybackSpeed(1f)
        p.clearMediaItems()
        p.setMediaItem(MediaItem.fromUri(v.uri))

        val savedPos = prefs.getPosition(v.id)
        p.prepare()
        p.playWhenReady = true
        if (savedPos > 0L) {
            p.seekTo(savedPos)
        }

        showControls()
        if (pendingRestoreComments) {
            pendingRestoreComments = false
            commentsOpen = true
            commentsPanel.visibility = View.VISIBLE
            fetchComments(v)
        }
    }

    private fun closePlayer() {
        val p = player
        val c = current
        if (p != null && c != null && prepared) saveProgress(p, c)
        playerRoot.visibility = View.GONE
        if (fullscreen) setFullscreen(false)
        p?.stop()
        p?.clearMediaItems()
        prepared = false
        current = null
        commentsPanel.visibility = View.GONE
        commentsOpen = false
        applyFilter()
    }

    private fun toggleComments() {
        if (commentsOpen) hideComments() else showComments()
    }

    private fun showComments() {
        commentsOpen = true
        commentsPanel.visibility = View.VISIBLE
        val v = current ?: return
        fetchComments(v)
    }

    private fun hideComments() {
        commentsOpen = false
        commentsPanel.visibility = View.GONE
    }

    private fun fetchComments(v: VideoItem) {
        commentsList.removeAllViews()
        val loading = TextView(this)
        loading.text = "جارٍ تحميل التعليقات..."; loading.setTextColor(gray); loading.textSize = 14f
        loading.setPadding(dp(16), dp(16), dp(16), dp(16))
        commentsList.addView(loading)

        val title = v.title
        pool.execute {
            try {
                val url = "https://www.youtube.com/results?search_query=" + Uri.encode(title)
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val html = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val videoIdMatch = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"").find(html)
                if (videoIdMatch == null) {
                    ui.post { showCommentsError("لم يتم العثور على الفيديو") }
                    return@execute
                }
                val videoId = videoIdMatch.groupValues[1]
                loadRealComments(videoId)
            } catch (e: Exception) {
                ui.post { showCommentsError("تعذّر تحميل التعليقات: ${e.message}") }
            }
        }
    }

    private fun showCommentsError(msg: String) {
        commentsList.removeAllViews()
        val tv = TextView(this)
        tv.text = msg; tv.setTextColor(gray); tv.textSize = 14f
        tv.setPadding(dp(16), dp(16), dp(16), dp(16))
        commentsList.addView(tv)
    }

    // تُنفَّذ على خيط خلفي (فيها طلب شبكة)
    private fun loadRealComments(videoId: String) {
        try {
            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            val ci = CommentsInfo.getInfo(ServiceList.YouTube, videoUrl)
            val items = ci.relatedItems
            val count = ci.commentsCount
            ui.post {
                commentsCount = count
                commentBadge.text = if (count > 0) "💬 $count" else "💬"
                commentsTitle.text = "التعليقات ($count)"
                commentsList.removeAllViews()
                if (items.isEmpty()) {
                    showCommentsError("لا توجد تعليقات")
                } else {
                    for (item in items) {
                        addCommentView(item)
                    }
                }
            }
        } catch (e: Exception) {
            ui.post { showCommentsError("تعذّر تحميل التعليقات: ${e.message}") }
        }
    }

    private fun addCommentView(item: CommentsInfoItem) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(dp(14), dp(8), dp(14), dp(8))

        val author = TextView(this)
        author.text = item.uploaderName ?: "مجهول"
        author.setTextColor(red); author.textSize = 13f; author.typeface = fontBold
        row.addView(author)

        val comment = TextView(this)
        comment.text = item.commentText?.content ?: ""
        comment.setTextColor(Color.WHITE); comment.textSize = 13f
        comment.setPadding(0, dp(4), 0, dp(4))
        row.addView(comment)

        val likes = TextView(this)
        likes.text = "👍 ${item.likeCount}"
        likes.setTextColor(gray); likes.textSize = 11f
        row.addView(likes)

        commentsList.addView(row)

        val divider = View(this)
        divider.setBackgroundColor(Color.parseColor("#2A2A2A"))
        commentsList.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
    }

    override fun onPause() {
        super.onPause()
        val p = player; val c = current
        if (p != null && c != null && prepared) saveProgress(p, c)
        val playing = p != null && p.isPlaying
        var inPip = false
        if (playing && playerRoot.visibility == View.VISIBLE && Build.VERSION.SDK_INT >= 26) {
            try {
                enterPictureInPictureMode(PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9)).build())
                inPip = true
            } catch (_: Exception) {}
        }
        if (playing && !inPip) p?.pause()
    }

    override fun onResume() {
        super.onResume()
        if (!firstResume) {
            if (playerRoot.visibility == View.VISIBLE) loadVideos() else reloadVideos(false)
        }
        firstResume = false
        if (!observerRegistered) {
            try {
                contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
                observerRegistered = true
            } catch (_: Exception) {}
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) {
            player?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacks(dlTicker)
        pool.shutdown()
        try { contentResolver.unregisterContentObserver(mediaObserver) } catch (_: Exception) {}
        player?.release()
        player = null
        cache.evictAll()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            controls.visibility = View.GONE
            commentsPanel.visibility = View.GONE
        } else {
            controls.visibility = View.VISIBLE
        }
    }

    private fun treeToPath(uri: Uri): String? {
        if (Build.VERSION.SDK_INT >= 21 && DocumentsContract.isTreeUri(uri)) {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val parts = docId.split(":")
            return if (parts.size >= 2) {
                val type = parts[0]; val path = parts[1]
                if (type == "primary") Environment.getExternalStorageDirectory().path + "/" + path
                else "/storage/$type/$path"
            } else null
        }
        return null
    }

    // ───────────── التنزيلات ─────────────

    private class QualityOption(val label: String, val videoUrl: String, val audioUrl: String?, val ext: String)

    private fun startDownloadFromUrl(url: String?) {
        if (url == null) return
        Toast.makeText(this, "جارٍ جلب الجودات المتاحة...", Toast.LENGTH_SHORT).show()
        pool.execute {
            try {
                val info = StreamInfo.getInfo(ServiceList.YouTube, url)
                val options = ArrayList<QualityOption>()

                // جودات عالية: فيديو بدون صوت (H.264) + أفضل صوت M4A ثم دمجهما
                val bestAudio = info.audioStreams
                    .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == NpFormat.M4A }
                    .maxByOrNull { it.averageBitrate }
                if (bestAudio != null) {
                    info.videoOnlyStreams
                        .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                                  it.format == NpFormat.MPEG_4 &&
                                  it.codec?.startsWith("avc1") == true }
                        .groupBy { it.height }
                        .forEach { (_, list) ->
                            val best = list.maxByOrNull { it.bitrate } ?: list.first()
                            options.add(QualityOption("${best.resolution} • mp4 (دمج صوت)", best.content, bestAudio.content, "mp4"))
                        }
                }
                // جودات جاهزة بصوتها (غالبًا 360p)
                val haveHeights = options.map { it.label.substringBefore("p") }.toSet()
                info.videoStreams
                    .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                    .forEach {
                        if (it.resolution.substringBefore("p") !in haveHeights) {
                            options.add(QualityOption("${it.resolution} • ${it.format?.suffix ?: "mp4"}", it.content, null, it.format?.suffix ?: "mp4"))
                        }
                    }
                options.sortByDescending { it.label.substringBefore("p").toIntOrNull() ?: 0 }

                ui.post {
                    if (isFinishing || isDestroyed) return@post
                    if (options.isEmpty()) {
                        Toast.makeText(this, "لا توجد جودة متاحة للتحميل لهذا الفيديو", Toast.LENGTH_LONG).show()
                        return@post
                    }
                    val labels = options.map { it.label }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("اختر الجودة")
                        .setItems(labels) { _, which ->
                            val o = options[which]
                            DownloadCenter.enqueue(info.name, o.videoUrl, o.ext, o.audioUrl)
                            Toast.makeText(this, "بدأ التحميل (${DownloadCenter.SEGMENTS} أجزاء متوازية)", Toast.LENGTH_SHORT).show()
                            showDownloads()
                        }
                        .setNegativeButton("إلغاء", null)
                        .show()
                }
            } catch (e: Exception) {
                ui.post { Toast.makeText(this, "تعذّر جلب الفيديو: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun buildDownloadsPanel() {
        dlPanel = LinearLayout(this)
        dlPanel.orientation = LinearLayout.VERTICAL
        dlPanel.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val r = dp(18).toFloat()
        dlPanel.background = GradientDrawable().apply {
            setColor(Color.parseColor("#F2151515"))
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
        dlPanel.elevation = dp(16).toFloat()
        dlPanel.isClickable = true
        dlPanel.visibility = View.GONE

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(16), dp(12), dp(8), dp(8))
        val title = TextView(this)
        title.text = "التنزيلات"; title.setTextColor(Color.WHITE); title.textSize = 16f; title.typeface = fontBold
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val close = TextView(this)
        close.text = "✕"; close.setTextColor(Color.WHITE); close.textSize = 18f
        close.setPadding(dp(12), dp(6), dp(12), dp(6))
        close.setOnClickListener { buzz(it); hideDownloads() }
        head.addView(close)
        dlPanel.addView(head)

        dlEmpty = TextView(this)
        dlEmpty.text = "لا توجد تنزيلات.\nافتح فيديو في تبويب YouTube ثم اضغط زر التحميل الأحمر."
        dlEmpty.setTextColor(gray); dlEmpty.textSize = 14f; dlEmpty.gravity = Gravity.CENTER
        dlEmpty.setPadding(dp(24), dp(24), dp(24), dp(24))
        dlPanel.addView(dlEmpty, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this)
        dlList = LinearLayout(this)
        dlList.orientation = LinearLayout.VERTICAL
        dlList.setPadding(dp(8), 0, dp(8), dp(12))
        scroll.addView(dlList)
        dlPanel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        applyFont(dlPanel)
        rootFrame.addView(dlPanel, FrameLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.6f).toInt(), Gravity.BOTTOM))
    }

    private fun showDownloads() { dlPanel.visibility = View.VISIBLE; refreshDownloads() }
    private fun hideDownloads() { dlPanel.visibility = View.GONE }

    private fun createDlRow(t: DownloadTask): DlRow {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(10), dp(12), dp(10))
        root.background = GradientDrawable().apply { setColor(Color.parseColor("#1F1F1F")); cornerRadius = dp(12).toFloat() }
        root.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) }

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = t.title; title.setTextColor(Color.WHITE); title.textSize = 13f; title.typeface = fontBold
        title.isSingleLine = true; title.ellipsize = android.text.TextUtils.TruncateAt.END
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val action = TextView(this)
        action.setTextColor(Color.WHITE); action.textSize = 12f
        action.setPadding(dp(12), dp(4), dp(12), dp(4))
        action.background = GradientDrawable().apply { setColor(Color.parseColor("#44FFFFFF")); cornerRadius = dp(12).toFloat() }
        action.setOnClickListener {
            buzz(it)
            if (t.isActive) DownloadCenter.cancel(t) else { DownloadCenter.remove(t); refreshDownloads() }
        }
        top.addView(action, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        root.addView(top)

        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 1000
        bar.progressTintList = android.content.res.ColorStateList.valueOf(red)
        bar.indeterminateTintList = android.content.res.ColorStateList.valueOf(red)
        root.addView(bar, LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(8) })

        val status = TextView(this)
        status.setTextColor(gray); status.textSize = 11f
        status.setPadding(0, dp(6), 0, 0)
        root.addView(status)

        applyFont(root)
        return DlRow(root, title, bar, status, action)
    }

    private fun fmtSize(b: Long): String =
        if (b >= 1_048_576L) String.format(Locale.US, "%.1f MB", b / 1048576.0)
        else String.format(Locale.US, "%d KB", b / 1024)

    private fun updateDlRow(r: DlRow, t: DownloadTask) {
        val done = t.downloaded.get()
        val total = t.total
        val now = System.currentTimeMillis()
        if (t.state == DownloadTask.DOWNLOADING) {
            if (r.lastTime == 0L) { r.lastTime = now; r.lastBytes = done }
            else if (now - r.lastTime >= 1000) {
                r.speed = (done - r.lastBytes) * 1000 / (now - r.lastTime)
                r.lastBytes = done; r.lastTime = now
            }
        }
        r.action.text = if (t.isActive) "إلغاء" else "إزالة"
        when (t.state) {
            DownloadTask.QUEUED -> {
                r.bar.isIndeterminate = true
                r.status.text = "جارٍ التحضير..."
            }
            DownloadTask.DOWNLOADING -> {
                val parts = if (t.segments > 1) "${t.segments} أجزاء" else "اتصال واحد"
                if (total > 0) {
                    r.bar.isIndeterminate = false
                    r.bar.progress = (done * 1000 / total).toInt().coerceIn(0, 1000)
                    r.status.text = "${done * 100 / total}% • ${fmtSize(done)} / ${fmtSize(total)} • ${fmtSize(r.speed)}/ث • $parts"
                } else {
                    r.bar.isIndeterminate = true
                    r.status.text = "${fmtSize(done)} • ${fmtSize(r.speed)}/ث • $parts"
                }
            }
            DownloadTask.SAVING -> {
                r.bar.isIndeterminate = true
                r.status.text = if (t.audioUrl != null) "جارٍ دمج الصوت والصورة وحفظ الملف..." else "جارٍ الحفظ في المعرض..."
            }
            DownloadTask.DONE -> {
                r.bar.isIndeterminate = false; r.bar.progress = 1000
                r.status.text = "اكتمل ✓ (Movies/TubeTV)"
            }
            DownloadTask.FAILED -> {
                r.bar.isIndeterminate = false
                r.status.text = "فشل: ${t.error ?: ""}"
            }
            DownloadTask.CANCELED -> {
                r.bar.isIndeterminate = false
                r.status.text = "أُلغي"
            }
        }
    }

    private fun refreshDownloads() {
        val tasks = DownloadCenter.tasks
        val active = tasks.count { it.isActive }
        for (t in tasks) { if (t.state == DownloadTask.DONE && notifiedDone.add(t.id)) loadVideos() }
        dlBadge.visibility = if (active > 0) View.VISIBLE else View.GONE
        dlBadge.text = active.toString()
        if (dlPanel.visibility != View.VISIBLE) return

        for (t in tasks.reversed()) {
            var row = dlRows[t.id]
            if (row == null) {
                row = createDlRow(t)
                dlRows[t.id] = row
                dlList.addView(row.root, 0)
            }
            updateDlRow(row, t)
        }
        val ids = tasks.map { it.id }.toSet()
        for (id in dlRows.keys.filter { it !in ids }) {
            dlRows.remove(id)?.let { dlList.removeView(it.root) }
        }
        dlEmpty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
    }

    inner class VideoAdapter : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int): Long = shown[position].id
        override fun hasStableIds(): Boolean = true

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = shown[position]
            val ctx = this@MainActivity

            val card = LinearLayout(ctx)
            card.orientation = LinearLayout.VERTICAL
            card.layoutDirection = View.LAYOUT_DIRECTION_RTL
            card.setPadding(dp(8), dp(8), dp(8), dp(8))
            card.background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A")); cornerRadius = dp(14).toFloat()
            }
            card.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(8), dp(6), dp(8), dp(6)) }

            // صورة كبيرة بعرض البطاقة كاملة (16:9)
            val thumb = ImageView(ctx)
            thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            thumb.clipToOutline = true
            thumb.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                }
            }
            val thumbWidth = resources.displayMetrics.widthPixels - dp(32)
            card.addView(thumb, LinearLayout.LayoutParams(thumbWidth, thumbWidth * 9 / 16))
            loadThumb(v, thumb)

            val bottom = LinearLayout(ctx)
            bottom.orientation = LinearLayout.HORIZONTAL
            bottom.gravity = Gravity.CENTER_VERTICAL
            bottom.layoutDirection = View.LAYOUT_DIRECTION_RTL
            card.addView(bottom, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

            val info = LinearLayout(ctx)
            info.orientation = LinearLayout.VERTICAL
            info.gravity = Gravity.CENTER_VERTICAL
            bottom.addView(info, LinearLayout.LayoutParams(0, -2, 1f))

            val title = TextView(ctx)
            title.text = v.title
            title.setTextColor(Color.WHITE); title.textSize = 15f; title.typeface = fontBold
            title.maxLines = 2; title.ellipsize = android.text.TextUtils.TruncateAt.END
            info.addView(title)

            val duration = TextView(ctx)
            duration.text = fmt(v.duration)
            duration.setTextColor(gray); duration.textSize = 12f
            duration.setPadding(0, dp(4), 0, 0)
            info.addView(duration)

            val progress = prefs.getPosition(v.id)
            if (progress > 0L && v.duration > 0L) {
                val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
                bar.max = v.duration.toInt()
                bar.progress = progress.toInt()
                bar.progressTintList = android.content.res.ColorStateList.valueOf(red)
                info.addView(bar, LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(6) })
            }

            val menuBtn = ImageButton(ctx)
            menuBtn.setImageDrawable(IconDrawable(IconDrawable.TRASH, gray))
            menuBtn.background = null
            menuBtn.isFocusable = false
            menuBtn.setOnClickListener { buzz(it); showDeleteDialog(v) }
            bottom.addView(menuBtn, LinearLayout.LayoutParams(dp(40), dp(40)))

            applyFont(card)
            return card
        }
    }

    private fun loadThumb(v: VideoItem, iv: ImageView) {
        iv.tag = v.id
        val cached = cache.get(v.id)
        if (cached != null) { iv.setImageBitmap(cached); return }
        iv.setImageDrawable(GradientDrawable().apply { setColor(Color.parseColor("#2A2A2A")); cornerRadius = dp(8).toFloat() })
        pool.execute {
            val file = File(thumbDir, "${v.id}.jpg")
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(file.path)
                if (bmp != null) {
                    cache.put(v.id, bmp)
                    ui.post { if (iv.tag == v.id) iv.setImageBitmap(bmp) }
                    return@execute
                }
            }
            try {
                val r = MediaMetadataRetriever()
                r.setDataSource(this@MainActivity, v.uri)
                val frame = r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                r.release()
                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(frame, thumbW, thumbH, true)
                    cache.put(v.id, scaled)
                    ui.post { if (iv.tag == v.id) iv.setImageBitmap(scaled) }
                    try {
                        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    private fun showDeleteDialog(v: VideoItem) {
        AlertDialog.Builder(this)
            .setTitle("حذف الفيديو")
            .setMessage("هل أنت متأكد من حذف \"${v.title}\"؟")
            .setPositiveButton("حذف") { _, _ -> deleteVideo(v) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun deleteVideo(v: VideoItem) {
        pendingDelete = v
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val deleteRequest = MediaStore.createDeleteRequest(contentResolver, listOf(v.uri))
                deleteNeedsRetry = false
                deleteLauncher.launch(IntentSenderRequest.Builder(deleteRequest.intentSender).build())
            } else {
                if (contentResolver.delete(v.uri, null, null) > 0) afterDeleted(v)
                else Toast.makeText(this, "تعذّر الحذف", Toast.LENGTH_SHORT).show()
            }
        } catch (e: RecoverableSecurityException) {
            deleteNeedsRetry = true
            try {
                deleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
            } catch (_: Exception) {
                Toast.makeText(this, "تعذّر الحذف", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "تعذّر الحذف", Toast.LENGTH_SHORT).show()
        }
    }

    class Prefs(context: Context) {
        private val sp: SharedPreferences = context.getSharedPreferences("tube_pro", Context.MODE_PRIVATE)

        var folderPath: String
            get() = sp.getString("folder", "") ?: ""
            set(v) { sp.edit().putString("folder", v).apply() }

        var lastTab: Int
            get() = sp.getInt("tab", 0)
            set(v) { sp.edit().putInt("tab", v).apply() }

        fun savePosition(id: Long, pos: Long, dur: Long, watched: Boolean) {
            val e = sp.edit()
            e.putLong("pos_$id", pos)
            if (watched) e.putBoolean("watched_$id", true)
            e.apply()
        }

        fun getPosition(id: Long): Long = sp.getLong("pos_$id", 0L)
        fun isWatched(id: Long): Boolean = sp.getBoolean("watched_$id", false)
        fun removePosition(id: Long) {
            sp.edit().remove("pos_$id").remove("watched_$id").apply()
        }
    }
}

class YoutubeTab(
    private val activity: MainActivity,
    private val getFolderPath: () -> String,
    private val onReload: () -> Unit,
    private val onPlay: (VideoItem) -> Unit,
    private val onDownload: (String?) -> Unit
) {
    val view: FrameLayout = FrameLayout(activity)
    private var web: WebView? = null
    private var initialized = false
    private var fab: ImageButton? = null

    private fun isWatchUrl(url: String?): Boolean =
        url != null && (url.contains("/watch") || url.contains("youtu.be/") || url.contains("/shorts/"))

    private fun updateFab(url: String?) {
        fab?.visibility = if (isWatchUrl(url)) View.VISIBLE else View.GONE
    }

    fun isInitialized(): Boolean = initialized

    fun reload() { web?.reload() }

    fun onShown() {
        if (!initialized) {
            initialized = true
            setupWebView()
        }
        web?.onResume()
    }

    fun onHidden() {
        web?.onPause()
    }

    fun handleBack(): Boolean {
        val w = web ?: return false
        if (w.canGoBack()) { w.goBack(); return true }
        return false
    }

    private fun setupWebView() {
        val w = WebView(activity)
        web = w
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.settings.loadWithOverviewMode = true
        w.settings.useWideViewPort = true

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)

        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView?, newProgress: Int) {}
        }
        w.webViewClient = object : WebViewClient() {
            @Suppress("OVERRIDE_DEPRECATION")
            override fun doUpdateVisitedHistory(v: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(v, url, isReload)
                updateFab(url)
            }
            override fun onPageFinished(v: WebView?, url: String?) {
                super.onPageFinished(v, url)
                updateFab(url)
            }
            override fun shouldOverrideUrlLoading(v: WebView?, url: String?): Boolean {
                if (url != null && (url.contains("youtube.com/watch") || url.contains("youtu.be/"))) {
                    v?.loadUrl(url)
                    return true
                }
                return false
            }
        }
        w.loadUrl("https://m.youtube.com")
        view.addView(w, FrameLayout.LayoutParams(-1, -1))

        val d = activity.resources.displayMetrics.density
        val b = ImageButton(activity)
        b.setImageDrawable(IconDrawable(IconDrawable.DOWNLOAD, Color.WHITE))
        b.background = GradientDrawable().apply { setColor(Color.parseColor("#FF0000")); shape = GradientDrawable.OVAL }
        b.elevation = 8 * d
        val pad = (15 * d).toInt()
        b.setPadding(pad, pad, pad, pad)
        b.visibility = View.GONE
        b.setOnClickListener { onDownload(w.url) }
        val m = (16 * d).toInt()
        view.addView(b, FrameLayout.LayoutParams((56 * d).toInt(), (56 * d).toInt(), Gravity.BOTTOM or Gravity.END).apply { setMargins(m, m, m, m) })
        fab = b
    }
}
