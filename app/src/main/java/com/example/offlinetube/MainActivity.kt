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

    // منتقي الملفات المتوافق مع أندرويد الحديث
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
            // فتح واجهة اختيار ملفات الفيديو (يمكنك تغيير "video/*" إلى "*/*" لاختيار أي ملف)
            filePickerLauncher.launch("video/*")
        }) {
            Text(text = "اختر فيديو من الجهاز")
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
