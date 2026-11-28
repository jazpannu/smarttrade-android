package com.pannulabs.smarttrade.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pannulabs.smarttrade.R
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Same palette as the Windows panel (panel/ui/theme.py); green is taken from the PannuLabs logo.
val Bg = Color(0xFF0B0F17)
val Panel = Color(0xFF121826)
val Panel2 = Color(0xFF1A2233)
val Border = Color(0xFF242E44)
val TextC = Color(0xFFE8ECF4)
val Muted = Color(0xFF8A94A8)
val Faint = Color(0xFF5B6478)
val Brand = Color(0xFF18D885)
val OnBrand = Color(0xFF05261A)
val Green = Brand
val Red = Color(0xFFF05463)
val Amber = Color(0xFFF5B547)
val Blue = Color(0xFF3D8BFD)
val Accent = Brand

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_bold, FontWeight.Bold),
)

private val base = Typography()
val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Inter), displayMedium = base.displayMedium.copy(fontFamily = Inter),
    displaySmall = base.displaySmall.copy(fontFamily = Inter), headlineLarge = base.headlineLarge.copy(fontFamily = Inter),
    headlineMedium = base.headlineMedium.copy(fontFamily = Inter), headlineSmall = base.headlineSmall.copy(fontFamily = Inter),
    titleLarge = base.titleLarge.copy(fontFamily = Inter), titleMedium = base.titleMedium.copy(fontFamily = Inter),
    titleSmall = base.titleSmall.copy(fontFamily = Inter), bodyLarge = base.bodyLarge.copy(fontFamily = Inter),
    bodyMedium = base.bodyMedium.copy(fontFamily = Inter), bodySmall = base.bodySmall.copy(fontFamily = Inter),
    labelLarge = base.labelLarge.copy(fontFamily = Inter), labelMedium = base.labelMedium.copy(fontFamily = Inter),
    labelSmall = base.labelSmall.copy(fontFamily = Inter),
)

val AppColors = darkColorScheme(
    primary = Brand, onPrimary = OnBrand, background = Bg, onBackground = TextC, surface = Panel,
    onSurface = TextC, surfaceVariant = Panel2, onSurfaceVariant = Muted, error = Red, secondary = Brand,
    surfaceContainer = Panel, surfaceContainerHigh = Panel2, surfaceContainerHighest = Panel2, outline = Border,
    outlineVariant = Border, primaryContainer = Panel2, onPrimaryContainer = TextC, secondaryContainer = Panel2,
    onSecondaryContainer = TextC, surfaceContainerLow = Panel, surfaceContainerLowest = Bg,
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

// ---- brand ------------------------------------------------------------------------------------------
/** "Pannu" in brand green, "Labs" in white. */
@Composable
fun BrandWordmark(size: Int = 18, tight: Boolean = false) = Text(
    buildAnnotatedString {
        withStyle(SpanStyle(color = Brand)) { append("Pannu") }
        withStyle(SpanStyle(color = Color.White)) { append("Labs") }
    },
    fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = size.sp,
    lineHeight = if (tight) (size + 2).sp else androidx.compose.ui.unit.TextUnit.Unspecified,
)

@Composable
fun BrandLogo(size: Int = 30) =
    Image(painterResource(R.drawable.logo), contentDescription = "PannuLabs", modifier = Modifier.size(size.dp))

// ---- text -------------------------------------------------------------------------------------------
@Composable
fun H1(text: String, modifier: Modifier = Modifier) =
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextC, modifier = modifier)

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier) = Text(text, color = Muted, fontSize = 12.sp, modifier = modifier)

// ---- cards ------------------------------------------------------------------------------------------
/** Number tile with a thin coloured line on top; [trend] > 0 / < 0 adds an up / down arrow. */
@Composable
fun StatCard(title: String, value: String, sub: String, modifier: Modifier = Modifier, valueColor: Color = TextC,
             accent: Color = Brand, trend: Double? = null) {
    val line = if (trend != null && trend != 0.0) pnlColor(trend) else accent
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Border),
        colors = CardDefaults.cardColors(containerColor = Panel)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(line))
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 13.dp)) {
            Text(title.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trend != null && trend != 0.0) {
                    Text(if (trend > 0) "▲" else "▼", color = valueColor, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                }
                Text(value, color = valueColor, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
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
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Border),
        colors = CardDefaults.cardColors(containerColor = Panel),
    ) { Column(Modifier.padding(14.dp)) { content() } }
}

/** "label  value" pair inside a list card. */
@Composable
fun KV(label: String, value: String, color: Color = TextC, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ---- pills, badges, dot -----------------------------------------------------------------------------
/** Small rounded badge: tinted background, coloured text (LONG/SHORT, P/L). */
@Composable
fun Pill(text: String, color: Color, fontSize: Int = 12) {
    Box(Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = fontSize.sp, maxLines = 1)
    }
}

@Composable
fun SideText(side: String) = Pill(side, sideColor(side), 11)

/** PAPER / DEMO / LIVE: outlined pill with a soft tint. */
@Composable
fun Badge(text: String, color: Color) {
    Box(Modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(50)).border(1.dp, color, RoundedCornerShape(50))
        .padding(horizontal = 11.dp, vertical = 3.dp)) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
    }
}

/** Connection dot with a soft pulsing glow. */
@Composable
fun Dot(color: Color, text: String) {
    val t = rememberInfiniteTransition(label = "glow")
    val a by t.animateFloat(0.15f, 0.45f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "a")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(16.dp).alpha(a).background(color, CircleShape))
            Box(Modifier.size(8.dp).background(color, CircleShape))
        }
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextC, fontSize = 12.sp, maxLines = 1)
    }
}

// ---- dialogs, empty states --------------------------------------------------------------------------
@Composable
fun Confirm(title: String, text: String, yes: String, onYes: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onYes() }) { Text(yes, color = if (danger) Red else Brand, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } },
        containerColor = Panel2, shape = RoundedCornerShape(20.dp),
    )
}

/** Friendly empty state: an icon and a short line. */
@Composable
fun Empty(text: String, icon: ImageVector = Icons.Outlined.Inbox) {
    Column(Modifier.fillMaxWidth().padding(vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Faint, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, color = Muted, fontSize = 13.sp)
    }
}

@Composable
fun Theme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = AppColors, typography = AppTypography, content = content)
