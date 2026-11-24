package com.pannulabs.smarttrade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipInputStream

/** Same cases as the Windows panel's tests/test_journal.py. */
class JournalTest {
    @Before fun utc() { Journal.zone = ZoneId.of("UTC") }

    private fun ms(y: Int, m: Int, d: Int, h: Int = 0) = Journal.ms(LocalDate.of(y, m, d)) + h * 3_600_000L

    private fun t(i: Int, opened: Long, status: String = "closed", mode: String = "paper", side: String = "LONG",
                  bal: Double? = 10_000.0, risk: Double? = 100.0, net: Double = 50.0, closed: Long? = null) = JTrade(
        i, "C${i}USDT", side, mode, status, 10.0, 100.0, if (side == "LONG") 90.0 else 110.0, 0.5,
        if (status == "closed") 0.6 else null, if (status == "closed") 105.0 else null, if (status == "closed") net else null,
        opened, if (status == "closed") (closed ?: opened + 3_600_000) else null, bal, risk)

    @Test fun rowsColumnsAndMath() {
        val rows = Journal.buildRows(listOf(t(2, ms(2026, 3, 5)), t(1, ms(2026, 1, 2), bal = null, risk = null, net = -20.0)))
        assertEquals(listOf(1, 2), rows.map { it.srn }); assertEquals("C1USDT", rows[0].pair)
        val r = rows[1]
        assertEquals(10.0, r.slPct!!, 1e-9); assertEquals(1000.0, r.size, 1e-9); assertEquals(1.1, r.fees, 1e-9)
        assertEquals(0.5, r.pnlPct!!, 1e-9); assertEquals(105.0, r.exit!!, 1e-9)
        val old = rows[0]
        assertNull(old.balance); assertNull(old.pnlPct); assertEquals(100.0, old.risk!!, 1e-9)
    }

    @Test fun openTradesOnlyWhenAsked() {
        val ts = listOf(t(1, ms(2026, 1, 2)), t(2, ms(2026, 1, 3), status = "open"))
        assertEquals(1, Journal.buildRows(ts).size)
        val rows = Journal.buildRows(ts, includeOpen = true)
        assertEquals(2, rows.size); assertNull(rows[1].exit); assertNull(rows[1].pnl)
    }

    @Test fun periodsAndAccountFilter() {
        val rows = Journal.buildRows(listOf(t(1, ms(2025, 12, 31, 23)), t(2, ms(2026, 1, 1)), t(3, ms(2026, 1, 15)),
            t(4, ms(2026, 2, 1), mode = "live")))
        fun pick(p: Pair<Long, Long>?, modes: Set<String> = emptySet()) =
            Journal.inPeriod(rows, p?.first, p?.second, modes).map { it.pair }
        assertEquals(4, pick(null).size)
        assertEquals(listOf("C2USDT"), pick(Journal.day(LocalDate.of(2026, 1, 1))))
        assertEquals(listOf("C1USDT"), pick(Journal.year(2025)))
        assertEquals(listOf("C3USDT", "C4USDT"), pick(Journal.custom(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 1))))
        assertEquals(listOf("C3USDT", "C4USDT"), pick(Journal.custom(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 15))))
        assertEquals(listOf("C4USDT"), pick(null, setOf("testnet", "live")))
        assertEquals(setOf("testnet", "live"), Journal.ACCOUNTS.first().second)       // Binance is the default
        assertEquals(listOf(1, 2), Journal.inPeriod(rows, ms(2026, 1, 1), ms(2026, 2, 1)).map { it.srn })
    }

    @Test fun monthAndYearSummaries() {
        val rows = Journal.buildRows(listOf(
            t(1, ms(2026, 1, 5), net = 100.0, bal = 10_000.0, closed = ms(2026, 1, 6)),
            t(2, ms(2026, 1, 20), net = -50.0, bal = 10_100.0, closed = ms(2026, 2, 2)),
            t(3, ms(2026, 3, 1), net = 30.0, bal = null, closed = ms(2026, 3, 3)),
            t(4, ms(2027, 1, 2), net = 10.0, bal = 9_000.0, closed = ms(2027, 1, 4)),
            t(5, ms(2026, 3, 9), status = "open", bal = null)), includeOpen = true)
        val m = Journal.summaryRows(rows, byYear = false)
        assertEquals(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1), LocalDate.of(2027, 1, 1)),
            m.map { it.period })
        val (jan, feb, mar, jan27) = m
        assertEquals(10_000.0, jan.start!!, 1e-9); assertEquals(10_100.0, jan.end!!, 1e-9); assertEquals(1.0, jan.pnlPct!!, 1e-9)
        assertEquals(1, feb.losses); assertEquals(10_100.0, feb.start!!, 1e-9); assertEquals(10_050.0, feb.end!!, 1e-9)
        assertEquals(10_050.0, mar.start!!, 1e-9); assertEquals(10_080.0, mar.end!!, 1e-9)
        assertEquals(9_000.0, jan27.start!!, 1e-9)
        val y = Journal.summaryRows(rows, byYear = true)
        assertEquals(listOf(2026, 2027), y.map { it.period.year })
        assertEquals(3, y[0].trades); assertEquals(80.0, y[0].pnl, 1e-9); assertEquals(10_080.0, y[0].end!!, 1e-9)
        val old = Journal.summaryRows(Journal.buildRows(listOf(t(9, ms(2025, 5, 1), bal = null, net = 5.0))), false)
        assertNull(old[0].start); assertNull(old[0].pnlPct)
    }

    @Test fun writesAnXlsxWorkbook() {
        val rows = Journal.buildRows(listOf(t(1, ms(2026, 1, 2)), t(2, ms(2026, 1, 3), side = "SHORT", net = -30.0)))
        val buf = ByteArrayOutputStream()
        Journal.writeXlsx(buf, Journal.COLUMNS, rows.map(Journal::cells), totals = listOf(6, 10, 12))
        val names = mutableListOf<String>(); var sheet = ""
        ZipInputStream(buf.toByteArray().inputStream()).use { z ->
            while (true) { val e = z.nextEntry ?: break; names += e.name
                if (e.name == "xl/worksheets/sheet1.xml") sheet = z.readBytes().toString(Charsets.UTF_8) }
        }
        assertTrue(names.containsAll(listOf("[Content_Types].xml", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml")))
        assertTrue(sheet.contains("<t>SrN</t>") && sheet.contains("<t>C2USDT</t>") && sheet.contains("<t>TOTAL</t>"))
        assertTrue(sheet.contains("<f>SUM(M2:M3)</f>"))
    }
}
