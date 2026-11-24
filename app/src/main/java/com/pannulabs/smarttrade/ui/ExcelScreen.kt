package com.pannulabs.smarttrade.ui

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pannulabs.smarttrade.Col
import com.pannulabs.smarttrade.JTrade
import com.pannulabs.smarttrade.Journal
import com.pannulabs.smarttrade.Kind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class View(val label: String) { ALL("All trades"), DAY("Day"), CUSTOM("Custom"), MONTH("Month"), YEAR("Year") }

private val dayFmt = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)

private fun width(c: Col): Dp = when (c.kind) {
    Kind.INT -> if (c.header == "SrN") 44.dp else 64.dp
    Kind.DATE -> 132.dp; Kind.TEXT -> 100.dp; Kind.PCT -> 84.dp; Kind.MONEY -> 104.dp
    Kind.PRICE -> 90.dp; Kind.PERIOD -> 84.dp
}

/** The Windows panel's Excel View: trade-by-trade journal, month / year summaries, .xlsx export. */
@Composable
fun ExcelScreen(trades: List<JTrade>, connected: Boolean, onRefresh: () -> Unit, toast: (String) -> Unit) {
    val ctx = LocalContext.current
    var view by rememberSaveable { mutableStateOf(View.ALL) }
    var account by rememberSaveable { mutableIntStateOf(0) }            // 0 = Binance (demo + live)
    var withOpen by rememberSaveable { mutableStateOf(false) }
    var dayEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var fromEpoch by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).toEpochDay()) }
    var toEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var sortCol by rememberSaveable { mutableIntStateOf(0) }
    var sortAsc by rememberSaveable { mutableStateOf(true) }
    val day = LocalDate.ofEpochDay(dayEpoch)
    val modes = Journal.ACCOUNTS[account].second
    val summary = view == View.MONTH || view == View.YEAR

    // ---- data for the current view ------------------------------------------------------------------
    val columns: List<Col>
    val cells: List<List<Any?>>
    val label: String
    val headline: String
    if (summary) {
        val byYear = view == View.YEAR
        columns = Journal.summaryColumns(byYear)
        var rows = Journal.summaryRows(Journal.inPeriod(Journal.buildRows(trades, true), null, null, modes), byYear)
        if (!byYear) rows = rows.filter { it.period.year == year }
        cells = rows.map(Journal::cells)
        label = if (byYear) "All years" else year.toString()
        val n = rows.sumOf { it.trades }; val w = rows.sumOf { it.wins }
        headline = if (rows.isEmpty()) "$label: no closed trades"
            else "$label: ${rows.size} ${if (byYear) "year" else "month"}${if (rows.size != 1) "s" else ""}, $n closed trades, " +
                "$w winners (${if (n > 0) w * 100 / n else 0}%), net ${money(rows.sumOf { it.pnl }, true)} $"
    } else {
        val (from, to) = when (view) {
            View.DAY -> Journal.day(day)
            View.CUSTOM -> Journal.custom(LocalDate.ofEpochDay(fromEpoch), LocalDate.ofEpochDay(toEpoch))
            else -> null to null
        }
        columns = Journal.COLUMNS
        val rows = Journal.inPeriod(Journal.buildRows(trades, withOpen), from, to, modes)
        cells = rows.map(Journal::cells)
        label = when (view) {
            View.DAY -> day.format(dayFmt)
            View.CUSTOM -> "${LocalDate.ofEpochDay(minOf(fromEpoch, toEpoch)).format(dayFmt)} - ${LocalDate.ofEpochDay(maxOf(fromEpoch, toEpoch)).format(dayFmt)}"
            else -> "All time"
        }
        val closed = rows.filter { it.pnl != null }; val w = closed.count { it.pnl!! > 0 }
        headline = if (rows.isEmpty()) "$label: no trades"
            else "$label: ${rows.size} trade${if (rows.size != 1) "s" else ""}, $w winners " +
                "(${if (closed.isNotEmpty()) w * 100 / closed.size else 0}% of ${closed.size} closed), " +
                "net ${money(closed.sumOf { it.pnl!! }, true)} $, fees ${money(rows.sumOf { it.fees })} $"
    }
    val sc = sortCol.coerceIn(0, columns.size - 1)
    @Suppress("UNCHECKED_CAST")
    val sorted = cells.sortedWith { a, b ->
        val x = a[sc] as Comparable<Any>?; val y = b[sc] as Comparable<Any>?
        val c = when { x == null && y == null -> 0; x == null -> 1; y == null -> -1; else -> x.compareTo(y) }
        if (sortAsc || x == null || y == null) c else -c
    }

    // ---- export -------------------------------------------------------------------------------------
    val kind = when (view) { View.MONTH -> "monthly summary"; View.YEAR -> "yearly summary"; else -> "trades" }
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val totals = if (summary) listOf(3, 4, 5, 7, 8) else listOf(6, 10, 12)
            ctx.contentResolver.openOutputStream(uri)?.use {
                Journal.writeXlsx(it, columns, sorted, totals, byYear = view == View.YEAR)
            }
            toast("Exported ${sorted.size} ${if (summary) (if (view == View.YEAR) "years" else "months") else "trades"} to Excel")
        } catch (e: Exception) {
            toast("Export failed: ${e.message}")
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // view chips
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                View.entries.forEach { v -> Chip(v.label, v == view) { view = v; sortCol = 0; sortAsc = true } }
            }
            // period controls
            when (view) {
                View.DAY -> Stepper(day.format(dayFmt), { dayEpoch-- }, { dayEpoch++ }, onToday = { dayEpoch = LocalDate.now().toEpochDay() },
                    onPick = { pickDate(ctx, day) { dayEpoch = it.toEpochDay() } })
                View.MONTH -> Stepper(year.toString(), { year-- }, { year++ }, onToday = { year = LocalDate.now().year })
                View.CUSTOM -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateButton(LocalDate.ofEpochDay(fromEpoch)) { pickDate(ctx, LocalDate.ofEpochDay(fromEpoch)) { fromEpoch = it.toEpochDay() } }
                    Text("to", color = Muted, fontSize = 13.sp)
                    DateButton(LocalDate.ofEpochDay(toEpoch)) { pickDate(ctx, LocalDate.ofEpochDay(toEpoch)) { toEpoch = it.toEpochDay() } }
                }
                else -> {}
            }
            // account, open trades, actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountMenu(account) { account = it }
                Spacer(Modifier.width(4.dp))
                Row(Modifier.clickable(enabled = !summary) { withOpen = !withOpen }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(withOpen && !summary, { withOpen = it }, enabled = !summary,
                        colors = CheckboxDefaults.colors(checkedColor = Brand, checkmarkColor = OnBrand, uncheckedColor = Border))
                    Text("Open trades", color = if (summary) Faint else TextC, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh, enabled = connected) { Icon(Icons.Outlined.Refresh, "Refresh", tint = Muted) }
                Button(
                    onClick = {
                        if (sorted.isEmpty()) toast("There are no trades in this period to export")
                        else export.launch("SmartTrade $kind - ${label.replace(Regex("[^A-Za-z0-9 -]"), "_")}.xlsx")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = OnBrand),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding, shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("Excel", fontWeight = FontWeight.Bold)
                }
            }
            MutedText(headline)
        }
        // ---- the table (scrolls left/right as one; header stays on top) --------------------------------
        val hs = rememberScrollState()
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(bottom = 10.dp)
            .border(1.dp, Border, RoundedCornerShape(14.dp)).background(Panel, RoundedCornerShape(14.dp))) {
            HeaderRow(columns, hs, sc, sortAsc) { i -> if (i == sc) sortAsc = !sortAsc else { sortCol = i; sortAsc = true } }
            if (sorted.isEmpty()) {
                Empty(if (summary) "No closed trades in this period" else "No trades in this period", Icons.Outlined.CalendarMonth)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(sorted) { i, row -> DataRow(columns, row, hs, i % 2 == 1, view == View.YEAR) }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(columns: List<Col>, hs: ScrollState, sortCol: Int, asc: Boolean, onSort: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(hs).background(Panel2, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
        .padding(vertical = 8.dp)) {
        columns.forEachIndexed { i, c ->
            Text(c.header + if (i == sortCol) (if (asc) " ▴" else " ▾") else "", color = if (i == sortCol) TextC else Muted,
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 12.sp,
                modifier = Modifier.width(width(c)).clickable { onSort(i) }.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun DataRow(columns: List<Col>, row: List<Any?>, hs: ScrollState, odd: Boolean, byYear: Boolean) {
    Row(Modifier.horizontalScroll(hs).background(if (odd) Color(0xFF151C2B) else Panel).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        columns.forEachIndexed { i, c ->
            val v = row[i]
            val text = Journal.fmt(c.kind, v, byYear)
            Box(Modifier.width(width(c)), contentAlignment = Alignment.Center) {
                when {
                    c.header.startsWith("LONG") && text.isNotEmpty() -> Pill(text, sideColor(text), 11)
                    c.header.startsWith("PROFIT") && v is Number -> Pill(text, pnlColor(v.toDouble()), 11)
                    else -> Text(text, color = TextC, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.background(if (selected) Brand.copy(alpha = 0.16f) else Panel, RoundedCornerShape(50))
        .border(1.dp, if (selected) Brand else Border, RoundedCornerShape(50)).clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(text, color = if (selected) Brand else TextC, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun Stepper(text: String, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit, onPick: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous", tint = TextC) }
        Box(Modifier.background(Panel2, RoundedCornerShape(10.dp)).border(1.dp, Border, RoundedCornerShape(10.dp))
            .clickable(enabled = onPick != null) { onPick?.invoke() }.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(text, color = TextC, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next", tint = TextC) }
        OutlinedButton(onClick = onToday, border = BorderStroke(1.dp, Border), shape = RoundedCornerShape(10.dp)) {
            Text("Today", color = TextC, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DateButton(d: LocalDate, onClick: () -> Unit) {
    Row(Modifier.background(Panel2, RoundedCornerShape(10.dp)).border(1.dp, Border, RoundedCornerShape(10.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CalendarMonth, null, tint = Muted, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
        Text(d.format(dayFmt), color = TextC, fontSize = 13.sp)
    }
}

@Composable
private fun AccountMenu(selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.background(Panel2, RoundedCornerShape(10.dp)).border(1.dp, Border, RoundedCornerShape(10.dp))
            .clickable { open = true }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Journal.ACCOUNTS[selected].first, color = TextC, fontSize = 13.sp, maxLines = 1)
            Text("  ▾", color = Muted, fontSize = 12.sp)
        }
        DropdownMenu(open, { open = false }, modifier = Modifier.background(Panel2)) {
            Journal.ACCOUNTS.forEachIndexed { i, (name, _) ->
                DropdownMenuItem(text = { Text(name, color = if (i == selected) Brand else TextC) }, onClick = { onSelect(i); open = false })
            }
        }
    }
}

private fun pickDate(ctx: android.content.Context, d: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert,
        { _, y, m, day -> onPicked(LocalDate.of(y, m + 1, day)) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
}
