package com.pannulabs.smarttrade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pannulabs.smarttrade.ConnState
import com.pannulabs.smarttrade.EngineViewModel
import com.pannulabs.smarttrade.LockViewModel

private data class Dest(val label: String, val icon: ImageVector)

private val DESTS = listOf(
    Dest("Dashboard", Icons.Filled.Dashboard), Dest("Signals", Icons.Filled.ListAlt),
    Dest("Activity", Icons.Filled.Notifications), Dest("History", Icons.Filled.History),
    Dest("Settings", Icons.Filled.Settings),
)

@Composable
fun App(vm: EngineViewModel, lock: LockViewModel, verify: (String, () -> Unit) -> Unit) {
    val conn by vm.conn.collectAsStateWithLifecycle()
    val st by vm.state.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(if (vm.hasCode) 0 else 4) }
    val snack = remember { SnackbarHostState() }
    val connected = conn.state == ConnState.CONNECTED

    LaunchedEffect(toast) {
        toast?.let { snack.showSnackbar(it); vm.toastShown() }
    }

    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snack) },
        topBar = { TopBar(st.modeBadge, conn.state, DESTS[page].label) },
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                DESTS.forEachIndexed { i, d ->
                    NavigationBarItem(
                        selected = page == i, onClick = { page = i },
                        icon = { Icon(d.icon, contentDescription = d.label) },
                        label = { Text(d.label, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TextC, selectedTextColor = TextC, indicatorColor = Panel2,
                            unselectedIconColor = Muted, unselectedTextColor = Muted,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            when (page) {
                0 -> DashboardScreen(st, connected, vm::setPaused, vm::closeAll, vm::closeTrade)
                1 -> SignalsScreen(st)
                2 -> ActivityScreen(st)
                3 -> HistoryScreen(st)
                else -> SettingsScreen(
                    st, conn, vm.hasCode,
                    onConnect = { code ->
                        if (code.isBlank()) { vm.reconnect(); null }
                        else vm.connect(code).also { if (it == null) page = 0 }
                    },
                    onDisconnect = vm::disconnect,
                    onSave = { section, patch -> vm.saveSettings(section, patch) },
                    onSaveKeys = vm::saveKeys,
                    toast = { /* shown through the view model so it survives recomposition */ msg -> vm.showToast(msg) },
                    lock = lock, verify = verify,
                )
            }
        }
    }
}

@Composable
private fun TopBar(badge: String, state: ConnState, title: String) {
    val (txt, col) = stateText(state)
    Column(Modifier.fillMaxWidth().background(Panel).statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SmartTrade", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextC)
            Spacer(Modifier.width(8.dp))
            Text(title, color = Muted, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            when (badge) {
                "PAPER" -> Badge("PAPER", Accent)
                "DEMO" -> Badge("DEMO", Amber)
                "LIVE" -> Badge("LIVE", Red)
            }
        }
        Spacer(Modifier.padding(top = 4.dp))
        Dot(col, txt)
    }
}
