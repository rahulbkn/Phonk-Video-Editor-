package dev.phonk.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.TextFieldValue
import dev.phonk.editor.R
import dev.phonk.editor.ui.components.PhonkButton
import dev.phonk.editor.ui.components.PhonkSlider

/** Dialog to author a text overlay: content, size, opacity and animation. */
@Composable
fun TextEditDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (text: String, fontSize: Float, opacity: Float, animation: String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(TextFieldValue(initial)) }
    var size by remember { mutableStateOf(24f) }
    var opacity by remember { mutableStateOf(1f) }
    var animation by remember { mutableStateOf("Fade") }
    val animations = listOf("Fade", "Slide", "Pop", "Glitch", "Zoom")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_add)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.text_add)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.text_size), fontSize = 11.sp, color = scheme.onSurface, modifier = Modifier.width(60.dp))
                    PhonkSlider(value = size, onValueChange = { size = it }, valueRange = 8f..96f, modifier = Modifier.weight(1f))
                    Text("%d".format(size.toInt()), fontSize = 11.sp, color = scheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.text_opacity), fontSize = 11.sp, color = scheme.onSurface, modifier = Modifier.width(60.dp))
                    PhonkSlider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.1f..1f, modifier = Modifier.weight(1f))
                    Text("%d".format((opacity * 100).toInt()), fontSize = 11.sp, color = scheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.text_animation), fontSize = 11.sp, color = scheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    animations.forEach { a ->
                        val selected = animation == a
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) scheme.primary.copy(alpha = 0.25f) else scheme.surfaceVariant.copy(alpha = 0.7f))
                                .clickableNoRipple { animation = a }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(a, fontSize = 11.sp, color = if (selected) scheme.primary else scheme.onSurface, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        },
        confirmButton = {
            PhonkButton(
                label = stringResource(R.string.ok),
                onClick = { onSave(text.text.ifBlank { "Text" }, size, opacity, animation) },
                primary = true,
            )
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = androidx.compose.foundation.interaction.MutableInteractionSource()
    return this
        .clip(RoundedCornerShape(8.dp))
        .clickable(interactionSource = interaction, indication = null) { onClick() }
}
