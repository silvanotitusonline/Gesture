package com.example

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Helper class that intercepts the AccessibilityService and requires authentication
 * before interacting with financial app packages defined in a restricted list.
 */
object BiometricHelper {

    fun authenticate(context: Context, onResult: (Boolean) -> Unit) {
        if (context !is FragmentActivity) {
            // BiometricPrompt requires a FragmentActivity context.
            // In a real system service, this would launch a translucent overlay Activity
            // to host the BiometricPrompt. For simplicity in this structure:
            onResult(false)
            return
        }

        val executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Agent Security Override")
            .setSubtitle("Biometric authentication required")
            .setDescription("The agent is attempting to interact with a secure application.")
            .setNegativeButtonText("Cancel")
            .build()

        val biometricPrompt = BiometricPrompt(context, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onResult(false)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onResult(true)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onResult(false)
                }
            })

        // We launch on main thread
        Handler(Looper.getMainLooper()).post {
            biometricPrompt.authenticate(promptInfo)
        }
    }
}
