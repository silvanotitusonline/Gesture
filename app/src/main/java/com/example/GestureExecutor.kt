package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Handles OS-level execution of human-like gestures.
 */
class GestureExecutor(private val service: AccessibilityService) {

    /**
     * Dispatches a tap gesture at the specified screen coordinates.
     */
    fun tap(x: Float, y: Float, onComplete: (Boolean) -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = onComplete(true)
            override fun onCancelled(gestureDescription: GestureDescription?) = onComplete(false)
        }, null)
    }

    /**
     * Injects text into a specific accessibility node.
     */
    fun inputText(node: AccessibilityNodeInfo, text: String): Boolean {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /**
     * Performs a swipe gesture.
     */
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 500, onComplete: (Boolean) -> Unit) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = onComplete(true)
            override fun onCancelled(gestureDescription: GestureDescription?) = onComplete(false)
        }, null)
    }
}
