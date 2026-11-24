package com.mymovie.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.imageLoader
import com.mymovie.app.appContainer
import com.mymovie.app.ui.theme.TextDim
import com.mymovie.app.ui.theme.VioletSoft
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val c = ctx.appContainer
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(c.prefs.tmdbKey) }
    var language by remember { mutableStateOf(c.prefs.language) }
    var region by remember { mutableStateOf(c.prefs.region) }
    var saved by remember { mutableStateOf(c.prefs.hasKey) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Text("TMDB API key", style = MaterialTheme.typography.titleMedium)
        Text(
            "Movie, series and anime data comes from TMDB. Create a free account at themoviedb.org, open Settings → API, " +
                "and paste the v3 \"API Key\" here. It is stored privately on this device only.",
            style = MaterialTheme.typography.bodySmall, color = TextDim
        )
        OutlinedTextField(
            key, { key = it }, singleLine = true, label = { Text("API key") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        Text(if (saved) "Status: key set" else "Status: no key — TMDB screens are disabled", color = if (saved) VioletSoft else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelLarge)

        Text("Content language & region", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(language, { language = it }, singleLine = true, label = { Text("Language (en-US)") }, modifier = Modifier.weight(1f))
            OutlinedTextField(region, { region = it.take(2) }, singleLine = true, label = { Text("Region (US)") }, modifier = Modifier.weight(1f))
        }
        Button(onClick = {
            c.prefs.tmdbKey = key
            c.prefs.language = language
            c.prefs.region = region
            saved = c.prefs.hasKey
            Toast.makeText(ctx, "Saved. Reopen screens to refresh.", Toast.LENGTH_SHORT).show()
        }) { Text("Save") }

        HorizontalDivider()
        Text("Data", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = {
            scope.launch { c.db.dao().clearHistory() }
            Toast.makeText(ctx, "Watch history cleared", Toast.LENGTH_SHORT).show()
        }) { Text("Clear watch history") }
        OutlinedButton(onClick = {
            ctx.imageLoader.diskCache?.clear()
            ctx.imageLoader.memoryCache?.clear()
            Toast.makeText(ctx, "Image cache cleared", Toast.LENGTH_SHORT).show()
        }) { Text("Clear image cache") }

        HorizontalDivider()
        Text("About", style = MaterialTheme.typography.titleMedium)
        Text(
            "My Movie is a private, ad-free player. It contains no advertising or analytics SDKs. Playable content comes only " +
                "from sources you are authorized to use: public-domain films (Internet Archive), links you add, and files on your device. " +
                "Provider listings open in the provider's own app or site.",
            style = MaterialTheme.typography.bodySmall, color = TextDim
        )
    }
}
