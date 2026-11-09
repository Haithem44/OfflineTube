package com.example.offlinetube

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
    }
}

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

data class Video(val id: Long, val title: String, val durationMs: Long, val uri: Uri)

fun loadVideos(ctx: Context): List<Video> {
    val out = mutableListOf<Video>()
    val col = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val proj = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION
    )
    ctx.contentResolver.query(col, proj, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
        while (c.moveToNext()) {
            val id = c.getLong(0)
            out += Video(id, c.getString(1).substringBeforeLast('.'), c.getLong(2), ContentUris.withAppendedId(col, id))
        }
    }
    return out
}

fun fmt(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var videos by remember { mutableStateOf(emptyList<Video>()) }
    var current by remember { mutableStateOf<Video?>(null) }
    var query by remember { mutableStateOf("") }
    var fullscreen by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    val prefs = remember { ctx.getSharedPreferences("positions", Context.MODE_PRIVATE) }
    val loader = remember { ImageLoader.Builder(ctx).components { add(VideoFrameDecoder.Factory()) }.build() }

    DisposableEffect(Unit) {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({ controller = future.get() }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }

    LaunchedEffect(granted) {
        if (granted) {
            videos = withContext(Dispatchers.IO) { loadVideos(ctx) }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else launcher.launch(perm)
    }

    fun savePos() {
        val v = current
        val c = controller
        if (v != null && c != null) {
            val pos = if (c.playbackState == Player.STATE_ENDED) 0L else c.currentPosition
            prefs.edit().putLong("p${v.id}", pos).apply()
        }
    }

    fun play(v: Video) {
        val c = controller ?: return
        savePos()
        val item = MediaItem.Builder()
            .setMediaId(v.id.toString())
            .setUri(v.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(v.title).build())
            .build()
        c.setMediaItem(item, prefs.getLong("p${v.id}", 0L))
        c.prepare()
        c.play()
        current = v
    }

    LaunchedEffect(current) {
        while (true) {
            delay(5000)
            savePos()
        }
    }

    LaunchedEffect(fullscreen) {
        val act = ctx.findActivity() ?: return@LaunchedEffect
        val ctl = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (fullscreen) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ctl.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(enabled = current != null) {
        if (fullscreen) {
            fullscreen = false
        } else {
            savePos()
            controller?.pause()
            current = null
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().then(
                if (fullscreen) Modifier else Modifier.statusBarsPadding().navigationBarsPadding()
            )
        ) {
            val cur = current
            val c = controller
            if (cur != null && c != null) {
                PlayerBox(c, fullscreen) { fullscreen = !fullscreen }
            }
            if (!fullscreen) {
                if (cur == null) {
                    Text("OfflineTube", fontSize = 22.sp, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text("بحث") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    )
                } else {
                    Text(cur.title, fontSize = 18.sp, modifier = Modifier.padding(12.dp))
                    HorizontalDivider()
                }
                if (!granted) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Button(onClick = { launcher.launch(perm) }) { Text("السماح بالوصول إلى الفيديوهات") }
                    }
                } else {
                    val shown = videos.filter { it.title.contains(query, ignoreCase = true) && it.id != current?.id }
                    if (shown.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("لا توجد فيديوهات") }
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(shown, key = { it.id }) { v -> VideoCard(v, loader) { play(v) } }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoCard(v: Video, loader: ImageLoader, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 14.dp)) {
        Box {
            AsyncImage(
                model = v.uri, imageLoader = loader, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.DarkGray)
            )
            Text(
                fmt(v.durationMs), fontSize = 12.sp, color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
