package com.example

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser responsible for traversing the Accessibility view tree and converting it
 * into a minified, token-efficient JSON payload for AI reasoning.
 */
class ViewTreeParser {

    /**
     * Traverses the given root node and returns a JSON representation.
     * Enforces the "Zero Email Logging" guardrail.
     */
    fun parse(rootNode: AccessibilityNodeInfo): String {
        // Guardrail: Zero Email Logging
        val packageName = rootNode.packageName?.toString() ?: ""
        if (packageName.contains("mail", ignoreCase = true) || packageName.contains("gmail", ignoreCase = true)) {
            return "{}"
        }

        val json = parseNode(rootNode)
        return json?.toString() ?: "{}"
    }

    private fun parseNode(node: AccessibilityNodeInfo): JSONObject? {
        if (!node.isVisibleToUser) return null

        val obj = JSONObject()
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // Class Name extraction (minified)
        node.className?.let {
            val name = it.toString()
            if (name.contains("Button") || name.contains("TextView") || name.contains("EditText") || name.contains("Image")) {
                obj.put("type", name.substringAfterLast('.'))
            }
        }

        // Essential semantic markers
        if (!node.text.isNullOrEmpty()) obj.put("text", node.text.toString())
        if (!node.contentDescription.isNullOrEmpty()) obj.put("desc", node.contentDescription.toString())
        if (!node.viewIdResourceName.isNullOrEmpty()) {
            obj.put("id", node.viewIdResourceName.toString().substringAfterLast('/'))
        }

        // Precision bounding boxes (minified)
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

        // Actionable flags
        if (node.isClickable) obj.put("clickable", true)
        if (node.isScrollable) obj.put("scrollable", true)
        if (node.isEditable) obj.put("editable", true)

        // Recursive tree traversal
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

        // Skip non-informative nodes to save tokens
        if (obj.length() == 0 || (obj.length() == 1 && obj.has("b"))) return null
        return obj
    }
}
