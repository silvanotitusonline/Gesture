package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Core Accessibility Service acting as the OS-level "Hands" and "Eyes" of the Agent.
 * Parses the view tree and injects precision human gestures.
 */
class AgentAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    // List of known package prefixes representing financial, banking, or crypto apps
    private val securePackages = listOf(
        "com.bank", "com.chase", "com.wells", "com.citi",
        "com.coinbase", "com.binance", "org.kraken"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Initialization logic for the Agent Service
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No background caching or monitoring of email.
        // We only respond to explicit triggers via the OverlayController or Routing logic.
    }

    override fun onInterrupt() {
        // Handle interruption (e.g., clear ephemeral data)
    }

    /**
     * Extracts the active window's UI into a token-efficient JSON payload.
     * All memory is considered ephemeral and must be cleared after dispatch.
     */
    fun extractSemanticViewTree(): String {
        val rootNode = rootInActiveWindow ?: return "{}"
        
        // Zero Email Logging Security Boundary:
        // Do not process nodes if the package belongs to an email client.
        val packageName = rootNode.packageName?.toString() ?: ""
        if (packageName.contains("mail", ignoreCase = true) || packageName.contains("gmail", ignoreCase = true)) {
            return "{}"
        }

        val jsonTree = parseNode(rootNode)
        
        // Data is ephemeral. We return it to the caller (e.g. MCP router) 
        // and do not cache it locally.
        return jsonTree.toString()
    }

    private fun parseNode(node: AccessibilityNodeInfo): JSONObject? {
        if (!node.isVisibleToUser) return null
        
        val obj = JSONObject()
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        // Token optimization: only include useful properties
        node.className?.let {
            val name = it.toString()
            if (name.contains("Button") || name.contains("TextView") || name.contains("EditText")) {
                obj.put("class", name.substringAfterLast('.'))
            }
        }
        
        if (!node.text.isNullOrEmpty()) obj.put("text", node.text.toString())
        if (!node.contentDescription.isNullOrEmpty()) obj.put("desc", node.contentDescription.toString())
        if (!node.viewIdResourceName.isNullOrEmpty()) {
            val id = node.viewIdResourceName.toString()
            obj.put("id", id.substringAfterLast('/'))
        }
        
        // Minimize bounds token size
        val w = bounds.width()
        val h = bounds.height()
        if (w > 0 && h > 0) {
            obj.put("b", JSONArray().apply {
                put(bounds.left)
                put(bounds.top)
                put(w)
                put(h)
            })
        }
        
        if (node.isClickable) obj.put("click", true)
        if (node.isScrollable) obj.put("scroll", true)
        
        if (node.childCount > 0) {
            val children = JSONArray()
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    val childObj = parseNode(child)
                    if (childObj != null && childObj.length() > 0) {
                        children.put(childObj)
                    }
                    child.recycle()
                }
            }
            if (children.length() > 0) {
                obj.put("children", children)
            }
        }
        
        // Optimize: skip empty nodes
        if (obj.length() == 0 || (obj.length() == 1 && obj.has("b"))) return null
        return obj
    }

    /**
     * Executes a tap gesture at the specified screen coordinates.
     * Includes Biometric Lockout interception.
     */
    fun executeTap(x: Float, y: Float, onComplete: (Boolean) -> Unit) {
        if (isFinancialAppForeground()) {
            requestBiometricAuth { success ->
                if (success) {
                    performTap(x, y, onComplete)
                } else {
                    onComplete(false)
                }
            }
            return
        }
        performTap(x, y, onComplete)
    }

    private fun performTap(x: Float, y: Float, onComplete: (Boolean) -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onComplete(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                onComplete(false)
            }
        }, null)
    }

    /**
     * Injects text into an AccessibilityNodeInfo directly, matching the exact target.
     */
    fun executeTextInput(node: AccessibilityNodeInfo, text: String): Boolean {
        if (isFinancialAppForeground()) {
            // Require biometrics before typing in a secure app
            return false // Simplified for this context. Use async wrapper similarly to tap.
        }
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /**
     * Evaluates whether the currently foregrounded application requires biometric auth.
     */
    private fun isFinancialAppForeground(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val pkg = rootNode.packageName?.toString() ?: return false
        return securePackages.any { pkg.startsWith(it, ignoreCase = true) }
    }

    /**
     * Simulates a biometric authentication prompt hook.
     * In a production system, this would launch a translucent Activity using BiometricPrompt.
     */
    private fun requestBiometricAuth(callback: (Boolean) -> Unit) {
        // Triggering the biometric prompt via the overlay or a dedicated activity.
        // BiometricHelper.authenticate() requires a FragmentActivity context.
        // In a real system service, we launch a translucent Activity using BiometricPrompt.
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("REQUIRE_BIOMETRIC", true)
        }
        startActivity(intent)
        // Assume failure here until authenticated via activity callback flow
        callback(false)
    }
}
