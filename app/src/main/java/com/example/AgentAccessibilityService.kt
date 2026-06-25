package com.example

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

/**
 * Core Accessibility Service acting as the OS-level "Hands" and "Eyes" of the Agent.
 * Orchestrates view tree parsing and gesture execution with built-in guardrails.
 */
class AgentAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private lateinit var viewTreeParser: ViewTreeParser
    private lateinit var gestureExecutor: GestureExecutor
    private lateinit var securityGuardrailManager: SecurityGuardrailManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        viewTreeParser = ViewTreeParser()
        gestureExecutor = GestureExecutor(this)
        securityGuardrailManager = SecurityGuardrailManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Ephemeral processing: No background logging or monitoring.
    }

    override fun onInterrupt() {
        // Cleanup resources
    }

    /**
     * Extracts the active window's UI into a token-efficient JSON payload.
     * All memory is considered ephemeral and must be cleared after use.
     */
    fun extractSemanticViewTree(): String {
        val rootNode = rootInActiveWindow ?: return "{}"
        
        val payload = viewTreeParser.parse(rootNode)
        
        // Explicitly clear rootNode reference to ensure ephemeral handling
        rootNode.recycle()
        
        return payload
    }

    /**
     * Executes a tap gesture with biometric guardrail interception.
     */
    fun executeTap(x: Float, y: Float, onComplete: (Boolean) -> Unit) {
        if (securityGuardrailManager.isSecureAppForeground(rootInActiveWindow)) {
            securityGuardrailManager.requestBiometricAuth { authenticated ->
                if (authenticated) {
                    gestureExecutor.tap(x, y, onComplete)
                } else {
                    onComplete(false)
                }
            }
        } else {
            gestureExecutor.tap(x, y, onComplete)
        }
    }

    /**
     * Injects text input into a node with biometric guardrail interception.
     */
    fun executeTextInput(node: AccessibilityNodeInfo, text: String, onComplete: (Boolean) -> Unit) {
        if (securityGuardrailManager.isSecureAppForeground(rootInActiveWindow)) {
            securityGuardrailManager.requestBiometricAuth { authenticated ->
                if (authenticated) {
                    val success = gestureExecutor.inputText(node, text)
                    onComplete(success)
                } else {
                    onComplete(false)
                }
            }
        } else {
            val success = gestureExecutor.inputText(node, text)
            onComplete(success)
        }
    }
}
