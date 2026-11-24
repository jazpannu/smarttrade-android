package com.pannulabs.smarttrade

import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

/**
 * The Excel View: every trade as a journal row, day / custom periods, month / year summaries and the .xlsx export.
 * Same rules as the Windows panel (panel/trade_journal.py). No Android or JSON in here, so it is unit-tested directly.
 */
data class JTrade(
    val id: Int, val symbol: String, val side: String, val mode: String, val status: String,
    val qty: Double, val entry: Double, val sl: Double, val entryFee: Double, val exitFee: Double?,
    val exit: Double?, val net: Double?, val openedMs: Long, val closedMs: Long?,
    val balance: Double?, val risk: Double?,
)

data class JRow(
    val srn: Int, val id: Int, val mode: String, val status: String, val openedMs: Long, val closedMs: Long?,
    val pair: String, val side: String, val slPct: Double?, val balance: Double?, val risk: Double?, val size: Double,
    val entry: Double, val exit: Double?, val fees: Double, val pnlPct: Double?, val pnl: Double?,
)

data class SRow(
    val period: LocalDate, val start: Double?, val end: Double?, val trades: Int, val wins: Int, val losses: Int,
    val winRate: Double, val fees: Double, val pnl: Double, val pnlPct: Double?,
)

enum class Kind { INT, DATE, TEXT, PCT, MONEY, PRICE, PERIOD }

data class Col(val header: String, val kind: Kind)

object Journal {
    var zone: ZoneId = ZoneId.systemDefault()

    val COLUMNS = listOf(
        Col("SrN", Kind.INT), Col("DATE", Kind.DATE), Col("PAIR", Kind.TEXT), Col("LONG/ SHORT", Kind.TEXT),
        Col("STOP LOSS %", Kind.PCT), Col("ACCOUNT BALANCE $", Kind.MONEY), Col("RISK IN $", Kind.MONEY),
        Col("POSITION SIZE IN $", Kind.MONEY), Col("Entry PRICE", Kind.PRICE), Col("Exit PRICE", Kind.PRICE),
        Col("FEES", Kind.MONEY), Col("PROFIT/LOSS IN %", Kind.PCT), Col("PROFIT/ LOSS IN $", Kind.MONEY),
    )

    fun summaryColumns(byYear: Boolean) = listOf(
        Col(if (byYear) "YEAR" else "MONTH", Kind.PERIOD), Col("STARTING BALANCE $", Kind.MONEY),
        Col("ENDING BALANCE $", Kind.MONEY), Col("TRADES", Kind.INT), Col("WINS", Kind.INT), Col("LOSSES", Kind.INT),
        Col("WIN RATE %", Kind.PCT), Col("FEES", Kind.MONEY), Col("PROFIT/ LOSS IN $", Kind.MONEY),
        Col("PROFIT/LOSS IN %", Kind.PCT),
    )

    fun cells(r: JRow): List<Any?> = listOf(r.srn, r.openedMs, r.pair, r.side, r.slPct, r.balance, r.risk, r.size,
        r.entry, r.exit, r.fees, r.pnlPct, r.pnl)

    fun cells(r: SRow): List<Any?> = listOf(r.period, r.start, r.end, r.trades, r.wins, r.losses, r.winRate, r.fees,
        r.pnl, r.pnlPct)

    /** Account filter: label to modes. Default = trades that really filled on Binance. */
    val ACCOUNTS = listOf(
        "Binance (demo + live)" to setOf("testnet", "live"), "Binance demo" to setOf("testnet"),
        "Binance live" to setOf("live"), "Paper (simulated)" to setOf("paper"), "All accounts" to emptySet(),
    )

    /** Journal rows, oldest first, numbered 1..n. Values are numbers (null when unknown). */
    fun buildRows(trades: List<JTrade>, includeOpen: Boolean = false): List<JRow> {
        val out = trades.sortedWith(compareBy({ it.openedMs }, { it.id }))
            .filter { it.status == "closed" || includeOpen }
            .map { t ->
                val closed = t.status == "closed"
                val net = if (closed) t.net else null
                JRow(
                    0, t.id, t.mode, t.status, t.openedMs, if (closed) t.closedMs else null, t.symbol, t.side,
                    if (t.entry != 0.0) abs(t.entry - t.sl) / t.entry * 100 else null,
                    t.balance, t.risk ?: (t.qty * abs(t.entry - t.sl)), t.qty * t.entry, t.entry,
                    if (closed) t.exit else null, t.entryFee + (t.exitFee ?: 0.0),
                    if (net != null && t.balance != null && t.balance != 0.0) net / t.balance * 100 else null, net,
                )
            }
        return out.mapIndexed { i, r -> r.copy(srn = i + 1) }
    }

    /** Filter by open time [fromMs, toMs) and account modes (empty = all), renumbering SrN from 1. */
    fun inPeriod(rows: List<JRow>, fromMs: Long?, toMs: Long?, modes: Set<String> = emptySet()): List<JRow> =
        rows.filter { (fromMs == null || it.openedMs >= fromMs) && (toMs == null || it.openedMs < toMs) &&
            (modes.isEmpty() || it.mode in modes) }
            .mapIndexed { i, r -> r.copy(srn = i + 1) }

    // ---- periods -----------------------------------------------------------------------------------
    fun ms(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun day(d: LocalDate) = ms(d) to ms(d.plusDays(1))
    fun year(y: Int) = ms(LocalDate.of(y, 1, 1)) to ms(LocalDate.of(y + 1, 1, 1))
    fun custom(a: LocalDate, b: LocalDate): Pair<Long, Long> {
        val (lo, hi) = if (a <= b) a to b else b to a
        return ms(lo) to ms(hi.plusDays(1))
    }

    private fun bucket(ms: Long, byYear: Boolean): LocalDate =
        date(ms).let { if (byYear) LocalDate.of(it.year, 1, 1) else it.withDayOfMonth(1) }

    /**
     * One row per month (or year) of CLOSED trades, grouped by close date. Starting balance = the balance recorded
     * on the first trade opened in that period, else the previous period's ending balance; ending = start + P/L.
     */
    fun summaryRows(rows: List<JRow>, byYear: Boolean): List<SRow> {
        val groups = rows.filter { it.pnl != null && it.closedMs != null }.groupBy { bucket(it.closedMs!!, byYear) }
        val firstBal = HashMap<LocalDate, Double>()
        rows.sortedBy { it.openedMs }.forEach { r ->
            if (r.balance != null && r.balance != 0.0) firstBal.putIfAbsent(bucket(r.openedMs, byYear), r.balance)
        }
        var prevEnd: Double? = null
        return groups.keys.sorted().map { k ->
            val g = groups.getValue(k)
            val pnl = g.sumOf { it.pnl!! }
            val wins = g.count { it.pnl!! > 0 }
            val start = firstBal[k] ?: prevEnd
            val end = start?.plus(pnl)
            prevEnd = end
            SRow(k, start, end, g.size, wins, g.size - wins, wins * 100.0 / g.size, g.sumOf { it.fees }, pnl,
                if (start != null && start != 0.0) pnl / start * 100 else null)
        }
    }

    // ---- .xlsx export ------------------------------------------------------------------------------
    /** A real Excel workbook (numbers, dates, % and money formats, header styling, filters, totals row). */
    fun writeXlsx(out: OutputStream, columns: List<Col>, rows: List<List<Any?>>, totals: List<Int>, byYear: Boolean = false) {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        fun colName(i: Int): String { var n = i + 1; var s = ""; while (n > 0) { val m = (n - 1) % 26; s = ('A' + m) + s; n = (n - 1) / 26 }; return s }
        fun serial(ms: Long): Double {
            val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
            val days = ldt.toLocalDate().toEpochDay() + 25569.0
            return days + ldt.toLocalTime().toSecondOfDay() / 86400.0
        }
        // style ids, see styles.xml below
        val st = mapOf(Kind.DATE to 2, Kind.MONEY to 3, Kind.PCT to 4, Kind.INT to 5, Kind.PRICE to 0, Kind.TEXT to 0,
            Kind.PERIOD to if (byYear) 7 else 6)
        val sheet = StringBuilder()
        val last = rows.size + 1
        sheet.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sheet.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        sheet.append("<cols>")
        columns.forEachIndexed { i, c ->
            val w = maxOf(when (c.kind) { Kind.INT -> 7; Kind.DATE -> 18; Kind.PERIOD -> 12; Kind.MONEY -> 16; else -> 13 }, minOf(c.header.length + 2, 20))
            sheet.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
        }
        sheet.append("</cols><sheetData>")
        sheet.append("""<row r="1" ht="32" customHeight="1">""")
        columns.forEachIndexed { i, c -> sheet.append("""<c r="${colName(i)}1" t="inlineStr" s="1"><is><t>${esc(c.header)}</t></is></c>""") }
        sheet.append("</row>")
        rows.forEachIndexed { ri, row ->
            val r = ri + 2
            sheet.append("""<row r="$r">""")
            row.forEachIndexed { ci, v ->
                val ref = "${colName(ci)}$r"
                val kind = columns[ci].kind
                when {
                    v == null -> {}
                    kind == Kind.DATE && v is Long -> sheet.append("""<c r="$ref" s="2"><v>${serial(v)}</v></c>""")
                    kind == Kind.PERIOD && v is LocalDate -> sheet.append("""<c r="$ref" s="${st[kind]}"><v>${v.toEpochDay() + 25569}</v></c>""")
                    v is Number -> {
                        val x = if (kind == Kind.PCT) v.toDouble() / 100 else v.toDouble()
                        val pnlCol = columns[ci].header.startsWith("PROFIT")
                        val s = if (pnlCol && x > 0) (if (kind == Kind.PCT) 10 else 8)
                                else if (pnlCol && x < 0) (if (kind == Kind.PCT) 11 else 9) else st[kind]
                        sheet.append("""<c r="$ref" s="$s"><v>$x</v></c>""")
                    }
                    else -> {
                        val s = when (v.toString()) { "LONG" -> 12; "SHORT" -> 13; else -> 0 }
                        sheet.append("""<c r="$ref" t="inlineStr" s="$s"><is><t>${esc(v.toString())}</t></is></c>""")
                    }
                }
            }
            sheet.append("</row>")
        }
        if (rows.isNotEmpty()) {
            val tr = last + 1
            sheet.append("""<row r="$tr"><c r="A$tr" t="inlineStr" s="14"><is><t>TOTAL</t></is></c>""")
            totals.sorted().forEach { ci ->
                val L = colName(ci)
                val s = if (columns[ci].kind == Kind.INT) 15 else 16
                sheet.append("""<c r="$L$tr" s="$s"><f>SUM(${L}2:$L$last)</f></c>""")
            }
            sheet.append("</row>")
        }
        sheet.append("</sheetData>")
        sheet.append("""<autoFilter ref="A1:${colName(columns.size - 1)}$last"/></worksheet>""")

        val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="3"><numFmt numFmtId="164" formatCode="dd\-mmm\-yyyy\ hh:mm"/><numFmt numFmtId="165" formatCode="mmm\ yyyy"/><numFmt numFmtId="166" formatCode="yyyy"/></numFmts>
<fonts count="6"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font><font><sz val="11"/><color rgb="FF1E8E3E"/><name val="Calibri"/></font><font><sz val="11"/><color rgb="FFD93025"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FF1E8E3E"/><name val="Calibri"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF0B5D3B"/><bgColor indexed="64"/></patternFill></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="17">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="10" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="1" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="166" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
<xf numFmtId="4" fontId="2" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>
<xf numFmtId="4" fontId="3" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>
<xf numFmtId="10" fontId="2" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>
<xf numFmtId="10" fontId="3" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>
<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="1" fontId="4" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>
<xf numFmtId="4" fontId="4" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
        val parts = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Trades" sheetId="1" r:id="rId1"/></sheets><definedNames><definedName name="_xlnm._FilterDatabase" localSheetId="0" hidden="1">Trades!${'$'}A${'$'}1:${'$'}${colName(columns.size - 1)}${'$'}$last</definedName></definedNames></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""",
            "xl/styles.xml" to styles,
            "xl/worksheets/sheet1.xml" to sheet.toString(),
        )
        ZipOutputStream(out).use { z ->
            parts.forEach { (name, body) -> z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray(Charsets.UTF_8)); z.closeEntry() }
        }
    }

    // ---- on-screen formatting ----------------------------------------------------------------------
    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.US)
    private val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)

    fun fmt(kind: Kind, v: Any?, byYear: Boolean = false): String = when {
        v == null -> ""
        kind == Kind.DATE && v is Long -> LocalDateTime.ofInstant(Instant.ofEpochMilli(v), zone).format(dateFmt)
        kind == Kind.PERIOD && v is LocalDate -> if (byYear) v.year.toString() else v.format(monthFmt)
        kind == Kind.MONEY && v is Number -> String.format(Locale.US, "%,.2f", v.toDouble())
        kind == Kind.PCT && v is Number -> String.format(Locale.US, "%.2f%%", v.toDouble())
        kind == Kind.PRICE && v is Number -> java.math.BigDecimal(v.toDouble()).round(java.math.MathContext(8)).stripTrailingZeros().toPlainString()
        else -> v.toString()
    }
}
