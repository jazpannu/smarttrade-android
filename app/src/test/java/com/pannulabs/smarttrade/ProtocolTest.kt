package com.pannulabs.smarttrade

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Uses messages recorded from the real Python engine (tests/engine_fixtures.py, paper trade on TESTUSDT). */
class ProtocolTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun decodesTheEnginesConnectionCode() {
        val info = ConnectionCode.decode(res("code.txt"))
        assertEquals("203.0.113.7", info.host)
        assertEquals(8770, info.port)
        assertEquals("tok_abc123", info.token)
        assertEquals("ab".repeat(32), info.fingerprint)
        assertEquals("wss://203.0.113.7:8770", info.url)
        // whitespace from copy/paste is ignored, like the panel
        assertEquals(info, ConnectionCode.decode("  " + res("code.txt").chunked(20).joinToString("\n") + " "))
    }

    @Test
    fun rejectsBadCodes() {
        for (bad in listOf("hello", "PLST1-!!!", "PLST1-eyJ4IjoxfQ")) {
            try { ConnectionCode.decode(bad); fail("accepted $bad") } catch (e: IllegalArgumentException) { }
        }
    }

    @Test
    fun hydratesFromHelloAndMergesPushes() {
        var st = EngineState().merge(JSONObject(res("hello.json")))
        assertEquals("PAPER", st.modeBadge)
        assertFalse(st.keysSet)
        assertEquals("paper", st.trading.str("mode"))
        assertTrue(st.scannerSettings.has("sl_max_pct"))

        st = st.merge(JSONObject(res("snapshot.json")))
        val snap = st.snapshot
        assertNotNull(snap)
        assertEquals(1, snap!!.positions.size)
        val p = snap.positions[0]
        assertEquals("TESTUSDT", p.symbol)
        assertEquals("LONG", p.side)
        assertTrue(p.qty > 0 && p.entry > 0 && p.sl > 0 && p.mark > 0)
        assertTrue(snap.equity > 0 && snap.capacity > 0)

        st = st.merge(JSONObject(res("tables.json")))
        assertTrue(st.trades.isNotEmpty())
        assertEquals("open", st.trades[0].status)
        assertTrue(st.openSignals.isNotEmpty())

        val before = st.notes.size
        st = st.merge(JSONObject(res("note.json")))
        assertEquals(before + 1, st.notes.size)

        // a snapshot push without a snapshot keeps the last one (same as the panel)
        st = st.merge(JSONObject().put("type", "snapshot").put("snapshot", JSONObject.NULL).put("paused", true))
        assertNotNull(st.snapshot)
        assertTrue(st.paused)
    }
}
