package dev.shike

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import dev.shike.data.*
import dev.shike.ui.*
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private var backgroundAt = 0L
    var locked by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShikeApp(
                (application as ShikeApplication).repository,
                this,
                intent.getStringExtra("itemId"),
            )
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (backgroundAt > 0 && SystemClock.elapsedRealtime() - backgroundAt > 30000) locked = true
    }
}
