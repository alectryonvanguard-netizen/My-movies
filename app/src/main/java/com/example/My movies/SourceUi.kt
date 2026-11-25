package com.mymovie.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymovie.app.ui.theme.*

val KindFull = Color(0xFF16A34A)
val KindTrailer = Color(0xFFD97706)
val KindClip = Color(0xFF2563EB)
val KindExternal = Color(0xFF6B7280)

@Composable
fun SourceBadge(label: String, color: Color) {
    Text(
        label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun LiveBadge() {
    val a by rememberInfiniteTransition(label = "live").animateFloat(0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFDC2626)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).alpha(a).clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(4.dp))
        Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SourceCard(
    badge: String, badgeColor: Color, title: String, caption: String,
    onPlay: (() -> Unit)?, onDownload: (() -> Unit)? = null, onDelete: (() -> Unit)? = null,
    playIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.PlayArrow
) {
    Surface(shape = RoundedCornerShape(16.dp), color = CardColor, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                SourceBadge(badge, badgeColor)
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                Text(caption, style = MaterialTheme.typography.labelSmall, color = TextDim, maxLines = 3)
            }
            onDownload?.let { IconButton(onClick = it) { Icon(Icons.Filled.Download, "Download") } }
            onDelete?.let { IconButton(onClick = it) { Icon(Icons.Filled.Delete, "Remove") } }
            onPlay?.let {
                FilledIconButton(onClick = it, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Violet)) { Icon(playIcon, "Open") }
            }
        }
    }
}

@Composable
fun AddLinkDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var label by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    val valid = url.startsWith("https://")
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Charcoal,
        title = { Text("Add a source link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Name (optional)") }, singleLine = true)
                OutlinedTextField(url, { url = it.trim() }, label = { Text("https:// link") }, singleLine = true, isError = url.isNotEmpty() && !valid)
                Text(
                    "Direct video links (.mp4, .m3u8, .mpd) play inside the app; other links open as an external viewing option.",
                    style = MaterialTheme.typography.bodySmall, color = TextDim
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(confirmed, { confirmed = it })
                    Text("I am authorized to watch this content.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(label.trim(), url) }, enabled = valid && confirmed) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
