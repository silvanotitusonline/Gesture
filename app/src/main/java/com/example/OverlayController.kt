package com.example

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Overlay Service that renders a floating UI using Jetpack Compose.
 * Acts as the visual interface and manual abort trigger.
 */
class OverlayController : Service(), SavedStateRegistryOwner, ViewModelStoreOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    
    // Manage lifecycle for Jetpack Compose in a Service
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    private var isExecuting by mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayController)
            setViewTreeSavedStateRegistryOwner(this@OverlayController)
            setViewTreeViewModelStoreOwner(this@OverlayController)
            
            setContent {
                MaterialTheme {
                    AgentOverlay(
                        isExecuting = isExecuting,
                        onTrigger = {
                            isExecuting = true
                            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
                            layoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                            layoutParams.x = 0
                            layoutParams.y = 0
                            windowManager.updateViewLayout(this, layoutParams)
                        },
                        onAbort = {
                            isExecuting = false
                            layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                            layoutParams.gravity = Gravity.TOP or Gravity.START
                            layoutParams.x = 100
                            layoutParams.y = 100
                            windowManager.updateViewLayout(this, layoutParams)
                        },
                        onDrag = { dx, dy ->
                            if (!isExecuting) {
                                layoutParams.x += dx.toInt()
                                layoutParams.y += dy.toInt()
                                windowManager.updateViewLayout(this, layoutParams)
                            }
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

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store
}

@Composable
fun AgentOverlay(
    isExecuting: Boolean,
    onTrigger: () -> Unit,
    onAbort: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    if (!isExecuting) {
        // Small FAB trigger
        Box(
            modifier = Modifier
                .size(64.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFFD0BCFF))
                .clickable { onTrigger() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Trigger Agent",
                tint = Color(0xFF381E72),
                modifier = Modifier.padding(16.dp)
            )
        }
    } else {
        // Full HUD Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .pointerInput(Unit) {
                    // Consume touches so it doesn't pass through if needed, 
                    // or detect drag for the bottom sheet (optional)
                }
                .shadow(24.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF2B2930).copy(alpha = 0.9f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Pulsing dot
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "scale"
                    )
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.8f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "alpha"
                    )
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .scale(scale)
                                .border(2.dp, Color(0xFFD0BCFF).copy(alpha = alpha), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFFD0BCFF), Color(0xFFB69DF8))
                                    ),
                                    CircleShape
                                )
                        )
                    }
                    
                    Column {
                        Text(
                            text = "AGENT ACTIVE",
                            color = Color(0xFFD0BCFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Analyzing Accessibility Node Tree...",
                            color = Color(0xFFE6E1E5),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF4CAF50), CircleShape))
                    Text(
                        text = "MCP:LOCAL",
                        color = Color(0xFF4CAF50),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            // Reasoning Stream
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row {
                        Text("> ", color = Color(0xFFF2B8B5), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text("query_intent: \"enable screen cursor\"", color = Color(0xFFD0BCFF).copy(alpha = 0.8f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Row {
                        Text("> ", color = Color(0xFFF2B8B5), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text("searching_view_tree: [Match Found]", color = Color(0xFFD0BCFF).copy(alpha = 0.8f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Row {
                        Text("> ", color = Color(0xFFF2B8B5), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text("executing: AccessibilityService.dispatchGesture()", color = Color(0xFFD0BCFF).copy(alpha = 0.8f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            
            // Security Guardrails Status
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = Color(0xFFCAC4D0),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = "BIOMETRIC LOCK: ARMED",
                        color = Color(0xFFCAC4D0),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                
                Text(
                    text = "EPHEMERAL_MODE: ON",
                    color = Color(0xFFCAC4D0).copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            // Abort Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF2B8B5))
                    .clickable { onAbort() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "TAP TO ABORT SEQUENCE",
                    color = Color(0xFF601410),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
