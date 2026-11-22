package com.pannulabs.smarttrade

import org.json.JSONArray
import org.json.JSONObject

// ---- small JSON helpers (the engine sends null for empty numbers) ----------------------------------------
fun JSONObject.num(k: String): Double = if (!has(k) || isNull(k)) 0.0 else optDouble(k, 0.0).let { if (it.isNaN()) 0.0 else it }
fun JSONObject.numOrNull(k: String): Double? = if (!has(k) || isNull(k)) null else optDouble(k).takeUnless { it.isNaN() }
fun JSONObject.long(k: String): Long = if (!has(k) || isNull(k)) 0L else optLong(k, 0L)
fun JSONObject.str(k: String): String = if (!has(k) || isNull(k)) "" else optString(k, "")
fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

data class Position(
    val id: Int, val symbol: String, val side: String, val qty: Double, val entry: Double, val mark: Double,
    val sl: Double, val unrealized: Double, val unrealizedPct: Double, val openedMs: Long,
) {
    companion object {
        fun from(o: JSONObject) = Position(
            o.optInt("id"), o.str("symbol"), o.str("side"), o.num("qty"), o.num("entry_price"), o.num("mark"),
            o.num("sl_price"), o.num("unrealized"), o.num("unrealized_pct"), o.long("opened_ms"),
        )
    }
}

data class Snapshot(
    val equity: Double, val sizingBase: Double, val used: Double, val capacity: Double, val todayRealized: Double,
    val positions: List<Position>,
) {
    companion object {
        fun from(o: JSONObject?) = o?.let {
            Snapshot(it.num("equity"), it.num("sizing_base"), it.num("used_notional"), it.num("capacity"),
                it.num("today_realized"), it.optJSONArray("positions").objects().map(Position::from))
        }
    }
}

data class Trade(
    val id: Int, val symbol: String, val side: String, val mode: String, val status: String, val entry: Double,
    val exit: Double, val reason: String, val netPnl: Double, val closedMs: Long,
) {
    companion object {
        fun from(o: JSONObject) = Trade(
            o.optInt("id"), o.str("symbol"), o.str("side"), o.str("mode"), o.str("status"), o.num("entry_price"),
            o.num("exit_price"), o.str("exit_reason"), o.num("net_pnl"), o.long("closed_ms"),
        )
    }
}

data class Skip(val timeMs: Long, val symbol: String, val side: String, val reason: String) {
    companion object {
        fun from(o: JSONObject) = Skip(o.long("time_ms"), o.str("symbol"), o.str("side"), o.str("reason"))
    }
}

data class SignalRow(
    val symbol: String, val side: String, val openMs: Long, val closeMs: Long, val entry: Double, val sl: Double,
    val slPct: Double, val exit: Double, val reason: String, val netPct: Double,
) {
    companion object {
        fun from(o: JSONObject) = SignalRow(
            o.str("symbol"), o.str("side"), o.long("open_time_ms"), o.long("close_time_ms"), o.num("entry_price"),
            o.num("sl_price"), o.num("sl_pct"), o.num("exit_price"), o.str("exit_reason"), o.num("net_pnl_pct"),
        )
    }
}

data class Note(val timeMs: Long, val kind: String, val text: String) {
    companion object {
        fun from(o: JSONObject) = Note(o.long("time_ms"), o.str("kind"), o.str("text"))
    }
}

/** Everything the engine told us, merged the same way panel/runtime.py does. Immutable: every update is a copy. */
data class EngineState(
    val version: String = "",
    val trading: JSONObject = JSONObject(),
    val scannerSettings: JSONObject = JSONObject(),
    val keysSet: Boolean = false,
    val paused: Boolean = false,
    val brokerError: String = "",
    val snapshot: Snapshot? = null,
    val scanner: JSONObject = JSONObject(),
    val notes: List<Note> = emptyList(),
    val trades: List<Trade> = emptyList(),
    val skips: List<Skip> = emptyList(),
    val openSignals: List<SignalRow> = emptyList(),
    val closedSignals: List<SignalRow> = emptyList(),
) {
    val modeBadge: String
        get() = when {
            trading.str("mode").isEmpty() -> ""
            trading.str("mode") == "paper" -> "PAPER"
            trading.optBoolean("testnet", true) -> "DEMO"
            else -> "LIVE"
        }

    fun withSettings(settings: JSONObject?): EngineState =
        if (settings == null) this
        else copy(trading = settings.optJSONObject("trading") ?: trading,
            scannerSettings = settings.optJSONObject("scanner") ?: scannerSettings)

    fun withTables(o: JSONObject): EngineState = copy(
        trades = if (o.has("trades")) o.optJSONArray("trades").objects().map(Trade::from) else trades,
        skips = if (o.has("skips")) o.optJSONArray("skips").objects().map(Skip::from) else skips,
        openSignals = if (o.has("open_signals")) o.optJSONArray("open_signals").objects().map(SignalRow::from) else openSignals,
        closedSignals = if (o.has("closed_signals")) o.optJSONArray("closed_signals").objects().map(SignalRow::from) else closedSignals,
    )

    /** Apply one push message from the engine. */
    fun merge(msg: JSONObject): EngineState = when (msg.optString("type")) {
        "hello" -> fromFull(msg.optJSONObject("state") ?: JSONObject())
        "snapshot" -> copy(
            snapshot = Snapshot.from(msg.optJSONObject("snapshot")) ?: snapshot,
            brokerError = msg.str("broker_error"),
            scanner = msg.optJSONObject("scanner") ?: scanner,
            paused = msg.optBoolean("paused", paused),
        )
        "tables" -> withTables(msg)
        "note" -> copy(notes = (notes + Note.from(msg)).takeLast(300))
        else -> this
    }

    companion object {
        fun fromFull(s: JSONObject): EngineState {
            val ks = s.optJSONObject("keys_set")
            return EngineState(
                version = s.str("version"),
                keysSet = ks != null && ks.optBoolean("binance_key") && ks.optBoolean("binance_secret"),
                paused = s.optBoolean("paused"),
                brokerError = s.str("broker_error"),
                snapshot = Snapshot.from(s.optJSONObject("snapshot")),
                scanner = s.optJSONObject("scanner") ?: JSONObject(),
                notes = s.optJSONArray("notes").objects().map(Note::from),
            ).withSettings(s.optJSONObject("settings")).withTables(s)
        }
    }
}
