package com.example.offlinetube

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ألوان اليوتيوب
val YouTubeBlack = Color(0xFF0F0F0F)
val YouTubeRed = Color(0xFFFF0000)
val YouTubeGray = Color(0xFFAAAAAA)

data class VideoItem(
    val id: String, 
    val title: String, 
    val channel: String, 
    val views: String, 
    val timeAgo: String, 
    val uri: Uri? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = YouTubeBlack
                ) {
                    YouTubeCloneScreen()
                }
            }
        }
    }
}

@Composable
fun YouTubeCloneScreen() {
    // بيانات وهمية للتجربة (يمكنك ربطها بملفاتك الحقيقية لاحقاً)
    val videoList = remember { 
        mutableStateListOf(
            VideoItem("1", "بعد استخدام شهر: 8 عيوب قاتلة في IPHONE 18 Pro Max", "Simon's Tech", "376 ألف مشاهدة", "قبل 3 ساعات"),
            VideoItem("2", "مراجعة STALKER 2 | لعبة معلمين 🔥", "Gamiew", "55 ألف مشاهدة", "قبل سنة"),
            VideoItem("3", "كيف تصنع تطبيقاً مثل يوتيوب من الصفر", "OfflineTube Studio", "12 ألف مشاهدة", "قبل يوم")
        ) 
    }

    // زر اختيار الفيديوهات
    val multipleFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        for (uri in uris) {
            videoList.add(
                VideoItem(
                    id = uri.toString(),
                    title = "فيديو محلي ${videoList.size + 1}",
                    channel = "قناتي المحلية",
                    views = "جديد",
                    timeAgo = "الآن",
                    uri = uri
                )
            )
        }
    }

    Scaffold(
        containerColor = YouTubeBlack,
        topBar = { YouTubeTopBar() },
        bottomBar = { YouTubeBottomBar() },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { multipleFilesLauncher.launch("video/*") },
                containerColor = YouTubeRed,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Video")
            }
        }
    ) { paddingValues ->
        // قائمة الفيديوهات
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(videoList) { video ->
                YouTubeVideoCard(video)
            }
        }
    }
}

@Composable
fun YouTubeTopBar() {
    Column(modifier = Modifier.background(YouTubeBlack)) {
        // شريط الشعار
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // أيقونة اليوتيوب
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "YouTube",
                tint = YouTubeRed,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "YouTube",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White)
            Spacer(modifier = Modifier.width(16.dp))
            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
        }

        // شريط التصنيفات (الرئيسية، اشتراكات...)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            TopBarTab("الرئيسية", isSelected = true)
            TopBarTab("الاشتراكات", isSelected = false)
            TopBarTab("موسيقى", isSelected = false)
            TopBarTab("مباشر", isSelected = false)
        }
    }
}

@Composable
fun TopBarTab(text: String, isSelected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            color = if (isSelected) Color.White else YouTubeGray,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        if (isSelected) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(2.dp)
                    .background(YouTubeRed, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
fun YouTubeVideoCard(video: VideoItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .clickable { /* فتح الفيديو */ }
    ) {
        // الصورة المصغرة (Thumbnail)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f) // نسبة أبعاد صورة اليوتيوب
                .background(Color.DarkGray) // لون مؤقت في حال عدم وجود صورة
        ) {
            // هنا يمكنك إضافة كود لعرض الصورة (Coil أو Glide)
            // Image(painter = ..., contentDescription = null, contentScale = ContentScale.Crop)
            
            // شارة مدة الفيديو (8:15)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(text = "8:15", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // تفاصيل الفيديو
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp)
        ) {
            // صورة القناة الدائرية
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // النصوص (العنوان، القناة، المشاهدات)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${video.channel} • ${video.views} • ${video.timeAgo}",
                    color = YouTubeGray,
                    fontSize = 13.sp
                )
            }

            // زر الخيارات الثلاث نقاط
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun YouTubeBottomBar() {
    NavigationBar(
        containerColor = YouTubeBlack,
        tonalElevation = 0.dp,
        modifier = Modifier.border(width = 0.5.dp, color = Color.DarkGray)
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("الرئيسية", fontSize = 10.sp) },
            selected = true,
            onClick = { },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color.White,
                unselectedIconColor = YouTubeGray,
                unselectedTextColor = YouTubeGray,
                indicatorColor = Color.Transparent // إزالة الدائرة التي تظهر عند التحديد
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Shorts") },
            label = { Text("Shorts", fontSize = 10.sp) },
            selected = false,
            onClick = { },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = YouTubeGray,
                unselectedTextColor = YouTubeGray,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.AddCircle, contentDescription = "Add") },
            label = { Text("", fontSize = 10.sp) },
            selected = false,
            onClick = { },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = YouTubeGray,
                unselectedTextColor = YouTubeGray,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Subscriptions, contentDescription = "Subscriptions") },
            label = { Text("الاشتراكات", fontSize = 10.sp) },
            selected = false,
            onClick = { },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = YouTubeGray,
                unselectedTextColor = YouTubeGray,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Library") },
            label = { Text("المكتبة", fontSize = 10.sp) },
            selected = false,
            onClick = { },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = YouTubeGray,
                unselectedTextColor = YouTubeGray,
                indicatorColor = Color.Transparent
            )
        )
    }
}
