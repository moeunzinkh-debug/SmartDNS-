package com.example.system

import android.app.KeyguardManager
import android.content.Context
import android.os.Build

class BiometricAuthHelper(private val context: Context) {

    private val keyguardManager =
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    /**
     * Checks if the device has a secure lock (PIN, Pattern, Password, or Biometrics) set up.
     */
    fun isDeviceSecure(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            keyguardManager.isDeviceSecure
        } else {
            keyguardManager.isKeyguardSecure
        }
    }
}
