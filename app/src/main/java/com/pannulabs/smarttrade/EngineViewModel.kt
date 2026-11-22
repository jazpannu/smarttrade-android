package com.pannulabs.smarttrade

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.json.JSONObject

data class Conn(val state: ConnState = ConnState.STOPPED, val detail: String = "")

class EngineViewModel(app: Application) : AndroidViewModel(app) {
    private val codes = CodeStore(app)
    private var client: EngineClient? = null

    private val _conn = MutableStateFlow(Conn())
    val conn: StateFlow<Conn> = _conn.asStateFlow()
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()
    /** One-off messages for a snackbar ("Settings saved", errors). */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** Compose state, so the Settings page updates when a code is saved or forgotten. */
    var hasCode by mutableStateOf(codes.get() != null)
        private set

    init {
        codes.get()?.let { connect(it, save = false) }
    }

    fun toastShown() { _toast.value = null }
    fun showToast(text: String) { _toast.value = text }
    private fun say(text: String) = showToast(text)

    fun connect(code: String, save: Boolean = true): String? {
        val info = try { ConnectionCode.decode(code) } catch (e: IllegalArgumentException) { return e.message }
        if (save) { codes.set(code.trim()); hasCode = true }
        client?.stop()
        client = EngineClient(info, viewModelScope + Dispatchers.IO,
            onPush = { msg -> _state.update { it.merge(msg) } },
            onConn = { s, d -> _conn.value = Conn(s, d) }).also { it.start() }
        return null
    }

    fun disconnect(forget: Boolean) {
        client?.stop(); client = null
        if (forget) { codes.clear(); hasCode = false; _state.value = EngineState() }
    }

    fun reconnect() { codes.get()?.let { connect(it, save = false) } }

    private fun call(cmd: String, args: JSONObject = JSONObject(), onOk: (JSONObject) -> Unit = {}) {
        val c = client ?: return say("Not connected to the engine")
        viewModelScope.launch(Dispatchers.IO) {
            c.request(cmd, args)
                .onSuccess { onOk(it) }
                .onFailure { say(it.message ?: "Command failed") }
        }
    }

    fun setPaused(on: Boolean) {
        _state.update { it.copy(paused = on) }
        call("pause", JSONObject().put("on", on)) { r -> _state.update { it.copy(paused = r.optBoolean("paused", on)) } }
    }

    fun closeTrade(p: Position) = call("close_trade", JSONObject().put("id", p.id)) { r ->
        say(if (r.optBoolean("closed")) "${p.symbol} closed" else "${p.symbol} is no longer open")
    }

    fun closeAll() = call("close_all") { r -> say("Closed ${r.optInt("closed")} position(s)") }

    fun saveSettings(section: String, patch: JSONObject) =
        call("set_settings", JSONObject().put("patch", JSONObject().put(section, patch))) { r ->
            _state.update { it.withSettings(r.optJSONObject("settings")) }
            val extra = r.str("message")
            say("Settings saved and applied on the engine" + if (extra.isNotEmpty()) ". $extra" else "")
        }

    fun saveKeys(key: String, secret: String, onDone: () -> Unit) =
        call("set_api_keys", JSONObject().put("key", key).put("secret", secret)) {
            _state.update { it.copy(keysSet = true) }
            say("API keys saved on the engine")
            onDone()
        }

    override fun onCleared() {
        client?.stop()
    }
}
