package com.mymovie.app.ui.screens

import android.content.Intent
import android.net.Uri
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
import com.mymovie.app.data.TestResult
import com.mymovie.app.notify.Notifier
import com.mymovie.app.data.TmdbAuth
import com.mymovie.app.data.remote.Network
import com.mymovie.app.ui.components.DropdownChip
import com.mymovie.app.ui.theme.TextDim
import com.mymovie.app.ui.theme.VioletSoft
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val c = ctx.appContainer
    val p = c.prefs
    val scope = rememberCoroutineScope()
    fun open(url: String) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    var cred by remember { mutableStateOf("") }
    var configured by remember { mutableStateOf(p.hasKey) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var tsdbKey by remember { mutableStateOf("") }
    var fdKey by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(p.language) }
    var region by remember { mutableStateOf(p.region) }
    var preset by remember { mutableStateOf(p.qualityPreset) }
    var audio by remember { mutableStateOf(p.audioLang) }
    var subs by remember { mutableStateOf(p.subtitleLang) }
    var aMovies by remember { mutableStateOf(p.alertMovies) }
    var aSports by remember { mutableStateOf(p.alertSports) }
    var aNews by remember { mutableStateOf(p.alertNews) }
    var followed by remember { mutableStateOf(p.followedLeagues) }
    var subs by remember { mutableStateOf(p.subreddits) }
    var tg by remember { mutableStateOf(p.telegramChannels) }
    var newSub by remember { mutableStateOf("") }
    var newTg by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Text("TMDB account & API key", style = MaterialTheme.typography.titleMedium)
        Text("Movie data comes from TMDB. Create a free account, then copy either the v3 API Key or the v4 Read Access Token from your API settings.", style = MaterialTheme.typography.bodySmall, color = TextDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { open("https://www.themoviedb.org/signup") }) { Text("Get your free key") }
            OutlinedButton(onClick = { open("https://www.themoviedb.org/settings/api") }) { Text("API page") }
        }
        Text(
            status ?: if (configured) "Status: credentials saved (not tested in this session)" else "Status: not configured — TMDB screens are disabled",
            color = if (configured && status?.startsWith("Invalid") != true) VioletSoft else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge
        )
        OutlinedTextField(
            cred, { cred = it.trim() }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(if (configured) "Replace API key or Read Access Token" else "API key or Read Access Token") },
            visualTransformation = PasswordVisualTransformation()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = cred.isNotBlank() && !testing, onClick = {
                testing = true
                scope.launch {
                    status = when (val r = TmdbAuth.test(cred)) {
                        TestResult.Ok -> "Connection OK — these credentials work"
                        TestResult.Invalid -> "Invalid credentials — TMDB rejected them"
                        is TestResult.Failed -> "Could not test: ${r.message}"
                    }
                    testing = false
                }
            }) { Text(if (testing) "Testing…" else "Test connection") }
            Button(enabled = cred.isNotBlank(), onClick = {
                if (p.saveTmdb(cred)) { configured = true; cred = ""; Network.forceRefresh(); status = "Saved securely on this device"; toast("Saved") } else toast("Could not save securely")
            }) { Text(if (configured) "Replace" else "Save") }
        }
        if (configured) TextButton(onClick = { confirmRemove = true }) { Text("Remove credentials", color = MaterialTheme.colorScheme.error) }

        HorizontalDivider()
        Text("Sports data", style = MaterialTheme.typography.titleMedium)
        Text("Works out of the box with TheSportsDB's free key (schedules, results, tables). Optional: your own TheSportsDB premium key (live scores, timelines) and a free football-data.org key (football tables, form, top scorers, today's matches).", style = MaterialTheme.typography.bodySmall, color = TextDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { open("https://www.thesportsdb.com/pricing") }) { Text("TheSportsDB") }
            OutlinedButton(onClick = { open("https://www.football-data.org/client/register") }) { Text("football-data.org") }
        }
        OutlinedTextField(tsdbKey, { tsdbKey = it.trim() }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
            label = { Text(if (p.sportsDbKey != "123") "TheSportsDB key saved — replace" else "TheSportsDB premium key (optional)") })
        OutlinedTextField(fdKey, { fdKey = it.trim() }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
            label = { Text(if (p.footballDataKey.isNotBlank()) "football-data.org key saved — replace" else "football-data.org key (optional)") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (tsdbKey.isNotBlank()) p.sportsDbKey = tsdbKey
                if (fdKey.isNotBlank()) p.footballDataKey = fdKey
                tsdbKey = ""; fdKey = ""; Network.forceRefresh(); toast("Saved. Reopen Live Sports to apply.")
            }) { Text("Save sports keys") }
            OutlinedButton(onClick = { p.sportsDbKey = ""; p.footballDataKey = ""; toast("Sports keys removed") }) { Text("Remove") }
        }

        HorizontalDivider()
        Text("Playback preferences", style = MaterialTheme.typography.titleMedium)
        val presets = listOf("AUTO" to "Auto", "DATA_SAVER" to "Data Saver", "BALANCED" to "Balanced", "HD" to "HD", "FULL" to "Full Quality")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DropdownChip("Default quality", presets.map { it.second }, presets.indexOfFirst { it.first == preset }.takeIf { it > 0 }) {
                val k = presets[it ?: 0].first; preset = k; p.qualityPreset = k
            }
            DropdownChip("Audio language", LANGUAGES.map { it.label }, LANGUAGES.indexOfFirst { it.code == audio }.takeIf { it >= 0 }) {
                audio = it?.let { i -> LANGUAGES[i].code } ?: ""; p.audioLang = audio
            }
            DropdownChip("Subtitles", LANGUAGES.map { it.label }, LANGUAGES.indexOfFirst { it.code == subs }.takeIf { it >= 0 }) {
                subs = it?.let { i -> LANGUAGES[i].code } ?: ""; p.subtitleLang = subs
            }
        }
        Text("Quality presets only apply to sources that offer several qualities; the player lists what each source really provides.", style = MaterialTheme.typography.bodySmall, color = TextDim)

        HorizontalDivider()
        Text("Notifications", style = MaterialTheme.typography.titleMedium)
        @Composable fun SwitchRow(label: String, sub: String, on: Boolean, change: (Boolean) -> Unit) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelLarge); Text(sub, style = MaterialTheme.typography.bodySmall, color = TextDim) }
                Switch(on, change)
            }
        }
        SwitchRow("Movie alerts", "Important: watchlist movies on release day. Normal: popular releases today.", aMovies) { aMovies = it; p.alertMovies = it }
        SwitchRow("Sports alerts", "Important: kick-off soon and final scores for competitions you follow (star icon in Live Sports).", aSports) { aSports = it; p.alertSports = it }
        SwitchRow("Daily headline digest", "One notification a day with a movie and a sports headline.", aNews) { aNews = it; p.alertNews = it }
        if (followed.isNotEmpty()) {
            Text("Followed competitions", style = MaterialTheme.typography.labelLarge)
            followed.forEach { f ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(f.split("|").getOrElse(3) { f }, Modifier.weight(1f))
                    TextButton(onClick = { followed = followed - f; p.followedLeagues = followed }) { Text("Unfollow") }
                }
            }
        }
        OutlinedButton(onClick = {
            if (Notifier.enabled(ctx)) { Notifier.send(ctx, "test", Notifier.HIGH, "My Movie alerts are on", "You will get release-day, match and score alerts here."); toast("Test sent") }
            else toast("Notifications are blocked. Allow them in Android settings.")
        }) { Text("Send a test notification") }
        Text("Alerts are checked about every 30 minutes by polling free data sources. Android may delay them to save battery, and they are not real-time goal alerts.", style = MaterialTheme.typography.bodySmall, color = TextDim)

        HorizontalDivider()
        Text("Reddit & Telegram", style = MaterialTheme.typography.titleMedium)
        Text("Subreddit posts appear in News (headline and link only, opened inside the app). Public Telegram channels open inside the app as web pages. Private channels and Telegram login are not supported, and checks are not instant.", style = MaterialTheme.typography.bodySmall, color = TextDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(newSub, { newSub = it.trim().removePrefix("r/").removePrefix("/r/") }, singleLine = true, label = { Text("Subreddit, e.g. movies") }, modifier = Modifier.weight(1f))
            Button(enabled = Regex("[A-Za-z0-9_]{2,21}").matches(newSub) && newSub !in subs, onClick = { subs = subs + newSub; p.subreddits = subs; newSub = "" }) { Text("Add") }
        }
        subs.forEach { s ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("r/$s", Modifier.weight(1f)); TextButton(onClick = { subs = subs - s; p.subreddits = subs }) { Text("Remove") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(newTg, { newTg = it.trim().removePrefix("https://t.me/").removePrefix("@") }, singleLine = true, label = { Text("Public Telegram channel") }, modifier = Modifier.weight(1f))
            Button(enabled = Regex("[A-Za-z0-9_]{5,32}").matches(newTg) && newTg !in tg, onClick = { tg = tg + newTg; p.telegramChannels = tg; newTg = "" }) { Text("Add") }
        }
        tg.forEach { s ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("@$s", Modifier.weight(1f)); TextButton(onClick = { tg = tg - s; p.telegramChannels = tg }) { Text("Remove") }
            }
        }

        HorizontalDivider()
        Text("Content language & region", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(language, { language = it }, singleLine = true, label = { Text("Language (en-US)") }, modifier = Modifier.weight(1f))
            OutlinedTextField(region, { region = it.take(2) }, singleLine = true, label = { Text("Region (US)") }, modifier = Modifier.weight(1f))
        }
        Button(onClick = { p.language = language; p.region = region; Network.forceRefresh(); toast("Saved") }) { Text("Save") }

        HorizontalDivider()
        Text("Data", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { Network.forceRefresh(); toast("Next loads fetch fresh data") }) { Text("Refresh catalogue now") }
        OutlinedButton(onClick = { scope.launch { c.db.dao().clearHistory() }; toast("Watch history cleared") }) { Text("Clear watch history") }
        OutlinedButton(onClick = { scope.launch { c.db.dao().clearSearches() }; toast("Search history cleared") }) { Text("Clear search history") }
        OutlinedButton(onClick = { ctx.imageLoader.diskCache?.clear(); ctx.imageLoader.memoryCache?.clear(); toast("Image cache cleared") }) { Text("Clear image cache") }

        HorizontalDivider()
        Text("About", style = MaterialTheme.typography.titleMedium)
        Text(
            "My Movie has no advertising or analytics SDKs and needs no account. Personal data stays on this device. Full-length playback and downloads are offered only for sources that permit them; sports scores and schedules do not imply broadcast rights.",
            style = MaterialTheme.typography.bodySmall, color = TextDim
        )
    }

    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text("Remove TMDB credentials?") },
        text = { Text("Movie data will stop loading until you add a key again. Cached pages stay available offline.") },
        confirmButton = { TextButton(onClick = { p.tmdbCredential = ""; configured = false; status = null; confirmRemove = false }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
    )
}
