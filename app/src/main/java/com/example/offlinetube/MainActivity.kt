package com.example.offlinetube

import android.Manifest
import android.content.ContentUris
import android.content.Context
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
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

@Composable
fun App() {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var videos by remember { mutableStateOf(emptyList<Video>()) }
    var current by remember { mutableStateOf<Video?>(null) }
    var query by remember { mutableStateOf("") }
    val loader = remember { ImageLoader.Builder(ctx).components { add(VideoFrameDecoder.Factory()) }.build() }

    LaunchedEffect(granted) {
        if (granted) videos = withContext(Dispatchers.IO) { loadVideos(ctx) } else launcher.launch(perm)
    }
    BackHandler(enabled = current != null) { current = null }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            val cur = current
            if (cur == null) {
                Text("OfflineTube", fontSize = 22.sp, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("بحث") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
            } else {
                key(cur.id) { PlayerBox(cur) }
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
                    items(shown, key = { it.id }) { v -> VideoCard(v, loader) { current = v } }
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
                    .clip(RoundedCornerShape(4.dp)).background(Color(0xCC000000)).padding(4.dp, 2.dp)
            )
        }
        Text(v.title, maxLines = 2, modifier = Modifier.padding(12.dp, 8.dp))
    }
}

@Composable
fun PlayerBox(v: Video) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("positions", Context.MODE_PRIVATE) }
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(v.uri))
            seekTo(prefs.getLong("p${v.id}", 0L))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val pos = if (player.playbackState == Player.STATE_ENDED) 0L else player.currentPosition
            prefs.edit().putLong("p${v.id}", pos).apply()
            player.release()
        }
    }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    )
}
