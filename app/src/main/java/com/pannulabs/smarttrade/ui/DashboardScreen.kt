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
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.HourglassEmpty
import com.pannulabs.smarttrade.EngineState
import com.pannulabs.smarttrade.Position
import com.pannulabs.smarttrade.PnlShare
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun DashboardScreen(
    st: EngineState, connected: Boolean,
    onPause: (Boolean) -> Unit, onCloseAll: () -> Unit, onClose: (Position) -> Unit,
) {
    var confirmAll by remember { mutableStateOf(false) }
    var confirmOne by remember { mutableStateOf<Position?>(null) }
    val snap = st.snapshot
    val maxTrades = st.trading.optInt("max_open_trades", 0)
    val leverage = st.trading.optInt("leverage", 0)
    val ctx = LocalContext.current

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
                    colors = SwitchDefaults.colors(checkedTrackColor = Brand, checkedThumbColor = OnBrand,
                        uncheckedTrackColor = Panel2, uncheckedBorderColor = Border),
                )
                Spacer(Modifier.padding(start = 10.dp))
                OutlinedButton(
                    onClick = { confirmAll = true }, enabled = connected, border = BorderStroke(1.dp, if (connected) Red else Faint),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                ) { Text("CLOSE ALL", fontWeight = FontWeight.Bold) }
            }
        }
        if (st.brokerError.isNotEmpty()) item { Text("Trading: ${st.brokerError}", color = Amber, fontSize = 13.sp) }
        if (snap == null) {
            item {
                Empty(if (connected) "Waiting for the first account update..." else "Not connected to the engine",
                    if (connected) Icons.Outlined.HourglassEmpty else Icons.Outlined.CloudOff)
            }
        } else {
            val openPnl = snap.positions.sumOf { it.unrealized }
            item {
                CardRow(
                    { StatCard("Equity", money(snap.equity), "USDT, sizing on ${money(snap.sizingBase)}", it, accent = Blue) },
                    {
                        StatCard("Position value used", if (snap.sizingBase > 0) String.format("%.2fx", snap.used / snap.sizingBase) else "-",
                            "${money(snap.used)} of ${money(snap.capacity)} (${snap.positions.size}/$maxTrades trades)", it, accent = Amber)
                    },
                )
            }
            item {
                CardRow(
                    { StatCard("Open P&L", money(openPnl, true), "before exit fee", it, pnlColor(openPnl), trend = openPnl) },
                    { StatCard("Today's realized", money(snap.todayRealized, true), "after fees & slippage", it,
                        pnlColor(snap.todayRealized), trend = snap.todayRealized) },
                )
            }
            item { H1("Open positions (${snap.positions.size})", Modifier.padding(top = 6.dp)) }
            if (snap.positions.isEmpty()) item { Empty("No open positions - new trades appear here") }
            items(snap.positions.sortedByDescending { it.unrealized }, key = { it.id }) { p ->
                PositionCard(p, leverage, connected, onShare = { roi -> PnlShare.share(ctx, p, roi) }) { confirmOne = p }
            }
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

/** Binance-style position card: B/S badge, PnL with share, ROI on margin, size/margin/prices grid, one Close button. */
@Composable
private fun PositionCard(p: Position, leverage: Int, connected: Boolean, onShare: (Double) -> Unit, onClose: () -> Unit) {
    val long = p.side == "LONG"
    val size = p.qty * p.mark
    val margin = if (leverage > 0) size / leverage else 0.0
    // ROI on margin, as Binance shows it (PnL / (position value / leverage)).
    val roi = if (margin > 0) p.unrealized / margin * 100 else p.unrealizedPct
    val pnlCol = pnlColor(p.unrealized)
    ListCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).background(if (long) Green else Red, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(if (long) "B" else "S", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            Spacer(Modifier.width(10.dp))
            Text(p.symbol, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextC)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("PNL (USDT)", color = Muted, fontSize = 13.sp)
            Icon(
                Icons.Outlined.Share, contentDescription = "Share PnL", tint = Muted,
                modifier = Modifier.padding(start = 6.dp).size(30.dp).clip(RoundedCornerShape(50))
                    .clickable { onShare(roi) }.padding(6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text("ROI", color = Muted, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(money(p.unrealized, true), color = pnlCol, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Spacer(Modifier.weight(1f))
            Text(String.format("%+.2f%%", roi), color = pnlCol, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Cell("Size (USDT)", money(size), Modifier.weight(1f))
            Cell("Margin (USDT)", if (margin > 0) money(margin) else "-", Modifier.weight(1f))
            Cell("Opened", ts(p.openedMs), Modifier.weight(1f), end = true)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Cell("Entry Price", price(p.entry), Modifier.weight(1f))
            Cell("Mark Price", price(p.mark), Modifier.weight(1f))
            Cell("Stop Loss", price(p.sl), Modifier.weight(1f), end = true, color = Red)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onClose, enabled = connected, modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Panel2, contentColor = TextC,
                disabledContainerColor = Panel2, disabledContentColor = Faint),
        ) { Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
    }
}

@Composable
private fun Cell(label: String, value: String, modifier: Modifier, end: Boolean = false, color: Color = TextC) {
    Column(modifier, horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Text(label, color = Muted, fontSize = 12.sp, maxLines = 1)
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
