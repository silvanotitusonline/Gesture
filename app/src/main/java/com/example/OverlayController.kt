package com.example

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Controller for the System Overlay UI.
 * Manages the WindowManager setup and Jetpack Compose lifecycle.
 */
class OverlayController : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView

    private var isExecuting = mutableStateOf(false)
    private var layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 100
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayController)
            setViewTreeSavedStateRegistryOwner(this@OverlayController)
            
            setContent {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides this@OverlayController
                ) {
                    OverlayContent(
                        isExecuting = isExecuting.value,
                        onTrigger = { isExecuting.value = true },
                        onAbort = {
                            isExecuting.value = false
                            // Robust abort: clear reasoning context placeholder
                        },
                        onDrag = { dx, dy ->
                            layoutParams.x += dx.toInt()
                            layoutParams.y += dy.toInt()
                            windowManager.updateViewLayout(this, layoutParams)
                        }
                    )
                }
            }
        }
        
        windowManager.addView(composeView, layoutParams)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (::composeView.isInitialized) {
            windowManager.removeView(composeView)
        }
        store.clear()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store
}

@Composable
fun OverlayContent(
    isExecuting: Boolean,
    onTrigger: () -> Unit,
    onAbort: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    if (!isExecuting) {
        // High-profile, low-profile trigger button (System-level look)
        Box(
            modifier = Modifier
                .size(56.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF6750A4), Color(0xFF381E72))
                    )
                )
                .clickable { onTrigger() }
                .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Trigger Agent",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    } else {
        // Status Overlay HUD
        Column(
            modifier = Modifier
                .width(280.dp)
                .shadow(16.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1C1B1F).copy(alpha = 0.95f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPulsar()
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("AGENT EXECUTING", color = Color(0xFFD0BCFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Perceiving View Tree...", color = Color.White, fontSize = 12.sp)
                }
            }

            // Reasoning Terminal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "> Initializing MCP Router...\n> Building UI Payload...\n> Waiting for Reasoning...",
                    color = Color(0xFF4CAF50),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Security Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SecurityTag(icon = Icons.Default.Lock, text = "SECURE_MODE")
                Text("EPHEMERAL", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
            }

            // Abort
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF2B8B5))
                    .clickable { onAbort() },
                contentAlignment = Alignment.Center
            ) {
                Text("TAP TO ABORT", color = Color(0xFF601410), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun StatusPulsar() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "alpha"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(24.dp).scale(scale).background(Color(0xFFD0BCFF).copy(alpha = alpha), CircleShape))
        Box(Modifier.size(12.dp).background(Color(0xFFD0BCFF), CircleShape))
    }
}

@Composable
fun SecurityTag(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = Color(0xFF4CAF50), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
