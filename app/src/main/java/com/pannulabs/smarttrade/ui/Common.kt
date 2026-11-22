package com.pannulabs.smarttrade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Same palette as the Windows panel (panel/ui/theme.py)
val Bg = Color(0xFF0F1420)
val Panel = Color(0xFF171D2D)
val Panel2 = Color(0xFF1E2538)
val TextC = Color(0xFFE6E9F2)
val Muted = Color(0xFF8B93A7)
val Accent = Color(0xFF3D8BFD)
val Green = Color(0xFF27C281)
val Red = Color(0xFFF0545F)
val Amber = Color(0xFFF0B44F)

val AppColors = darkColorScheme(
    primary = Accent, onPrimary = Color.White, background = Bg, onBackground = TextC, surface = Panel,
    onSurface = TextC, surfaceVariant = Panel2, onSurfaceVariant = Muted, error = Red, secondary = Green,
    surfaceContainer = Panel, surfaceContainerHigh = Panel2, surfaceContainerHighest = Panel2,
)

fun money(x: Double, sign: Boolean = false): String =
    if (sign) String.format(Locale.US, "%+,.2f", x) else String.format(Locale.US, "%,.2f", x)

/** Like Python's :g for prices: no trailing zeros, no exponent. */
fun price(x: Double): String =
    if (x == 0.0) "0" else BigDecimal(x).round(java.math.MathContext(8)).stripTrailingZeros().toPlainString()

fun pnlColor(x: Double) = if (x > 0) Green else if (x < 0) Red else TextC
fun sideColor(side: String) = if (side == "LONG") Green else Red

private val tsFmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
private val tsFmtSec = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())
fun ts(ms: Long): String = if (ms <= 0) "" else tsFmt.format(Date(ms))
fun tsSec(ms: Long): String = if (ms <= 0) "" else tsFmtSec.format(Date(ms))

@Composable
fun H1(text: String, modifier: Modifier = Modifier) =
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextC, modifier = modifier)

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier) = Text(text, color = Muted, fontSize = 12.sp, modifier = modifier)

@Composable
fun StatCard(title: String, value: String, sub: String, modifier: Modifier = Modifier, valueColor: Color = TextC) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = Muted, fontSize = 12.sp)
            Text(value, color = valueColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Muted, fontSize = 11.sp, maxLines = 2)
        }
    }
}

@Composable
fun CardRow(vararg cards: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.forEach { it(Modifier.weight(1f)) }
    }
}

@Composable
fun ListCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
    ) { Column(Modifier.padding(12.dp)) { content() } }
}

/** "label  value" pair inside a list card. */
@Composable
fun KV(label: String, value: String, color: Color = TextC, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, color = color, fontSize = 14.sp)
    }
}

@Composable
fun SideText(side: String) = Text(side, color = sideColor(side), fontWeight = FontWeight.Bold, fontSize = 13.sp)

@Composable
fun Badge(text: String, color: Color) {
    Box(Modifier.background(color, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
fun Dot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextC, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun Confirm(title: String, text: String, yes: String, onYes: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onYes() }) { Text(yes, color = if (danger) Red else Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Panel2,
    )
}

@Composable
fun Empty(text: String) = MutedText(text, Modifier.padding(vertical = 16.dp))

@Composable
fun Theme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = AppColors, content = content)
