package com.pannulabs.smarttrade

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Connection states, the same names the Windows panel uses. */
enum class ConnState { STOPPED, CONNECTING, CONNECTED, RECONNECTING, AUTH_ERROR, CERT_ERROR }

class CertMismatch(msg: String) : CertificateException(msg)

/**
 * Talks to the SmartTrade engine exactly like panel/client.py:
 *  1. TLS, accepting ONLY the certificate whose SHA-256 is in the connection code (checked during the handshake,
 *     so the token is never sent to anyone else),
 *  2. {"type":"auth","token":...} -> {"type":"hello","state":{...}},
 *  3. live pushes (snapshot / tables / note) and {"type":"req"} -> {"type":"resp"} commands,
 *  4. automatic reconnect with back-off, except after a rejected code or a certificate mismatch.
 */
class EngineClient(
    private val info: ConnectionInfo,
    private val scope: CoroutineScope,
    private val onPush: (JSONObject) -> Unit,
    private val onConn: (ConnState, String) -> Unit,
) {
    private val ids = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Result<JSONObject>>>()
    @Volatile private var ws: WebSocket? = null
    @Volatile private var authed = false
    @Volatile private var stopped = false
    private var loop: Job? = null

    private val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw CertificateException("not a server")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertMismatch("no certificate")
            val fp = MessageDigest.getInstance("SHA-256").digest(leaf.encoded).joinToString("") { "%02x".format(it) }
            if (fp != info.fingerprint) throw CertMismatch(
                "The server's certificate does not match the connection code. Do not connect - create a new code on your server."
            )
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val http: OkHttpClient by lazy {
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        OkHttpClient.Builder()
            .sslSocketFactory(ctx.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }          // identity is the pinned certificate, not a domain name
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    fun start() {
        stopped = false
        loop = scope.launch {
            var backoff = 1000L
            while (!stopped) {
                onConn(if (backoff == 1000L) ConnState.CONNECTING else ConnState.RECONNECTING, "")
                val outcome = session()
                if (outcome == Outcome.FATAL || stopped) break
                if (outcome == Outcome.WAS_CONNECTED) backoff = 1000L
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(20_000L)
            }
            if (stopped) onConn(ConnState.STOPPED, "")
        }
    }

    fun stop() {
        stopped = true
        ws?.close(1000, "bye")
        ws = null
        loop?.cancel()
        failPending("disconnected")
        onConn(ConnState.STOPPED, "")
    }

    private enum class Outcome { FATAL, WAS_CONNECTED, FAILED }

    /** One connection attempt; returns when the socket is gone. */
    private suspend fun session(): Outcome {
        val done = CompletableDeferred<Outcome>()
        authed = false
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject().put("type", "auth").put("token", info.token).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (stopped) return
                val msg = try { JSONObject(text) } catch (e: Exception) { return }
                when (msg.optString("type")) {
                    "hello" -> {
                        authed = true
                        ws = webSocket
                        onConn(ConnState.CONNECTED, "")
                        onPush(msg)
                    }
                    "auth_error" -> {
                        onConn(ConnState.AUTH_ERROR, "The engine rejected this connection code (it may have been revoked)")
                        webSocket.close(1000, null)
                        done.complete(Outcome.FATAL)
                    }
                    "resp" -> {
                        val d = pending.remove(msg.optInt("id")) ?: return
                        if (msg.optBoolean("ok")) d.complete(Result.success(msg.optJSONObject("result") ?: JSONObject()))
                        else d.complete(Result.failure(RuntimeException(msg.optString("error", "failed"))))
                    }
                    else -> onPush(msg)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finish(if (code == 4001) Outcome.FATAL else null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (stopped) { finish(null); return }
                var c: Throwable? = t
                while (c != null) {
                    if (c is CertMismatch) {
                        onConn(ConnState.CERT_ERROR, c.message ?: "certificate mismatch")
                        finish(Outcome.FATAL); return
                    }
                    c = c.cause
                }
                onConn(ConnState.RECONNECTING, t.message ?: t.javaClass.simpleName)
                finish(null)
            }

            private fun finish(o: Outcome?) {
                ws = null
                failPending("connection lost")
                done.complete(o ?: if (authed) Outcome.WAS_CONNECTED else Outcome.FAILED)
            }
        }
        http.newWebSocket(Request.Builder().url(info.url.replaceFirst("wss://", "https://")).build(), listener)
        return done.await()
    }

    private fun failPending(why: String) {
        pending.values.forEach { it.complete(Result.failure(RuntimeException(why))) }
        pending.clear()
    }

    /** Send a command; returns the engine's result or its error text. */
    suspend fun request(cmd: String, args: JSONObject = JSONObject()): Result<JSONObject> {
        val socket = ws ?: return Result.failure(RuntimeException("not connected to the engine"))
        val id = ids.getAndIncrement()
        val d = CompletableDeferred<Result<JSONObject>>()
        pending[id] = d
        val ok = socket.send(JSONObject().put("type", "req").put("id", id).put("cmd", cmd).put("args", args).toString())
        if (!ok) {
            pending.remove(id)
            return Result.failure(RuntimeException("send failed"))
        }
        return try {
            withTimeout(60_000) { d.await() }
        } catch (e: Exception) {
            pending.remove(id)
            Result.failure(RuntimeException("no answer from the engine"))
        }
    }
}
