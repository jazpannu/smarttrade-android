package com.pannulabs.smarttrade

import org.json.JSONObject
import java.util.Base64

/** The same PLST1-... code the Windows panel uses: host + port + token + certificate fingerprint. */
data class ConnectionInfo(val host: String, val port: Int, val token: String, val fingerprint: String) {
    val url: String
        get() = "wss://${if (host.contains(":")) "[$host]" else host}:$port"
}

object ConnectionCode {
    const val PREFIX = "PLST1-"

    fun decode(code: String): ConnectionInfo {
        val clean = code.filterNot { it.isWhitespace() }
        require(clean.startsWith(PREFIX)) { "This is not a SmartTrade connection code (it should start with $PREFIX)" }
        val body = clean.removePrefix(PREFIX)
        try {
            val padded = body + "=".repeat((4 - body.length % 4) % 4)
            val json = JSONObject(String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8))
            return ConnectionInfo(
                host = json.getString("h"),
                port = json.getInt("p"),
                token = json.getString("t"),
                fingerprint = json.getString("f").lowercase(),
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("The connection code is damaged - copy it again from the server")
        }
    }
}
