package com.pannulabs.smarttrade

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.pannulabs.smarttrade.ui.App
import com.pannulabs.smarttrade.ui.LockScreen
import com.pannulabs.smarttrade.ui.Theme

// FragmentActivity (not plain ComponentActivity) because the system biometric prompt needs it.
class MainActivity : FragmentActivity() {
    private val vm: EngineViewModel by viewModels()
    private val lock: LockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Theme {
                if (lock.locked) LockScreen(lock.message, onUnlock = ::promptUnlock)
                else App(vm, lock, ::verifyThen)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lock.onForeground()
    }

    override fun onResume() {
        super.onResume()
        if (lock.locked && !lock.autoPrompted) { lock.autoPrompted = true; promptUnlock() }
        // After the phone slept or changed network, reconnect at once instead of waiting for the back-off.
        if (vm.conn.value.state == ConnState.RECONNECTING) vm.reconnect()
    }

    override fun onPause() {
        super.onPause()
        // Keep positions and balances out of the recent-apps preview while the lock is on.
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!lock.enabled)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) lock.onBackground()
    }

    private fun promptUnlock() {
        val reason = Biometric.unavailableReason(this)
        if (reason != null) {
            // The phone no longer has any screen lock, so there is nothing to check against. Don't lock the owner out.
            lock.unlock()
            vm.showToast("App lock could not run: $reason")
            return
        }
        Biometric.prompt(this, "Unlock SmartTrade", "Use your fingerprint, face or screen lock") { err ->
            if (err == null) lock.unlock() else lock.message = err.ifEmpty { null }
        }
    }

    /** Confirms it is the owner before a security setting changes. [onOk] runs only after a successful check. */
    private fun verifyThen(title: String, onOk: () -> Unit) {
        Biometric.unavailableReason(this)?.let { vm.showToast(it); return }
        Biometric.prompt(this, title, "Confirm it's you") { err ->
            if (err == null) onOk() else if (err.isNotEmpty()) vm.showToast(err)
        }
    }
}
