package com.pannulabs.smarttrade

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.pannulabs.smarttrade.ui.App
import com.pannulabs.smarttrade.ui.Theme

class MainActivity : ComponentActivity() {
    private val vm: EngineViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Theme { App(vm) } }
    }

    override fun onResume() {
        super.onResume()
        // After the phone slept or changed network, reconnect at once instead of waiting for the back-off.
        if (vm.conn.value.state == ConnState.RECONNECTING) vm.reconnect()
    }
}
