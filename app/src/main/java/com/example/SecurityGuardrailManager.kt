package com.example

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Enforces hardcoded security boundaries and guardrails.
 */
class SecurityGuardrailManager(private val context: Context) {

    private val securePackages = listOf(
        "com.bank", "com.chase", "com.wells", "com.citi",
        "com.coinbase", "com.binance", "org.kraken", "com.venmo", "com.paypal"
    )

    /**
     * Checks if the foreground application is a restricted financial app.
     */
    fun isSecureAppForeground(rootNode: AccessibilityNodeInfo?): Boolean {
        val pkg = rootNode?.packageName?.toString() ?: return false
        return securePackages.any { pkg.startsWith(it, ignoreCase = true) }
    }

    /**
     * Requests biometric authentication from the user.
     * In a real service, this would coordinate with a UI activity.
     */
    fun requestBiometricAuth(onResult: (Boolean) -> Unit) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("REQUIRE_BIOMETRIC", true)
        }
        context.startActivity(intent)

        // This is a simplified hook. In practice, we'd use a broadcast receiver
        // or a shared flow to get the result back from the activity.
        onResult(false) // Default to failure for safety in this placeholder
    }
}
