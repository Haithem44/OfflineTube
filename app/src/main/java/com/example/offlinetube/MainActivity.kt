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

data class VideoItem(val title: String, val channel: String, val uri: Uri)

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
    val videoList = remember { mutableStateListOf<VideoItem>() }
    var selectedVideo by remember { mutableStateOf<Uri?>(null) }

    // منتقي ملفات يتيح اختيار عدة فيديوهات دفعة واحدة
    constval multipleFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        for (uri in uris) {
            videoList.add(
                VideoItem(
                    title = "فيديو محلي ${videoList.size + 1}",
                    channel = "OfflineTube Studio",
                    uri = uri
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F)) // خلفية يوتيوب الداكنة الرسمية
    ) {
        // شريط التنقل العلوي بتصميم يوتيوب
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
                fontSize = 22.sp,
                style = MaterialTheme.typography.titleLarge
            )
            Button(
                onClick = { multipleFilesLauncher.launch("video/*") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)) // أحمر يوتيوب المميز
            ) {
                Text(text = "إضافة فيديوهات دفعة واحدة 📥", color = Color.White)
            }
        }

        // عرض مشغل الفيديو في الأعلى عند الضغط على أي فيديو
        selectedVideo?.let { uri ->
            PlayerBox(
                videoUri = uri,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            HorizontalDivider(color = Color(0xFF272727))
        }

        // قائمة الفيديوهات بتصميم شبيه بيوتيوب تماماً
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
                    // صندوق الصورة المصغرة للفيديو
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(75.dp)
                            .background(Color(0xFF272727)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "▶️ فيديو", color = Color.LightGray, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // تفاصيل الفيديو (العنوان، اسم القناة، ووقت النشر)
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
                            text = "${item.channel} • مشاهدة الآن",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
