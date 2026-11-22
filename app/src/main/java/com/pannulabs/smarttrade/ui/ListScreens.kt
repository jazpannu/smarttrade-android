package com.pannulabs.smarttrade.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pannulabs.smarttrade.EngineState
import com.pannulabs.smarttrade.SignalRow
import com.pannulabs.smarttrade.long
import com.pannulabs.smarttrade.num

@Composable
private fun Tabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    TabRow(selectedTabIndex = selected, containerColor = Bg, contentColor = TextC) {
        titles.forEachIndexed { i, t -> Tab(selected = i == selected, onClick = { onSelect(i) }, text = { Text(t, fontSize = 13.sp) }) }
    }
}

// ================================================================================================ Signals
@Composable
fun SignalsScreen(st: EngineState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val sc = st.scanner
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val ready = sc.optBoolean("ready")
            CardRow(
                { StatCard("Coins scanned", if (ready) sc.optInt("coins").toString() else "loading...", "${sc.optInt("open_signals")} open signals", it) },
                {
                    if (sc.has("ws_connections")) {
                        val n = sc.optInt("ws_connections"); val ok = sc.optInt("ws_connected")
                        StatCard("Live data", "$ok/$n", "WebSocket connections", it, if (n > 0 && ok == n) Green else Amber)
                    } else StatCard("Live data", "-", "starting", it)
                },
            )
            val closed = st.closedSignals
            val net = closed.sumOf { it.netPct }
            val wins = closed.count { it.netPct > 0 }
            CardRow(
                { StatCard("Last 6h scan", ts(sc.long("last_boundary_ms")).ifEmpty { "-" },
                    "stop ${price(sc.num("sl_min_pct"))}-${price(sc.num("sl_max_pct"))}%", it) },
                {
                    if (closed.isEmpty()) StatCard("Signal track record", "-", "no closed signals yet", it)
                    else StatCard("Signal track record", String.format("%+.1f%%", net),
                        "last ${closed.size}: $wins wins (${wins * 100 / closed.size}%)", it, pnlColor(net))
                },
            )
        }
        Tabs(listOf("Open (${st.openSignals.size})", "Closed (${st.closedSignals.size})"), tab) { tab = it }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Spacer(Modifier.height(4.dp)) }
            if (tab == 0) {
                if (st.openSignals.isEmpty()) item { Empty("No open signals") }
                items(st.openSignals) { s -> OpenSignalCard(s) }
            } else {
                item { MutedText("Net % after fees and slippage, without leverage. Newest first.") }
                if (st.closedSignals.isEmpty()) item { Empty("No closed signals yet") }
                items(st.closedSignals) { s -> ClosedSignalCard(s) }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun OpenSignalCard(s: SignalRow) = ListCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(s.symbol, fontWeight = FontWeight.Bold, color = TextC, fontSize = 15.sp)
        Spacer(Modifier.padding(start = 8.dp)); SideText(s.side)
        Spacer(Modifier.weight(1f)); MutedText(ts(s.openMs))
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        KV("Entry", price(s.entry), modifier = Modifier.weight(1f))
        KV("Stop loss", price(s.sl), modifier = Modifier.weight(1f))
        KV("SL %", String.format("%.2f%%", s.slPct), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ClosedSignalCard(s: SignalRow) = ListCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(s.symbol, fontWeight = FontWeight.Bold, color = TextC, fontSize = 15.sp)
        Spacer(Modifier.padding(start = 8.dp)); SideText(s.side)
        Spacer(Modifier.weight(1f))
        Text(String.format("%+.2f%%", s.netPct), color = pnlColor(s.netPct), fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        KV("Entry", price(s.entry), modifier = Modifier.weight(1f))
        KV("Exit", price(s.exit), modifier = Modifier.weight(1f))
        KV("Reason", s.reason, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(4.dp))
    MutedText("${ts(s.openMs)}  →  ${ts(s.closeMs)}")
}

// ================================================================================================ Activity
@Composable
fun ActivityScreen(st: EngineState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Tabs(listOf("Log", "Skipped signals (${st.skips.size})"), tab) { tab = it }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Spacer(Modifier.height(6.dp)) }
            if (tab == 0) {
                val notes = st.notes.asReversed()
                if (notes.isEmpty()) item { Empty("Nothing yet") }
                items(notes) { n ->
                    val col = when (n.kind) { "error" -> Red; "open" -> Green; "close" -> Accent; "skip" -> Amber; else -> TextC }
                    Column {
                        MutedText("${tsSec(n.timeMs)}  [${n.kind}]")
                        Text(n.text, color = col, fontSize = 13.sp)
                    }
                }
            } else {
                if (st.skips.isEmpty()) item { Empty("No skipped signals") }
                items(st.skips) { s ->
                    ListCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.symbol, fontWeight = FontWeight.Bold, color = TextC)
                            Spacer(Modifier.padding(start = 8.dp)); SideText(s.side)
                            Spacer(Modifier.weight(1f)); MutedText(ts(s.timeMs))
                        }
                        Text(s.reason, color = TextC, fontSize = 13.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

// ================================================================================================ History
@Composable
fun HistoryScreen(st: EngineState) {
    val done = st.trades.filter { it.status == "closed" }
    val net = done.sumOf { it.netPnl }
    val wins = done.count { it.netPnl > 0 }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Spacer(Modifier.height(6.dp)) }
        item {
            if (done.isEmpty()) MutedText("No closed trades yet")
            else CardRow(
                { StatCard("Closed trades", done.size.toString(), "$wins winners (${wins * 100 / done.size}%)", it) },
                { StatCard("Net P&L", money(net, true), "USDT, after fees", it, pnlColor(net)) },
            )
        }
        items(done, key = { it.id }) { t ->
            ListCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.symbol, fontWeight = FontWeight.Bold, color = TextC, fontSize = 15.sp)
                    Spacer(Modifier.padding(start = 8.dp)); SideText(t.side)
                    Spacer(Modifier.padding(start = 8.dp)); MutedText(t.mode)
                    Spacer(Modifier.weight(1f))
                    Text(money(t.netPnl, true), color = pnlColor(t.netPnl), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    KV("Entry", price(t.entry), modifier = Modifier.weight(1f))
                    KV("Exit", price(t.exit), modifier = Modifier.weight(1f))
                    KV("Reason", t.reason, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                MutedText("Closed ${ts(t.closedMs)}")
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
