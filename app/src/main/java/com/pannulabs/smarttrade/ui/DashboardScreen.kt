package com.pannulabs.smarttrade.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.pannulabs.smarttrade.EngineState
import com.pannulabs.smarttrade.Position

@Composable
fun DashboardScreen(
    st: EngineState, connected: Boolean,
    onPause: (Boolean) -> Unit, onCloseAll: () -> Unit, onClose: (Position) -> Unit,
) {
    var confirmAll by remember { mutableStateOf(false) }
    var confirmOne by remember { mutableStateOf<Position?>(null) }
    val snap = st.snapshot
    val maxTrades = st.trading.optInt("max_open_trades", 0)

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (st.paused) "New trades paused" else "Taking new trades", color = if (st.paused) Amber else TextC,
                    modifier = Modifier.weight(1f))
                Switch(
                    checked = !st.paused, enabled = connected, onCheckedChange = { onPause(!it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Green),
                )
                Spacer(Modifier.padding(start = 10.dp))
                Button(
                    onClick = { confirmAll = true }, enabled = connected,
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                ) { Text("CLOSE ALL", fontWeight = FontWeight.Bold) }
            }
        }
        if (st.brokerError.isNotEmpty()) item { Text("Trading: ${st.brokerError}", color = Amber, fontSize = 13.sp) }
        if (snap == null) {
            item { Empty(if (connected) "Waiting for the first account update..." else "Not connected to the engine") }
        } else {
            val openPnl = snap.positions.sumOf { it.unrealized }
            item {
                CardRow(
                    { StatCard("Equity", money(snap.equity), "USDT, sizing on ${money(snap.sizingBase)}", it) },
                    {
                        StatCard("Position value used", if (snap.sizingBase > 0) String.format("%.2fx", snap.used / snap.sizingBase) else "-",
                            "${money(snap.used)} of ${money(snap.capacity)} (${snap.positions.size}/$maxTrades trades)", it)
                    },
                )
            }
            item {
                CardRow(
                    { StatCard("Open P&L", money(openPnl, true), "before exit fee", it, pnlColor(openPnl)) },
                    { StatCard("Today's realized P&L", money(snap.todayRealized, true), "after fees & slippage", it, pnlColor(snap.todayRealized)) },
                )
            }
            item { H1("Open positions (${snap.positions.size})", Modifier.padding(top = 6.dp)) }
            if (snap.positions.isEmpty()) item { Empty("No open positions") }
            items(snap.positions, key = { it.id }) { p -> PositionCard(p, connected) { confirmOne = p } }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    if (confirmAll) Confirm(
        "Close all trades", "Close ALL open positions at market now?", "Close all",
        onYes = onCloseAll, onDismiss = { confirmAll = false }, danger = true,
    )
    confirmOne?.let { p ->
        Confirm(
            "Close position",
            "Close ${p.symbol} ${p.side} (${price(p.qty)}) at market now?\n\nThe stop-loss order is cancelled. " +
                "The coin is only traded again on a new signal.",
            "Close", onYes = { onClose(p) }, onDismiss = { confirmOne = null }, danger = true,
        )
    }
}

@Composable
private fun PositionCard(p: Position, connected: Boolean, onClose: () -> Unit) {
    ListCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(p.symbol, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextC)
            Spacer(Modifier.padding(start = 8.dp))
            SideText(p.side)
            Spacer(Modifier.weight(1f))
            Text(money(p.unrealized, true), color = pnlColor(p.unrealized), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("  " + String.format("%+.2f%%", p.unrealizedPct), color = pnlColor(p.unrealized), fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            KV("Entry", price(p.entry), modifier = Modifier.weight(1f))
            KV("Mark", price(p.mark), modifier = Modifier.weight(1f))
            KV("Stop loss", price(p.sl), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KV("Qty", price(p.qty), modifier = Modifier.weight(1f))
            KV("Size (USDT)", money(p.qty * p.mark), modifier = Modifier.weight(1f))
            KV("Opened", ts(p.openedMs), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onClose, enabled = connected, border = BorderStroke(1.dp, Red),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Red), modifier = Modifier.fillMaxWidth(),
        ) { Text("Close", fontWeight = FontWeight.Bold) }
    }
}
