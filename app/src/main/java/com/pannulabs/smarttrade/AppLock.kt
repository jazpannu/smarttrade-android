package com.pannulabs.smarttrade

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel

/**
 * App lock: when on, the app asks for fingerprint / face (or the phone's PIN, pattern or password as a fallback)
 * when it opens and when it comes back from the background after the chosen delay.
 * The engine connection keeps running while locked; only the screens are hidden.
 */
class LockViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("smarttrade_lock", Context.MODE_PRIVATE)

    var enabled by mutableStateOf(prefs.getBoolean("enabled", false))
        private set
    /** Seconds the app may stay in the background before it locks again. 0 = immediately. */
    var delaySec by mutableStateOf(prefs.getInt("delay_sec", 0))
        private set
    /** Starts locked on a fresh launch when the lock is on. Survives screen rotation (view model). */
    var locked by mutableStateOf(enabled)
        private set

    /** Shown on the lock screen, e.g. "Too many attempts". */
    var message by mutableStateOf<String?>(null)

    private var backgroundedAt = 0L
    /** The prompt is shown automatically once per lock; after a cancel the user taps Unlock. */
    var autoPrompted = false

    fun changeEnabled(on: Boolean) {
        enabled = on
        prefs.edit().putBoolean("enabled", on).apply()
        if (!on) locked = false
    }

    fun setDelay(sec: Int) {
        delaySec = sec
        prefs.edit().putInt("delay_sec", sec).apply()
    }

    fun unlock() { locked = false; message = null }

    fun onBackground() { backgroundedAt = SystemClock.elapsedRealtime() }

    fun onForeground() {
        if (!enabled || locked || backgroundedAt == 0L) return
        if (SystemClock.elapsedRealtime() - backgroundedAt >= delaySec * 1000L) {
            locked = true
            autoPrompted = false
        }
        backgroundedAt = 0L
    }
}

object Biometric {
    /** Fingerprint/face, with the phone's screen lock as fallback (works on every Android version we support). */
    const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** null when the phone can authenticate; otherwise a readable reason. */
    fun unavailableReason(context: Context): String? =
        when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "Set up a fingerprint, face unlock or screen lock in your phone's settings first."
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE, BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "This phone has no usable fingerprint or face sensor right now."
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                "A security update is required before biometrics can be used."
            else -> "Biometric unlock is not available on this phone."
        }

    /**
     * Shows the system prompt. [onResult] gets null on success, or an error message
     * (empty string when the user just cancelled).
     */
    fun prompt(activity: FragmentActivity, title: String, subtitle: String, onResult: (String?) -> Unit) {
        val cb = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(null)
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                val cancelled = code == BiometricPrompt.ERROR_USER_CANCELED ||
                    code == BiometricPrompt.ERROR_NEGATIVE_BUTTON || code == BiometricPrompt.ERROR_CANCELED
                onResult(if (cancelled) "" else msg.toString())
            }
            // onAuthenticationFailed (a finger not recognised) keeps the prompt open; nothing to do.
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), cb).authenticate(info)
    }
}
