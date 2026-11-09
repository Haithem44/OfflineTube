package com.example.offlinetube

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView

@Composable
fun PlayerBox(player: Player, fullscreen: Boolean, onToggle: () -> Unit) {
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                this.player = player
                setFullscreenButtonClickListener { onToggle() }
            }
        },
        modifier = if (fullscreen) Modifier.fillMaxSize()
        else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    )
}
