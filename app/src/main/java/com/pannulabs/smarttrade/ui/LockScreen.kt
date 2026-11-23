package com.pannulabs.smarttrade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Covers the whole app while it is locked. The engine keeps trading on the server. */
@Composable
fun LockScreen(message: String?, onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Bg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = Accent, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text("SmartTrade is locked", color = TextC, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        MutedText("Your engine keeps running on the server.")
        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Red, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlock) { Text("Unlock") }
    }
}
