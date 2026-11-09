package com.example.offlinetube

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

val YouTubeBlack = Color(0xFF0F0F0F)
val YouTubeRed = Color(0xFFFF0000)
val YouTubeGray = Color(0xFFAAAAAA)

data class VideoItem(val id: String, val title: String, val uri: Uri)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = YouTubeBlack) {
                    YouTubeCloneScreen()
                }
            }
        }
    }
}

@Composable
fun YouTubeCloneScreen() {
    val videoList = remember { mutableStateListOf<VideoItem>() }
    var playingVideoUri by remember { mutableStateOf<Uri?>(null) }

    val multipleFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        for (uri in uris) {
            // هنا نقوم بحفظ صلاحية الوصول للملف لمنع الفصل
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            val contentResolver = LocalContext.current.contentResolver
            try {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: SecurityException) {
                // تجاهل إذا لم يدعم
            }
            
            videoList.add(
                VideoItem(id = uri.toString(), title = "فيديو محلي ${videoList.size + 1}", uri = uri)
            )
        }
    }

    if (playingVideoUri != null) {
        VideoPlayerScreen(videoUri = playingVideoUri!!, onBack = { playingVideoUri = null })
    } else {
        Scaffold(
            containerColor = YouTubeBlack,
            topBar = { YouTubeTopBar() },
            bottomBar = { YouTubeBottomBar() },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { multipleFilesLauncher.launch("video/*") },
                    containerColor = YouTubeRed, contentColor = Color.White
                ) { Icon(Icons.Default.Add, contentDescription = "Add Video") }
            }
        ) { paddingValues ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                items(videoList) { video ->
                    YouTubeVideoCard(video) { playingVideoUri = video.uri }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerScreen(videoUri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) }
        }
        
        // استخدام المشغل المخصص الجديد
        AndroidView(
            factory = { ctx ->
                CustomPlayerView(ctx).apply {
                    setPlayer(exoPlayer)
                }
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        )
    }
}

// (باقي الدوال مثل YouTubeTopBar, YouTubeBottomBar, YouTubeVideoCard اتركها كما هي من الكود السابق)
