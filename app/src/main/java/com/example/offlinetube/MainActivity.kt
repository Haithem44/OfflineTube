package com.example.offlinetube

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }

    // منتقي الملفات الشامل لجميع الملفات
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedFileUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "OfflineTube App",
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = {
            // السماح باختيار أي ملف من الجهاز بدون قيود
            filePickerLauncher.launch("*/*")
        }) {
            Text(text = "اختر ملفاً من الجهاز")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (selectedFileUri != null) {
            Text(
                text = "تم اختيار الملف بنجاح ✅",
                fontSize = 14.sp
            )
        } else {
            Text(
                text = "لم يتم اختيار أي ملف بعد",
                fontSize = 14.sp
            )
        }
    }
}
package com.example.offlinetube

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder

data class Video(val uri: String, val name: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            MaterialTheme { Surface(Modifier.fillMaxSize()) { App() } }
        }
    }
}

tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun loadVideos(context: Context): List<Video> {
    val prefs = context.getSharedPreferences("videos", Context.MODE_PRIVATE)
    val raw = prefs.getString("list", "") ?: ""
    return raw.split("\n").filter { it.isNotBlank() }.map { Video(it, displayName(context, it)) }
}

fun saveVideos(context: Context, list: List<Video>) {
    context.getSharedPreferences("videos", Context.MODE_PRIVATE)
        .edit().putString("list", list.joinToString("\n") { it.uri }).apply()
}

fun displayName(context: Context, uriStr: String): String {
    return try {
        context.contentResolver.query(Uri.parse(uriStr), null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        } ?: "فيديو"
    } catch (e: Exception) { "فيديو" }
}

@Composable
fun App() {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context.findActivity()

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var videos by remember { mutableStateOf(loadVideos(context)) }
    var current by remember { mutableStateOf(-1) }
    var fullscreen by remember { mutableStateOf(false) }

    val loader = remember {
        ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
    }

    // الاتصال بخدمة التشغيل
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ controller = future.get() }, ContextCompat.getMainExecutor(context))
        onDispose { MediaController.releaseFuture(future); controller = null }
    }

    // متابعة الفيديو الحالي
    DisposableEffect(controller) {
        val c = controller
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                current = c?.currentMediaItemIndex ?: -1
            }
        }
        c?.addListener(listener)
        if (c != null && c.mediaItemCount > 0) current = c.currentMediaItemIndex
        onDispose { c?.removeListener(listener) }
    }

    // ملء الشاشة
    LaunchedEffect(fullscreen) {
        val act = activity ?: return@LaunchedEffect
        val ctrl = WindowCompat.getInsetsController(act.window, view)
        if (fullscreen) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ctrl.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    BackHandler(enabled = fullscreen) { fullscreen = false }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val added = uris.mapNotNull { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val s = uri.toString()
            if (videos.any { it.uri == s }) null else Video(s, displayName(context, s))
        }
        if (added.isNotEmpty()) {
            videos = videos + added
            saveVideos(context, videos)
        }
    }

    fun play(index: Int) {
        val c = controller ?: return
        c.setMediaItems(
            videos.map {
                MediaItem.Builder().setUri(it.uri)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder().setTitle(it.name).build()
                    ).build()
            }, index, 0L
        )
        c.prepare()
        c.play()
        current = index
    }

    val rootModifier = if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxSize().systemBarsPadding()

    Column(rootModifier) {
        if (current >= 0 && controller != null) {
            AndroidView(
                modifier = if (fullscreen) Modifier.fillMaxSize()
                else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        keepScreenOn = true
                        player = controller
                        setFullscreenButtonClickListener { fs -> fullscreen = fs }
                    }
                },
                update = { it.player = controller }
            )
        }

        if (!fullscreen) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("OfflineTube", style = MaterialTheme.typography.titleLarge)
                Button(onClick = { picker.launch(arrayOf("video/*")) }) {
                    Text("إضافة فيديوهات")
                }
            }

            if (videos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد فيديوهات، اضغط إضافة فيديوهات")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(videos) { index, v ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { play(index) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = Uri.parse(v.uri),
                                imageLoader = loader,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.width(120.dp).aspectRatio(16f / 9f)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                v.name,
                                maxLines = 2,
                                color = if (index == current) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
