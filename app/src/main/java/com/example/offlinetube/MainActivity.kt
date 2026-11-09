package com.example.offlinetube

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class VideoItem(val title: String, val channel: String, val uri: Uri?)

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
    var videoList = remember { mutableStateListOf<VideoItem>() }
    var selectedVideo by remember { mutableStateOf<Uri?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            videoList.add(VideoItem(title = "فيديو محلي جديد من الجهاز", channel = "OfflineTube Channel", uri = it))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // لون خلفية تشبه يوتيوب الداكن
    ) {
        // شريط العنوان العلوي
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "▶️ OfflineTube",
                color = Color.White,
                fontSize = 20.sp
            )
            Button(onClick = { filePickerLauncher.launch("video/*") }) {
                Text(text = "إضافة فيديو +")
            }
        }

        // إذا تم اختيار فيديو للعرض، يظهر المشغل في الأعلى
        selectedVideo?.let { uri ->
            PlayerBox(
                videoUri = uri,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            HorizontalDivider(color = Color.DarkGray)
        }

        // قائمة الفيديوهات على شكل يوتيوب
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(videoList) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedVideo = item.uri }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // صندوق مصغر يمثل الصورة المصغرة للفيديو
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(70.dp)
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "▶️", color = Color.White)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${item.channel} • قبل قليل",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
