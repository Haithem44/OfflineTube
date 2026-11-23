package com.haithem44.offlinetube

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
            @Suppress("DEPRECATION")
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
