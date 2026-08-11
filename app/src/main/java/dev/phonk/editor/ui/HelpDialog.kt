package dev.phonk.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HelpDialog(
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Help & Tips") },
        text = {
            Column {
                Text("Getting Started", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("• Tap 'Open video' to import a video from your device", fontSize = 12.sp)
                Text("• Use the editor to add effects, text, and overlays", fontSize = 12.sp)
                Text("• Export your video when you're done editing", fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Text("Editor Tools", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("• Media: Import videos, audio, and photos", fontSize = 12.sp)
                Text("• Audio: Adjust volume, fade, and pitch", fontSize = 12.sp)
                Text("• Text: Add text overlays with animations", fontSize = 12.sp)
                Text("• Overlay: Add images, stickers, and shapes", fontSize = 12.sp)
                Text("• Effects: Apply visual effects to clips", fontSize = 12.sp)
                Text("• Filters: Color grade your video", fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Text("Beat Sync", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("• Analyze your audio to detect BPM and beats", fontSize = 12.sp)
                Text("• Use beat sync for automatic cut patterns", fontSize = 12.sp)
                Text("• Effects can pulse with the beat", fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", color = scheme.primary)
            }
        },
    )
}
