package com.pannulabs.smarttrade.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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

private const val SETTINGS = 5

private val DESTS = listOf(
    Dest("Dashboard", Icons.Outlined.GridView), Dest("Signals", Icons.Outlined.Radar),
    Dest("Activity", Icons.AutoMirrored.Outlined.ListAlt), Dest("History", Icons.Outlined.History),
    Dest("Excel", Icons.Outlined.TableChart),
)

@Composable
fun App(vm: EngineViewModel, lock: LockViewModel, verify: (String, () -> Unit) -> Unit) {
    val conn by vm.conn.collectAsStateWithLifecycle()
    val st by vm.state.collectAsStateWithLifecycle()
    val journal by vm.journal.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(if (vm.hasCode) 0 else SETTINGS) }
    val snack = remember { SnackbarHostState() }
    val connected = conn.state == ConnState.CONNECTED

    LaunchedEffect(toast) {
        toast?.let { snack.showSnackbar(it); vm.toastShown() }
    }

    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snack) { data -> Toast(data.visuals.message) } },
        topBar = {
            TopBar(st.modeBadge, conn.state, if (page == SETTINGS) "Settings" else DESTS[page].label,
                settingsOpen = page == SETTINGS, onSettings = { page = if (page == SETTINGS) 0 else SETTINGS })
        },
        bottomBar = {
            NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                DESTS.forEachIndexed { i, d ->
                    NavigationBarItem(
                        selected = page == i, onClick = { page = i },
                        icon = { Icon(d.icon, contentDescription = d.label) },
                        label = { Text(d.label, fontSize = 10.sp, maxLines = 1, fontWeight = if (page == i) FontWeight.SemiBold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Brand, selectedTextColor = TextC, indicatorColor = Brand.copy(alpha = 0.14f),
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
                4 -> ExcelScreen(journal, connected, onRefresh = vm::loadJournal, toast = vm::showToast)
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
private fun TopBar(badge: String, state: ConnState, title: String, settingsOpen: Boolean, onSettings: () -> Unit) {
    val (txt, col) = stateText(state)
    Column(Modifier.fillMaxWidth().background(Bg).statusBarsPadding()) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandLogo(30)
            Spacer(Modifier.width(8.dp))
            Column {
                // Tight line heights: the default 24sp body line height left a big gap between the two lines.
                BrandWordmark(17, tight = true)
                Text("SMARTTRADE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp,
                    lineHeight = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            when (badge) {
                "PAPER" -> Badge("PAPER", Blue)
                "DEMO" -> Badge("DEMO", Amber)
                "LIVE" -> Badge("LIVE", Red)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = if (settingsOpen) Brand else Muted)
            }
        }
        Row(Modifier.padding(horizontal = 14.dp).padding(top = 2.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextC, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Dot(col, txt)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
    }
}

/** Snackbar styled like the panel's corner toasts: icon, coloured edge. */
@Composable
private fun Toast(text: String) {
    val bad = listOf("fail", "error", "could not", "not connected", "invalid").any { text.contains(it, ignoreCase = true) }
    val col = if (bad) Red else Brand
    Snackbar(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        containerColor = Panel2, contentColor = TextC, shape = RoundedCornerShape(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(22.dp).background(col, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(10.dp))
            Icon(if (bad) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle, null, tint = col, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 13.sp)
        }
    }
}
