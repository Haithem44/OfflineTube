package com.example.offlinetube

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun PlayerBox(videoUri: Uri, modifier: Modifier = Modifier) {
    var isPlaying by remember { mutableStateOf(true) }
    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // مشغل الوسائط للفيديو والصوت
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
            factory = { context ->
                VideoView(context).apply {
                    setVideoURI(videoUri)
                    setOnPreparedListener { mp ->
                        mp.start()
                        isPlaying = true
                    }
                    videoViewInstance = this
                }
            },
            update = { videoView ->
                if (videoViewInstance != videoView) {
                    videoView.setVideoURI(videoUri)
                    videoView.start()
                    videoViewInstance = videoView
                    isPlaying = true
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // أزرار التحكم في التشغيل
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                videoViewInstance?.let { view ->
                    if (view.isPlaying) {
                        view.pause()
                        isPlaying = false
                    } else {
                        view.start()
                        isPlaying = true
                    }
                }
            }) {
                Text(text = if (isPlaying) "إيقاف مؤقت ⏸️" else "تشغيل ▶️")
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(onClick = {
                videoViewInstance?.let { view ->
                    view.seekTo(0)
                    view.start()
                    isPlaying = true
                }
            }) {
                Text(text = "إعادة 🔄")
            }
        }
    }
}
