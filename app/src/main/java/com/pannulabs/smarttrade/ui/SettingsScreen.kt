package com.pannulabs.smarttrade.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pannulabs.smarttrade.ConnState
import com.pannulabs.smarttrade.Conn
import com.pannulabs.smarttrade.EngineState
import com.pannulabs.smarttrade.LockViewModel
import com.pannulabs.smarttrade.Biometric
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalContext
import com.pannulabs.smarttrade.num
import com.pannulabs.smarttrade.numOrNull
import com.pannulabs.smarttrade.str
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun SettingsScreen(
    st: EngineState, conn: Conn, hasCode: Boolean,
    onConnect: (String) -> String?, onDisconnect: (Boolean) -> Unit,
    onSave: (String, JSONObject) -> Unit, onSaveKeys: (String, String, () -> Unit) -> Unit,
    toast: (String) -> Unit,
    lock: LockViewModel, verify: (String, () -> Unit) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(if (hasCode) 1 else 0) }
    val connected = conn.state == ConnState.CONNECTED
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, containerColor = Bg, contentColor = TextC, edgePadding = 8.dp) {
            listOf("Connection", "Trading", "Scanner", "Binance API", "Security").forEachIndexed { i, t ->
                Tab(selected = i == tab, onClick = { tab = i }, text = { Text(t, fontSize = 13.sp) })
            }
        }
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (tab) {
                0 -> ConnectionTab(conn, hasCode, onConnect, onDisconnect)
                1 -> if (connected) TradingTab(st, onSave, toast) else NeedEngine()
                2 -> if (connected) ScannerTab(st, onSave, toast) else NeedEngine()
                3 -> if (connected) KeysTab(st, onSaveKeys, toast) else NeedEngine()
                4 -> SecurityTab(lock, verify)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NeedEngine() = MutedText("Connect to your engine first (Connection tab).")

// ---- small form widgets -----------------------------------------------------------------------------------
@Composable
private fun NumField(label: String, v: MutableState<String>, suffix: String = "", decimal: Boolean = true) =
    OutlinedTextField(
        value = v.value, onValueChange = { v.value = it.trim() }, label = { Text(label) }, singleLine = true,
        suffix = { if (suffix.isNotEmpty()) Text(suffix, color = Muted) },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )

@Composable
private fun TextField(label: String, v: MutableState<String>, placeholder: String = "", enabled: Boolean = true) =
    OutlinedTextField(
        value = v.value, onValueChange = { v.value = it }, label = { Text(label) }, enabled = enabled,
        placeholder = { Text(placeholder, color = Muted) }, modifier = Modifier.fillMaxWidth(),
    )

@Composable
private fun <T> Choice(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(label, color = Muted, fontSize = 12.sp)
        options.forEach { (value, text) ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(value) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = value == selected, onClick = { onSelect(value) })
                Text(text, color = TextC, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Check(text: String, v: MutableState<Boolean>) {
    Row(Modifier.fillMaxWidth().clickable { v.value = !v.value }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = v.value, onCheckedChange = { v.value = it })
        Text(text, color = TextC, fontSize = 14.sp)
    }
}

/** Number parsing with the same limits as the Windows panel. Throws with a readable message. */
private fun d(label: String, s: String, lo: Double, hi: Double): Double {
    val x = s.replace(",", ".").toDoubleOrNull() ?: throw IllegalArgumentException("$label: enter a number")
    if (x < lo || x > hi) throw IllegalArgumentException("$label must be between ${price(lo)} and ${price(hi)}")
    return x
}

private fun i(label: String, s: String, lo: Int, hi: Int): Int = d(label, s, lo.toDouble(), hi.toDouble()).let {
    if (it != Math.floor(it)) throw IllegalArgumentException("$label must be a whole number") else it.toInt()
}

private fun fmt(x: Double): String = price(x)

// ---- connection ---------------------------------------------------------------------------------------
@Composable
private fun ConnectionTab(conn: Conn, hasCode: Boolean, onConnect: (String) -> String?, onDisconnect: (Boolean) -> Unit) {
    val code = remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    H1("Engine on your server")
    val (txt, col) = stateText(conn.state)
    Dot(col, txt)
    if (conn.detail.isNotEmpty() && conn.state != ConnState.CONNECTED) MutedText(conn.detail)
    OutlinedTextField(
        value = code.value, onValueChange = { code.value = it; error = null },
        label = { Text(if (hasCode) "New connection code (optional)" else "Connection code") },
        placeholder = { Text("PLST1-...", color = Muted) }, visualTransformation = PasswordVisualTransformation(),
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    error?.let { Text(it, color = Red, fontSize = 13.sp) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = {
            if (code.value.isBlank() && !hasCode) error = "Paste the connection code from your server first."
            else { error = onConnect(code.value); if (error == null) code.value = "" }
        }) { Text(if (code.value.isBlank() && hasCode) "Reconnect" else "Connect") }
        if (hasCode) {
            OutlinedButton(onClick = { onDisconnect(false) }) { Text("Disconnect") }
            OutlinedButton(onClick = { onDisconnect(true) }) { Text("Forget code", color = Red) }
        }
    }
    MutedText(
        "Create a code for this phone on your server:\n" +
            "sudo smarttrade-admin pair MyPhone --host YOUR_SERVER_IP\n\n" +
            "The connection is encrypted and locked to your server's certificate. The code is stored encrypted on " +
            "this phone. Closing the app never stops the engine on your server."
    )
}

// ---- security (stored on this phone only, never sent to the engine) ----------------------------------------
@Composable
private fun SecurityTab(lock: LockViewModel, verify: (String, () -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val reason = remember { Biometric.unavailableReason(ctx) }
    H1("App lock")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Biometric unlock", color = TextC, fontSize = 15.sp)
            MutedText("Ask for fingerprint or face (or your phone PIN) to open this app")
        }
        Switch(
            checked = lock.enabled, enabled = lock.enabled || reason == null,
            // Both turning it on and off need a successful check, so only the phone's owner can change it.
            onCheckedChange = { on ->
                verify(if (on) "Turn on app lock" else "Turn off app lock") { lock.setEnabled(on) }
            },
        )
    }
    reason?.let { if (!lock.enabled) Text(it, color = Amber, fontSize = 13.sp) }
    if (lock.enabled) {
        Choice(
            "Lock again after the app is in the background for",
            listOf(0 to "Immediately", 60 to "1 minute", 300 to "5 minutes", 900 to "15 minutes"),
            lock.delaySec,
        ) { lock.setDelay(it) }
    }
    MutedText(
        "The lock only protects this app on this phone. The engine keeps trading on your server while the app is " +
            "locked. While the lock is on, the recent-apps preview of SmartTrade is hidden (Android 13 and newer)."
    )
}

fun stateText(s: ConnState) = when (s) {
    ConnState.CONNECTED -> "Engine connected" to Green
    ConnState.CONNECTING -> "Connecting to engine..." to Amber
    ConnState.RECONNECTING -> "Engine unreachable, retrying..." to Amber
    ConnState.AUTH_ERROR -> "Engine rejected the connection code" to Red
    ConnState.CERT_ERROR -> "Server certificate does not match - not connected" to Red
    ConnState.STOPPED -> "Not connected" to Muted
}

// ---- trading ------------------------------------------------------------------------------------------
@Composable
private fun TradingTab(st: EngineState, onSave: (String, JSONObject) -> Unit, toast: (String) -> Unit) {
    val t = st.trading
    val k = t.toString()
    var mode by remember(k) { mutableStateOf(t.str("mode").ifEmpty { "paper" }) }
    val demo = remember(k) { mutableStateOf(t.optBoolean("testnet", true)) }
    var side by remember(k) { mutableStateOf(t.str("trade_side").ifEmpty { "both" }) }
    var basis by remember(k) { mutableStateOf(t.str("sizing_basis").ifEmpty { "lower" }) }
    val risk = remember(k) { mutableStateOf(fmt(t.num("risk_pct"))) }
    val lev = remember(k) { mutableStateOf(t.optInt("leverage", 5).toString()) }
    val maxt = remember(k) { mutableStateOf(t.optInt("max_open_trades", 6).toString()) }
    val age = remember(k) { mutableStateOf(t.optInt("max_signal_age_min", 30).toString()) }
    val dev = remember(k) { mutableStateOf(fmt(t.num("max_entry_deviation_pct"))) }
    val dl = remember(k) { mutableStateOf(t.numOrNull("daily_loss_limit_pct")?.let(::fmt) ?: "0") }
    val bal = remember(k) { mutableStateOf(fmt(t.num("paper_balance"))) }
    var confirmLive by remember { mutableStateOf<JSONObject?>(null) }

    Choice("Trading mode", listOf("paper" to "Paper trading (simulated)", "live" to "Binance futures"), mode) { mode = it }
    if (mode == "live") Check("Use Binance Demo Trading (fake money) - untick for real money", demo)
    Choice("Trade side", listOf("both" to "Both long and short", "long" to "Long only", "short" to "Short only"), side) { side = it }
    MutedText("Only signals on the selected side are traded. Open trades on the other side are still closed normally.")
    NumField("Risk per trade", risk, "%")
    Choice("Risk is calculated on", listOf(
        "lower" to "Lower of balance and equity (recommended)",
        "equity" to "Equity, including open profit/loss",
        "balance" to "Balance only, ignoring open profit/loss"), basis) { basis = it }
    NumField("Leverage", lev, "x", decimal = false)
    MutedText("Set on Binance for each coin before entry. Total position value is capped at account x leverage.")
    NumField("Max open trades", maxt, decimal = false)
    NumField("Ignore signals older than", age, "min", decimal = false)
    NumField("Skip if price moved more than", dev, "%")
    NumField("Daily loss limit (0 = off)", dl, "% of equity today")
    NumField("Paper starting balance", bal, "USDT")
    MutedText("Max stop-loss %, fees and slippage are set once, on the Scanner tab.")

    Button(onClick = {
        try {
            val dlv = d("Daily loss limit", dl.value, 0.0, 100.0)
            val patch = JSONObject()
                .put("mode", mode).put("testnet", demo.value).put("trade_side", side)
                .put("risk_pct", d("Risk per trade", risk.value, 0.01, 10.0)).put("sizing_basis", basis)
                .put("leverage", i("Leverage", lev.value, 1, 125))
                .put("max_open_trades", i("Max open trades", maxt.value, 1, 200))
                .put("max_signal_age_min", i("Signal age", age.value, 1, 1440))
                .put("max_entry_deviation_pct", d("Price move limit", dev.value, 0.1, 20.0))
                .put("paper_balance", d("Paper balance", bal.value, 100.0, 10_000_000.0))
                .put("daily_loss_limit_pct", if (dlv > 0) dlv else JSONObject.NULL)
            val alreadyReal = t.str("mode") == "live" && !t.optBoolean("testnet", true)
            if (mode == "live" && !demo.value && !alreadyReal) confirmLive = patch else onSave("trading", patch)
        } catch (e: IllegalArgumentException) {
            toast(e.message ?: "Check the values")
        }
    }) { Text("Save trading settings") }

    confirmLive?.let { p ->
        Confirm("Real money", "You are switching to LIVE trading with real money.\n\nOrders will be placed on your real " +
            "Binance futures account. Continue?", "Yes, go live", onYes = { onSave("trading", p) },
            onDismiss = { confirmLive = null }, danger = true)
    }
}

// ---- scanner ------------------------------------------------------------------------------------------
@Composable
private fun ScannerTab(st: EngineState, onSave: (String, JSONObject) -> Unit, toast: (String) -> Unit) {
    val s = st.scannerSettings
    val k = s.toString()
    fun list(key: String) = s.optJSONArray(key)?.let { a -> (0 until a.length()).joinToString(", ") { a.opt(it).toString() } } ?: ""
    var all by remember(k) { mutableStateOf(s.optBoolean("all_coins", true)) }
    val coins = remember(k) { mutableStateOf(list("coins")) }
    val minvol = remember(k) { mutableStateOf(fmt(s.num("min_quote_volume_usdt"))) }
    val exclude = remember(k) { mutableStateOf(list("exclude")) }
    val slmax = remember(k) { mutableStateOf(fmt(s.num("sl_max_pct"))) }
    val slmin = remember(k) { mutableStateOf(fmt(s.num("sl_min_pct"))) }
    val fast = remember(k) { mutableStateOf(list("ema_fast")) }
    val slow = remember(k) { mutableStateOf(s.optInt("ema_slow", 55).toString()) }
    val zzDepth = remember(k) { mutableStateOf(s.optInt("zz_depth", 5).toString()) }
    val zzDev = remember(k) { mutableStateOf(s.optInt("zz_deviation_ticks", 1).toString()) }
    val zzBack = remember(k) { mutableStateOf(s.optInt("zz_backstep", 2).toString()) }
    val developing = remember(k) { mutableStateOf(s.optBoolean("include_developing", true)) }
    val cool = remember(k) { mutableStateOf(s.optInt("cooldown_candles", 1).toString()) }
    val stopChk = remember(k) { mutableStateOf(s.optInt("stop_check_minutes", 15).toString()) }
    val fee = remember(k) { mutableStateOf(fmt(s.num("taker_fee_pct"))) }
    val slip = remember(k) { mutableStateOf(fmt(s.num("slippage_pct"))) }

    Choice("Coins", listOf(true to "All Binance USDT perpetuals", false to "Only the coins listed below"), all) { all = it }
    TextField("Coin list", coins, "BTC, ETH, SOL ...", enabled = !all)
    NumField("Minimum 24h volume (all-coins mode, 0 = no limit)", minvol, "USDT")
    TextField("Exclude", exclude, "coins never to scan, e.g. USDC, BTCDOM")
    NumField("Max stop-loss", slmax, "%")
    NumField("Min stop-loss", slmin, "%")
    TextField("Fast EMAs", fast, "8, 13, 21")
    NumField("Slow EMA", slow, decimal = false)
    MutedText("Timeframes: 1D + 12h + 6h (entry), 6h (exit and zigzag)")
    NumField("Zigzag depth", zzDepth, decimal = false)
    NumField("Zigzag deviation", zzDev, "ticks", decimal = false)
    NumField("Zigzag backstep", zzBack, decimal = false)
    Check("Use the developing zigzag point (what TradingView shows)", developing)
    NumField("Cooldown after a stop-out", cool, "x 6h candles", decimal = false)
    NumField("Check stops every", stopChk, "min", decimal = false)
    NumField("Taker fee", fee, "% per side")
    NumField("Slippage", slip, "% per side")
    MutedText("Changes apply at once without a restart: the coin list is rebuilt within seconds and the rules are used " +
        "from the next 6h close. The max stop-loss is checked twice - when the signal fires, and again at the live " +
        "price just before the order.")

    Button(onClick = {
        try {
            val fastList = fast.value.split(',', ';', ' ').filter { it.isNotBlank() }.map {
                it.trim().toIntOrNull() ?: throw IllegalArgumentException("Fast EMAs: enter whole numbers separated by commas, e.g. 8, 13, 21")
            }
            val patch = JSONObject()
                .put("all_coins", all).put("coins", coins.value)
                .put("min_quote_volume_usdt", d("Minimum volume", minvol.value, 0.0, 10_000_000_000.0))
                .put("exclude", exclude.value)
                .put("sl_max_pct", d("Max stop-loss", slmax.value, 0.1, 100.0))
                .put("sl_min_pct", d("Min stop-loss", slmin.value, 0.0, 99.0))
                .put("ema_fast", JSONArray(fastList))
                .put("ema_slow", i("Slow EMA", slow.value, 2, 500))
                .put("zz_depth", i("Zigzag depth", zzDepth.value, 2, 100))
                .put("zz_deviation_ticks", i("Zigzag deviation", zzDev.value, 0, 1000))
                .put("zz_backstep", i("Zigzag backstep", zzBack.value, 0, 50))
                .put("include_developing", developing.value)
                .put("cooldown_candles", i("Cooldown", cool.value, 0, 20))
                .put("stop_check_minutes", i("Stop check", stopChk.value, 1, 360))
                .put("taker_fee_pct", d("Taker fee", fee.value, 0.0, 1.0))
                .put("slippage_pct", d("Slippage", slip.value, 0.0, 1.0))
            onSave("scanner", patch)
        } catch (e: IllegalArgumentException) {
            toast(e.message ?: "Check the values")
        }
    }) { Text("Save scanner settings") }
}

// ---- keys ---------------------------------------------------------------------------------------------
@Composable
private fun KeysTab(st: EngineState, onSaveKeys: (String, String, () -> Unit) -> Unit, toast: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    H1("Binance API keys")
    Text(if (st.keysSet) "Keys are saved on the engine" else "No keys saved yet", color = if (st.keysSet) Green else Muted)
    OutlinedTextField(key, { key = it }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(secret, { secret = it }, label = { Text("API secret") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    Button(onClick = {
        if (key.isBlank() || secret.isBlank()) toast("Enter both the API key and the API secret.")
        else onSaveKeys(key.trim(), secret.trim()) { key = ""; secret = "" }
    }) { Text("Save keys on the engine") }
    MutedText("Keys are sent over the encrypted connection and stored on the engine only (owner-only file). They are " +
        "never shown again here. Use keys with Futures enabled, withdrawals DISABLED, and restrict them to your " +
        "server's IP address in Binance.")
}
